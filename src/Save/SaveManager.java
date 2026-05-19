package Save;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Main.GamePanel;
import Pokemon.GetPokemon;
import Pokemon.Move;
import Pokemon.Moves;
import Pokemon.Pokemon;
import object.SuperObject;

// Slot-based save/load. Saves live as plain-text key=value files at ./saves/slot_N.sav so
// they're easy to diff and inspect in the IDE. We persist enough state to reconstruct
// the world: player position+facing, party + PC pokemon (name/level/HP/EXP/IVs/EVs/moves),
// which map objects got picked up, and whether the boss was defeated.
//
// On load: we let AssetSetter spawn everything normally, then apply the save delta by
// removing the picked-up objects and (if defeated) the boss. Pokemon get rebuilt via
// GetPokemon.findPokemon, then their IVs/EVs are overridden from disk and recalcStats()
// re-derives the matching stats. currentHP is clamped to maxHP after the recalc.
public class SaveManager {
    public static final int SLOT_COUNT = 3;
    private static final String SAVES_DIR = "./saves";
    private static final String FILE_FMT  = SAVES_DIR + "/slot_%d.sav";
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static File slotFile(int slot) {
        return new File(String.format(FILE_FMT, slot));
    }

    public static boolean slotExists(int slot) {
        return slotFile(slot).exists();
    }

    // Best-effort slot delete. Returns true iff the file is gone afterward (either we
    // deleted it or it was never there). Title screen calls this on Del + confirm.
    public static boolean delete(int slot) {
        File f = slotFile(slot);
        if (!f.exists()) return true;
        return f.delete();
    }

    // Lightweight summary for the title screen: when it was saved and what the lead is.
    // Returns null for empty/corrupt slots.
    public static SlotInfo readSlotInfo(int slot) {
        File f = slotFile(slot);
        if (!f.exists()) return null;
        Map<String, String> kv = readKeyValues(f);
        if (kv == null || kv.isEmpty()) return null;
        SlotInfo info = new SlotInfo();
        info.savedAt = kv.getOrDefault("saved_at", "");
        info.playerName = kv.getOrDefault("player.name", "");
        int partySize = parseIntOrDefault(kv.get("party.size"), 0);
        info.partySize = partySize;
        if (partySize > 0) {
            info.leadName  = kv.getOrDefault("party.0.name", "?");
            info.leadLevel = parseIntOrDefault(kv.get("party.0.level"), 0);
        } else {
            info.leadName  = "";
            info.leadLevel = 0;
        }
        info.bossDefeated = "true".equalsIgnoreCase(kv.get("boss_defeated"));
        return info;
    }

