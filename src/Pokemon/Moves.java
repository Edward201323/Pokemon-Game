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
    private static final String LEARNSET_CSV_PATH = "./src/res/PokemonData/learnsets.csv";
    private static final Random RNG = new Random();
    private static final Map<String, List<Move>> BY_TYPE = load();

    // Per-species whitelist of move names the species can learn (mainline level-up + TM
    // through Gen 7, filtered to moves that exist in our 150-move catalog). If a species
    // isn't listed (or has an empty set), getMoves falls back to the type-based picker so
    // every pokemon still ends up with a sensible loadout.
    private static final Map<String, java.util.Set<String>> LEARNSET = loadLearnset();

    private static Map<String, java.util.Set<String>> loadLearnset() {
        Map<String, java.util.Set<String>> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(LEARNSET_CSV_PATH))) {
            reader.readLine(); // header: pokemon,move
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                int comma = line.indexOf(',');
                if (comma < 0) continue;
                String poke = line.substring(0, comma).trim();
                String move = line.substring(comma + 1).trim();
                if (poke.isEmpty() || move.isEmpty()) continue;
                map.computeIfAbsent(poke, k -> new java.util.HashSet<>()).add(move);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

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

    // Back-compat wrappers for callers that don't have species or level handy.
    public static List<Move> getMoves(String type1, String type2) {
        return getMoves(null, type1, type2, 50);
    }
    public static List<Move> getMoves(String type1, String type2, int level) {
        return getMoves(null, type1, type2, level);
    }

    // Hyper Beam is excluded from the universal Normal slot and from the Move Tutor —
    // it's still in the regular pool so Normal-type pokemon can roll it via tier bias.
    private static final String HYPER_BEAM = "Hyper Beam";

    // Signature moves are min-level 1 (so the tutor can teach them right away) but
    // would otherwise be filtered out by the tier-bias picker on high-level spawns
    // (top-half-by-minLevel cut). Always force-include any signature move a species
    // learns so e.g. a wild Kyogre actually shows up with Origin Pulse.
    private static final java.util.Set<String> SIGNATURE_MOVES = new java.util.HashSet<>(
        java.util.Arrays.asList(
            "Sacred Fire", "Sacred Sword", "Aeroblast", "Psystrike", "Origin Pulse",
            "Precipice Blades", "Dragon Ascent", "Roar of Time", "Spacial Rend",
            "Shadow Force", "Judgment", "Magma Storm", "Crush Grip", "V-create",
            "Blue Flare", "Fusion Flare", "Bolt Strike", "Fusion Bolt",
            "Glaciate", "Freeze Shock", "Ice Burn", "Secret Sword", "Relic Song",
            "Mist Ball", "Luster Purge", "Doom Desire", "Psycho Boost",
            "Oblivion Wing", "Diamond Storm", "Hyperspace Hole", "Hyperspace Fury",
            "Steam Eruption"
        )
    );

    // Returns up to 4 moves tailored to the pokemon's level + types. Slot 0 is the
    // strongest qualifying Normal move (except Hyper Beam) — guarantees every pokemon
    // has a fallback against immune defenders (e.g., Electric vs Ground). The remaining
    // slots are filled by the type-tiered picker, optionally restricted to the species'
    // real-game learnset (when `species` is listed in learnsets.csv). If the learnset
    // filter would empty the type-pool, falls back to the unfiltered type-pool.
    public static List<Move> getMoves(String species, String type1, String type2, int level) {
        List<Move> result = new ArrayList<>();
        java.util.Set<String> learnset = (species != null) ? LEARNSET.get(species) : null;
        // Force-include any signature moves the species learns — they bypass the tier-bias
        // picker so a wild Kyogre actually shows up with Origin Pulse, etc.
        if (learnset != null) {
            for (String name : learnset) {
                if (!SIGNATURE_MOVES.contains(name)) continue;
                Move m = findByName(name);
                if (m != null && m.minLevel <= level) result.add(m);
            }
        }
        // Slot 0: strongest Normal (except Hyper Beam) the species can learn. When the
        // species has a learnset, respect it strictly — if no Normal move is in the
        // learnset, slot 0 stays empty (the user explicitly stripped Normal moves from
        // non-Normal species). Only species without any learnset entry get the universal
        // fallback so they still receive a Normal coverage move.
        Move normal;
        if (learnset != null) {
            normal = strongestNormalExceptHyperBeam(level, learnset);
        } else {
            normal = strongestNormalExceptHyperBeam(level, null);
        }
        if (normal != null) result.add(normal);

        boolean dualType = type2 != null
                && !type2.equalsIgnoreCase("none")
                && !type2.equalsIgnoreCase(type1);
        if (dualType) {
            pickFromType(result, type1, level, 2, learnset);
            pickFromType(result, type2, level, 1, learnset);
        } else {
            pickFromType(result, type1, level, 3, learnset);
        }
        // Safety net: an underleveled spawn of a species whose entire learnset is
        // above its current level (e.g., a Lv-5 Klink whose earliest legal move is
        // Lv 15) would otherwise end up with zero moves. Top up with the lowest-
        // minLevel learnset moves regardless of level so the encounter is never
        // a complete no-op.
        if (result.isEmpty() && learnset != null && !learnset.isEmpty()) {
            List<Move> safetyPool = new ArrayList<>();
            for (String name : learnset) {
                Move m = findByName(name);
                if (m != null) safetyPool.add(m);
            }
            safetyPool.sort((a, b) -> a.minLevel - b.minLevel);
            for (int i = 0; i < Math.min(2, safetyPool.size()); i++) {
                result.add(safetyPool.get(i));
            }
        }

        // De-dupe: a Normal-type pokemon may roll the same move via the type-picker;
        // keep the earlier pick (signatures > Normal slot > type picks) and drop later duplicates.
        java.util.LinkedHashMap<String, Move> uniq = new java.util.LinkedHashMap<>();
        for (Move m : result) if (m != null) uniq.putIfAbsent(m.name, m);
        // Cap at 4 slots — if signatures already filled the moveset, type picks fall off
        // the end. Iteration order preserves signature-first priority.
        List<Move> capped = new ArrayList<>(uniq.values());
        if (capped.size() > 4) capped = capped.subList(0, 4);
        return new ArrayList<>(capped);
    }

    // Highest-power Normal move the pokemon qualifies for, excluding Hyper Beam. Ties
    // broken by minLevel (newer move wins). If `learnset` is non-null, restricts to
    // moves the species actually learns. Returns null if no Normal move qualifies.
    private static Move strongestNormalExceptHyperBeam(int level, java.util.Set<String> learnset) {
        List<Move> pool = BY_TYPE.get("normal");
        if (pool == null) return null;
        Move best = null;
        for (Move m : pool) {
            if (m.name.equalsIgnoreCase(HYPER_BEAM)) continue;
            if (m.minLevel > level) continue;
            if (learnset != null && !learnset.contains(m.name)) continue;
            if (best == null
                    || m.basePower > best.basePower
                    || (m.basePower == best.basePower && m.minLevel > best.minLevel)) {
                best = m;
            }
        }
        return best;
    }

    // Pick `count` moves from one type, split between physical and special (phys takes
    // the extra slot on odd counts). Each half is independently filtered by level then
    // biased toward the highest-tier moves the pokemon qualifies for. If `learnset` is
    // non-null, the type-pool is first filtered to moves the species can actually learn;
    // an empty filtered pool falls through to the unrestricted type-pool so species with
    // sparse learnsets still produce moves.
    private static void pickFromType(List<Move> out, String type, int level, int count,
                                     java.util.Set<String> learnset) {
        if (type == null || count <= 0) return;
        List<Move> pool = BY_TYPE.get(type.toLowerCase());
        if (pool == null || pool.isEmpty()) return;
        List<Move> filtered = pool;
        if (learnset != null) {
            List<Move> kept = new ArrayList<>(pool.size());
            for (Move m : pool) if (learnset.contains(m.name)) kept.add(m);
            if (!kept.isEmpty()) filtered = kept;
            // If kept is empty, fall back to the unfiltered pool — better to give a
            // mistyped move than no move at all.
        }
        int physCount = (count + 1) / 2;
        int specCount = count - physCount;
        List<Move> phys = new ArrayList<>(), spec = new ArrayList<>();
        for (Move m : filtered) (m.physical ? phys : spec).add(m);
        pickWithTierBias(out, phys, level, physCount);
        pickWithTierBias(out, spec, level, specCount);
    }

    // Filter `pool` to moves that qualify at `level`. If too few qualify, slots stay
    // empty — low-level pokemon (e.g., a Lv-5 starter) shouldn't be handed a Lv-85
    // move just to fill space. Among qualifying moves, take the top half by minLevel
    // (the "tier bias" — favors newer moves for high-level pokemon) and randomly sample
    // `count` from that subset.
    private static void pickWithTierBias(List<Move> out, List<Move> pool, int level, int count) {
        if (pool.isEmpty() || count <= 0) return;
        List<Move> qualified = new ArrayList<>();
        for (Move m : pool) if (m.minLevel <= level) qualified.add(m);
        if (qualified.isEmpty()) return;
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

    // Look up a move by name across every type pool. Used by SaveManager to rebuild
    // a pokemon's move list on load (we persist names only). Returns null if not found.
    public static Move findByName(String name) {
        if (name == null) return null;
        for (List<Move> pool : BY_TYPE.values()) {
            for (Move m : pool) {
                if (m.name.equals(name)) return m;
            }
        }
        return null;
    }

    // For the Move Tutor: every move the pokemon could be taught right now. All moves of
    // its type(s) where minLevel <= pokemon.level, excluding moves it already knows.
    // Sorted by type then minLevel ascending so the list scrolls predictably.
    public static List<Move> getLearnableMoves(Pokemon p) {
        List<Move> out = new ArrayList<>();
        if (p == null) return out;
        java.util.Set<String> alreadyKnown = new java.util.HashSet<>();
        if (p.moves != null) for (Move m : p.moves) if (m != null) alreadyKnown.add(m.name);
        // If the species has a learnset, restrict tutor offerings to it (intersected with
        // type / level / already-known filters). Species not in learnsets.csv keep the
        // old "all moves of your type(s) + all Normal" behavior so nothing breaks.
        java.util.Set<String> learnset = LEARNSET.get(p.name);
        addLearnableForType(out, p.type1, p.level, alreadyKnown, learnset);
        if (p.type2 != null && !p.type2.equalsIgnoreCase("none")
                && !p.type2.equalsIgnoreCase(p.type1)) {
            addLearnableForType(out, p.type2, p.level, alreadyKnown, learnset);
        }
        // Normal coverage in the tutor: species with a learnset get only the Normal
        // moves they actually learn (matches the strip we applied to non-Normal species).
        // Species without any learnset entry fall back to universal Normal coverage so
        // they're not left empty. Hyper Beam is excluded from tutoring across the board.
        addLearnableForType(out, "normal", p.level, alreadyKnown, learnset);
        // Cross-type learnset moves: TMs like Ice Beam on Suicune (Water type) aren't
        // in any of the type pools above. Walk the species' learnset and add anything
        // not already covered, respecting level + already-known filters.
        if (learnset != null) {
            java.util.Set<String> alreadyInOut = new java.util.HashSet<>();
            for (Move m : out) alreadyInOut.add(m.name);
            for (String moveName : learnset) {
                if (alreadyKnown.contains(moveName) || alreadyInOut.contains(moveName)) continue;
                Move m = findByName(moveName);
                if (m == null) continue;
                if (m.minLevel > p.level) continue;
                out.add(m);
                alreadyInOut.add(moveName);
            }
        }
        out.removeIf(m -> m.name.equalsIgnoreCase(HYPER_BEAM));
        out.sort((a, b) -> {
            int t = a.type.compareTo(b.type);
            return t != 0 ? t : a.minLevel - b.minLevel;
        });
        return out;
    }

    private static void addLearnableForType(List<Move> out, String type, int level,
                                             java.util.Set<String> alreadyKnown,
                                             java.util.Set<String> learnset) {
        if (type == null) return;
        List<Move> pool = BY_TYPE.get(type.toLowerCase());
        if (pool == null) return;
        for (Move m : pool) {
            if (m.minLevel > level) continue;
            if (alreadyKnown.contains(m.name)) continue;
            if (learnset != null && !learnset.contains(m.name)) continue;
            out.add(m);
        }
    }
}
