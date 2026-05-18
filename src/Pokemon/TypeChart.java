package Pokemon;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Full 18-type matchup table. Returns a damage multiplier for a move of `moveType`
// striking a defender with up to two types. Dual-type defenders multiply the two
// per-type values (so 2x * 2x = 4x super-effective, 0.5 * 0.5 = 0.25x).
public class TypeChart {
    public static double effectiveness(String moveType, String defType1, String defType2) {
        double m = single(moveType, defType1);
        if (defType2 != null && !defType2.equalsIgnoreCase("none") && !defType2.equalsIgnoreCase(defType1)) {
            m *= single(moveType, defType2);
        }
        return m;
    }

    private static double single(String atk, String def) {
        if (atk == null || def == null) return 1.0;
        Double v = MAP.get(atk.toLowerCase(Locale.ROOT) + ">" + def.toLowerCase(Locale.ROOT));
        return v != null ? v : 1.0;
    }

    private static final Map<String, Double> MAP = build();

    private static Map<String, Double> build() {
        Map<String, Double> m = new HashMap<>();
        nv(m, "normal",   "rock", "steel");
        im(m, "normal",   "ghost");

        se(m, "fire",     "grass", "ice", "bug", "steel");
        nv(m, "fire",     "fire", "water", "rock", "dragon");

        se(m, "water",    "fire", "ground", "rock");
        nv(m, "water",    "water", "grass", "dragon");

        se(m, "electric", "water", "flying");
        nv(m, "electric", "electric", "grass", "dragon");
        im(m, "electric", "ground");

        se(m, "grass",    "water", "ground", "rock");
        nv(m, "grass",    "fire", "grass", "poison", "flying", "bug", "dragon", "steel");

        se(m, "ice",      "grass", "ground", "flying", "dragon");
        nv(m, "ice",      "fire", "water", "ice", "steel");

        se(m, "fighting", "normal", "ice", "rock", "dark", "steel");
        nv(m, "fighting", "poison", "flying", "psychic", "bug", "fairy");
        im(m, "fighting", "ghost");

        se(m, "poison",   "grass", "fairy");
        nv(m, "poison",   "poison", "ground", "rock", "ghost");
        im(m, "poison",   "steel");

        se(m, "ground",   "fire", "electric", "poison", "rock", "steel");
        nv(m, "ground",   "grass", "bug");
        im(m, "ground",   "flying");

        se(m, "flying",   "grass", "fighting", "bug");
        nv(m, "flying",   "electric", "rock", "steel");

        se(m, "psychic",  "fighting", "poison");
        nv(m, "psychic",  "psychic", "steel");
        im(m, "psychic",  "dark");

        se(m, "bug",      "grass", "psychic", "dark");
        nv(m, "bug",      "fire", "fighting", "poison", "flying", "ghost", "steel", "fairy");

        se(m, "rock",     "fire", "ice", "flying", "bug");
        nv(m, "rock",     "fighting", "ground", "steel");

        se(m, "ghost",    "psychic", "ghost");
        nv(m, "ghost",    "dark");
        im(m, "ghost",    "normal");

        se(m, "dragon",   "dragon");
        nv(m, "dragon",   "steel");
        im(m, "dragon",   "fairy");

        se(m, "dark",     "psychic", "ghost");
        nv(m, "dark",     "fighting", "dark", "fairy");

        se(m, "steel",    "ice", "rock", "fairy");
        nv(m, "steel",    "fire", "water", "electric", "steel");

        se(m, "fairy",    "fighting", "dragon", "dark");
        nv(m, "fairy",    "fire", "poison", "steel");
        return m;
    }

    private static void se(Map<String,Double> m, String atk, String... defs) {
        for (String d : defs) m.put(atk + ">" + d, 2.0);
    }
    private static void nv(Map<String,Double> m, String atk, String... defs) {
        for (String d : defs) m.put(atk + ">" + d, 0.5);
    }
    private static void im(Map<String,Double> m, String atk, String... defs) {
        for (String d : defs) m.put(atk + ">" + d, 0.0);
    }
}