    // Snapshot the whole gp state to the given slot. Best-effort: a failed write logs but
    // doesn't throw, so a save attempt never crashes the game.
    public static boolean save(int slot, GamePanel gp) {
        File dir = new File(SAVES_DIR);
        if (!dir.exists()) dir.mkdirs();
        File f = slotFile(slot);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write("slot=" + slot); w.newLine();
            w.write("saved_at=" + TS.format(new Date())); w.newLine();

            w.write("player.name=" + (gp.playerName == null ? "" : gp.playerName)); w.newLine();
            w.write("player.x=" + gp.player.worldX); w.newLine();
            w.write("player.y=" + gp.player.worldY); w.newLine();
            w.write("player.dir=" + gp.player.direction); w.newLine();

            // Persist the surviving non-boss object set keyed by (name, worldX, worldY).
            // On load, AssetSetter spawns everything afresh; we drop any spawned object
            // not present in this list. Boss state is a separate boolean.
            List<String> survivingObjects = new ArrayList<>();
            boolean bossAlive = false;
            for (SuperObject o : gp.obj) {
                if (o == null) continue;
                if ("Boss".equals(o.name)) { bossAlive = true; continue; }
                survivingObjects.add(o.name + "," + o.worldX + "," + o.worldY);
            }
            w.write("boss_defeated=" + (!bossAlive)); w.newLine();
            w.write("surviving.size=" + survivingObjects.size()); w.newLine();
            for (int i = 0; i < survivingObjects.size(); i++) {
                w.write("surviving." + i + "=" + survivingObjects.get(i)); w.newLine();
            }

            writePokemonList(w, "party",  gp.playerPokemon.pokemonEquipped);
            writePokemonList(w, "pc",     gp.playerPokemon.pokemonInPC);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void writePokemonList(BufferedWriter w, String prefix, List<Pokemon> list)
            throws IOException {
        w.write(prefix + ".size=" + list.size()); w.newLine();
        for (int i = 0; i < list.size(); i++) {
            Pokemon p = list.get(i);
            if (p == null) continue;
            String base = prefix + "." + i + ".";
            w.write(base + "name="       + p.name); w.newLine();
            w.write(base + "level="      + p.level); w.newLine();
            w.write(base + "currentHP="  + p.currentHP); w.newLine();
            w.write(base + "totalExp="   + p.totalExp); w.newLine();
            w.write(base + "ivs="        + p.ivHP + "," + p.ivAttack + "," + p.ivDefense
                                       + "," + p.ivSpAttack + "," + p.ivSpDef + "," + p.ivSpeed);
            w.newLine();
            w.write(base + "evs="        + p.evHP + "," + p.evAttack + "," + p.evDefense
                                       + "," + p.evSpAttack + "," + p.evSpDef + "," + p.evSpeed);
            w.newLine();
            w.write(base + "shiny="      + p.shiny); w.newLine();
            w.write(base + "gender="     + safe(p.gender)); w.newLine();
            w.write(base + "ability="    + safe(p.ability)); w.newLine();
            StringBuilder moveStr = new StringBuilder();
            if (p.moves != null) {
                for (int m = 0; m < p.moves.size(); m++) {
                    if (m > 0) moveStr.append("|");
                    if (p.moves.get(m) != null) moveStr.append(p.moves.get(m).name);
                }
            }
            w.write(base + "moves=" + moveStr); w.newLine();
        }
    }

    // Apply a saved slot on top of a freshly-initialized gp: player position, party/PC,
    // missing objects, defeated boss. Caller is responsible for setting gameState to playState
    // after this returns successfully.
    public static boolean load(int slot, GamePanel gp) {
        File f = slotFile(slot);
        if (!f.exists()) return false;
        Map<String, String> kv = readKeyValues(f);
        if (kv == null) return false;

        // Player
        String name = kv.get("player.name");
        if (name != null && !name.isEmpty()) gp.playerName = name;
        Integer px = parseIntOrNull(kv.get("player.x"));
        Integer py = parseIntOrNull(kv.get("player.y"));
        if (px != null) gp.player.worldX = px;
        if (py != null) gp.player.worldY = py;
        String dir = kv.get("player.dir");
        if (dir != null && !dir.isEmpty()) gp.player.direction = dir;

        // Party + PC: drop the default party (constructed empty now, but defensive) and
        // rebuild from disk.
        gp.playerPokemon.clearAll();
        readPokemonList(kv, "party",  gp.playerPokemon.pokemonEquipped);
        readPokemonList(kv, "pc",     gp.playerPokemon.pokemonInPC);
        // PC contents auto-heal — sweep here so any save written before this rule
        // existed still presents fully-healed box mons after a load.
        gp.playerPokemon.healAllInPC();

        // Map objects: AssetSetter already spawned everything. Strip down to the surviving
        // set listed in the save. Boss is special-cased via boss_defeated.
        applyObjectDelta(gp, kv);

        return true;
    }

    private static void applyObjectDelta(GamePanel gp, Map<String, String> kv) {
        int n = parseIntOrDefault(kv.get("surviving.size"), 0);
        java.util.Set<String> survivors = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) {
            String row = kv.get("surviving." + i);
            if (row == null) continue;
            survivors.add(row);
        }
        boolean bossDefeated = "true".equalsIgnoreCase(kv.get("boss_defeated"));
        for (int i = 0; i < gp.obj.size(); i++) {
            SuperObject o = gp.obj.get(i);
            if (o == null) continue;
            if ("Boss".equals(o.name)) {
                if (bossDefeated) gp.obj.set(i, null);
                continue;
            }
            String key = o.name + "," + o.worldX + "," + o.worldY;
            if (!survivors.contains(key)) {
                gp.obj.set(i, null);
            }
        }
    }

