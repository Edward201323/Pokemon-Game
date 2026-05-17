package Pokemon;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import Main.GamePanel;
public class GetPokemonImages {
    GamePanel gp;
    public GetPokemonImages(GamePanel gp){
        this.gp = gp;
    }
    
    public BufferedImage getPokemonBack(Pokemon pokemon){
        File directory = new File("./src/res/PokemonBackImages");
        File[] files = directory.listFiles();
        for(File file: files){
            String fileName = file.getName();
            BufferedImage pokemonImage;
            if(pokemon.NameInFile.equals(fileName)){
                try {
                    pokemonImage = ImageIO.read(new File("./src/res/PokemonBackImages/"+fileName));
                    return pokemonImage;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
 
 
 
    public BufferedImage getPokemonFront(Pokemon pokemon) {
        File directory = new File("./src/res/PokemonFrontImages");
        File[] files = directory.listFiles();
        for(File file: files){
            String fileName = file.getName();
            BufferedImage pokemonImage;
            if(gp.wildPokemon.NameInFile.equals(fileName)){
                try {
                    pokemonImage = ImageIO.read(new File("./src/res/PokemonFrontImages/"+fileName));
                    return pokemonImage;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

}
