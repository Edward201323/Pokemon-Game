package Pokemon;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Type-tiered move pools. Each row in moves.csv is one move:
//   type,name,power,accuracy,physical,min_level
// At pick-time we filter by level, bias toward the highest-tier moves the pokemon
// qualifies for, and guarantee a physical+special split so loadouts feel balanced.
public class Moves {
    private static final String CSV_PATH = "./src/res/movesData/moves.csv";
    private static final Random RNG = new Random();
    private static final Map<String, List<Move>> BY_TYPE = load();

    private static Map<String, List<Move>> load() {
        Map<String, List<Move>> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_PATH))) {
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] c = line.split(",");
                if (c.length < 6) continue;
                String type     = c[0].trim().toLowerCase();
                String name     = c[1].trim();
                int power       = Integer.parseInt(c[2].trim());
                int accuracy    = parseAccuracy(c[3]);
                boolean physical = Boolean.parseBoolean(c[4].trim());
                int minLevel    = Integer.parseInt(c[5].trim());
                map.computeIfAbsent(type, k -> new ArrayList<>())
                   .add(new Move(name, type, power, accuracy, physical, minLevel));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    private static int parseAccuracy(String s) {
        s = s.trim();
        if (s.equalsIgnoreCase("Always Hits")) return -1;
        return Integer.parseInt(s);
    }

    // Back-compat wrapper for callers that don't have a level handy. Treat them as mid-tier.
    public static List<Move> getMoves(String type1, String type2) {
        return getMoves(type1, type2, 50);
    }

    // Returns 4 moves tailored to the pokemon's level + types. Mono-type gets 2 physical +
    // 2 special from its single pool. Dual-type gets 1 physical + 1 special from each type.
    public static List<Move> getMoves(String type1, String type2, int level) {
        List<Move> result = new ArrayList<>();
        boolean dualType = type2 != null
                && !type2.equalsIgnoreCase("none")
                && !type2.equalsIgnoreCase(type1);
        if (dualType) {
            pickFromType(result, type1, level, 2);
            pickFromType(result, type2, level, 2);
        } else {
            pickFromType(result, type1, level, 4);
        }
        return result;
    }

    // Pick `count` moves from one type, split evenly between physical and special. Each
    // half is independently filtered by level, then biased toward the highest-tier moves
    // the pokemon qualifies for.
    private static void pickFromType(List<Move> out, String type, int level, int count) {
        if (type == null) return;
        List<Move> pool = BY_TYPE.get(type.toLowerCase());
        if (pool == null || pool.isEmpty()) return;
        int halfCount = Math.max(1, count / 2);
        List<Move> phys = new ArrayList<>(), spec = new ArrayList<>();
        for (Move m : pool) (m.physical ? phys : spec).add(m);
        pickWithTierBias(out, phys, level, halfCount);
        pickWithTierBias(out, spec, level, halfCount);
    }

    // Filter `pool` to moves that qualify at `level`. If too few qualify, top up from the
    // next-lowest tiers so low-level pokemon still fill their slots. Then take the top
    // half by minLevel (the "tier bias" — favors newer moves for high-level pokemon) and
    // randomly sample `count` from that subset.
    private static void pickWithTierBias(List<Move> out, List<Move> pool, int level, int count) {
        if (pool.isEmpty() || count <= 0) return;
        List<Move> qualified = new ArrayList<>();
        for (Move m : pool) if (m.minLevel <= level) qualified.add(m);
        if (qualified.size() < count) {
            // Top up with next-lowest tiers (sorted asc by minLevel) until we have count.
            List<Move> unqualified = new ArrayList<>();
            for (Move m : pool) if (m.minLevel > level) unqualified.add(m);
            unqualified.sort((a, b) -> a.minLevel - b.minLevel);
            for (Move m : unqualified) {
                if (qualified.size() >= count) break;
                qualified.add(m);
            }
        }
        if (qualified.size() <= count) {
            out.addAll(qualified);
            return;
        }
        // Tier bias: sort desc by minLevel, slice top half (rounded up, at least `count`).
        qualified.sort((a, b) -> b.minLevel - a.minLevel);
        int topHalfSize = Math.max(count, (qualified.size() + 1) / 2);
        List<Move> top = new ArrayList<>(qualified.subList(0, Math.min(qualified.size(), topHalfSize)));
        Collections.shuffle(top, RNG);
        for (int i = 0; i < count && i < top.size(); i++) out.add(top.get(i));
    }

    // For the Move Tutor: every move the pokemon could be taught right now. All moves of
    // its type(s) where minLevel <= pokemon.level, excluding moves it already knows.
    // Sorted by type then minLevel ascending so the list scrolls predictably.
    public static List<Move> getLearnableMoves(Pokemon p) {
        List<Move> out = new ArrayList<>();
        if (p == null) return out;
        java.util.Set<String> alreadyKnown = new java.util.HashSet<>();
        if (p.moves != null) for (Move m : p.moves) if (m != null) alreadyKnown.add(m.name);
        addLearnableForType(out, p.type1, p.level, alreadyKnown);
        if (p.type2 != null && !p.type2.equalsIgnoreCase("none")
                && !p.type2.equalsIgnoreCase(p.type1)) {
            addLearnableForType(out, p.type2, p.level, alreadyKnown);
        }
        out.sort((a, b) -> {
            int t = a.type.compareTo(b.type);
            return t != 0 ? t : a.minLevel - b.minLevel;
        });
        return out;
    }

    private static void addLearnableForType(List<Move> out, String type, int level,
                                             java.util.Set<String> alreadyKnown) {
        if (type == null) return;
        List<Move> pool = BY_TYPE.get(type.toLowerCase());
        if (pool == null) return;
        for (Move m : pool) {
            if (m.minLevel > level) continue;
            if (alreadyKnown.contains(m.name)) continue;
            out.add(m);
        }
    }
}