    private static void readPokemonList(Map<String, String> kv, String prefix, List<Pokemon> out) {
        int size = parseIntOrDefault(kv.get(prefix + ".size"), 0);
        GetPokemon gp = new GetPokemon();
        for (int i = 0; i < size; i++) {
            String base = prefix + "." + i + ".";
            String name = kv.get(base + "name");
            if (name == null || name.isEmpty()) continue;
            int level = parseIntOrDefault(kv.get(base + "level"), 1);
            Pokemon p = gp.findPokemon(name, level);
            if (p == null) continue;

            // Restore IVs/EVs *before* the recalc so the derived stats match the snapshot.
            int[] ivs = parseIntCsv(kv.get(base + "ivs"), 6);
            if (ivs != null) {
                p.ivHP = ivs[0]; p.ivAttack = ivs[1]; p.ivDefense = ivs[2];
                p.ivSpAttack = ivs[3]; p.ivSpDef = ivs[4]; p.ivSpeed = ivs[5];
            }
            int[] evs = parseIntCsv(kv.get(base + "evs"), 6);
            if (evs != null) {
                p.evHP = evs[0]; p.evAttack = evs[1]; p.evDefense = evs[2];
                p.evSpAttack = evs[3]; p.evSpDef = evs[4]; p.evSpeed = evs[5];
            }
            p.recalcStats();

            // currentHP: clamp to [0, maxHP] so a save written at full HP still tracks a
            // post-recalc maxHP that may differ slightly from the original (formula is
            // identical, so usually the same).
            Integer chp = parseIntOrNull(kv.get(base + "currentHP"));
            if (chp != null) {
                p.currentHP = Math.max(0, Math.min(p.maxHP, chp));
            }
            Integer texp = parseIntOrNull(kv.get(base + "totalExp"));
            if (texp != null) p.totalExp = texp;
            String shiny = kv.get(base + "shiny");
            if (shiny != null) p.shiny = "true".equalsIgnoreCase(shiny);
            String gender = kv.get(base + "gender");
            if (gender != null && !gender.isEmpty()) p.gender = gender;
            String ability = kv.get(base + "ability");
            if (ability != null && !ability.isEmpty()) p.ability = ability;

            // Moves: replace the random-generated set with the persisted ones (looked up
            // by name in the move pool). Skip null/missing names rather than crashing.
            String movesStr = kv.get(base + "moves");
            if (movesStr != null && !movesStr.isEmpty()) {
                List<Move> rebuilt = new ArrayList<>();
                for (String moveName : movesStr.split("\\|")) {
                    if (moveName.isEmpty()) continue;
                    Move m = Moves.findByName(moveName);
                    if (m != null) rebuilt.add(m);
                }
                if (!rebuilt.isEmpty()) p.moves = rebuilt;
            }

            out.add(p);
        }
    }

    // ---- parsing helpers ----

    private static Map<String, String> readKeyValues(File f) {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                map.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return map;
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static int parseIntOrDefault(String s, int def) {
        Integer v = parseIntOrNull(s);
        return v == null ? def : v;
    }

    private static int[] parseIntCsv(String s, int expectedLen) {
        if (s == null) return null;
        String[] parts = s.split(",");
        if (parts.length != expectedLen) return null;
        int[] out = new int[expectedLen];
        for (int i = 0; i < expectedLen; i++) {
            Integer v = parseIntOrNull(parts[i]);
            if (v == null) return null;
            out[i] = v;
        }
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static class SlotInfo {
        public String savedAt;
        public String playerName;
        public int partySize;
        public String leadName;
        public int leadLevel;
        public boolean bossDefeated;
    }
}
