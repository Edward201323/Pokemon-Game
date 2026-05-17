package Pokemon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class GetPokemonImages {
    private static final Map<String, BufferedImage> FRONT = loadDir("./src/res/PokemonFrontImages");
    private static final Map<String, BufferedImage> BACK = loadDir("./src/res/PokemonBackImages");

    public GetPokemonImages() {
    }

    private static Map<String, BufferedImage> loadDir(String path) {
        Map<String, BufferedImage> map = new HashMap<>();
        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null) return map;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".") || !name.toLowerCase().endsWith(".png")) continue;
            try {
                map.put(name, ImageIO.read(file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return map;
    }

    public BufferedImage getPokemonBack(Pokemon pokemon) {
        return pokemon == null ? null : BACK.get(pokemon.NameInFile);
    }

    public BufferedImage getPokemonFront(Pokemon pokemon) {
        return pokemon == null ? null : FRONT.get(pokemon.NameInFile);
    }
}
