package object;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayList;
public class OBJ_BushRoute1 extends SuperObject{
    ArrayList<String> encounterablePokemon;
    public OBJ_BushRoute1(){
        name = "BushRoute1";
        try{
            image = ImageIO.read(new File("./src/res/objects/bush_route1.png"));
        }catch(IOException e){
            e.printStackTrace();
        }
        encounterablePokemon = new ArrayList<>();
    }
    
    public void addEncounterablePokemon(){
        encounterablePokemon.add("zigzagoon");
        encounterablePokemon.add("bunnelby");
        encounterablePokemon.add("sentret");
        encounterablePokemon.add("aipom");
        encounterablePokemon.add("starly");
        
        encounterablePokemon.add("houndour");
        encounterablePokemon.add("buneary");
        
        encounterablePokemon.add("fletchling");
        encounterablePokemon.add("pancham");
        encounterablePokemon.add("pikachu");

        encounterablePokemon.add("absol");

        encounterablePokemon.add("zapdos");
        encounterablePokemon.add("moltres");
        encounterablePokemon.add("articuno");
    }
}
