package Pokemon;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Moves {
    private static final String CSV_PATH = "./src/res/movesData/moves.csv";
    private static final Map<String, Move[]> BY_TYPE = load();

    private static Map<String, Move[]> load() {
        Map<String, Move[]> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_PATH))) {
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] c = line.split(",");
                if (c.length < 7) continue;
                String type = c[0].trim().toLowerCase();
                Move physical = new Move(
                    c[1].trim(), type,
                    Integer.parseInt(c[2].trim()), parseAccuracy(c[3]), true);
                Move special = new Move(
                    c[4].trim(), type,
                    Integer.parseInt(c[5].trim()), parseAccuracy(c[6]), false);
                map.put(type, new Move[] { physical, special });
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

    // Returns up to 4 moves: physical+special for type1, plus physical+special for type2 if distinct.
    public static List<Move> getMoves(String type1, String type2) {
        List<Move> result = new ArrayList<>();
        addType(result, type1);
        if (type2 != null && !type2.equalsIgnoreCase("none") && !type2.equalsIgnoreCase(type1)) {
            addType(result, type2);
        }
        return result;
    }

    private static void addType(List<Move> out, String type) {
        if (type == null) return;
        Move[] moves = BY_TYPE.get(type.toLowerCase());
        if (moves != null) Collections.addAll(out, moves);
    }
}
