package object;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
public class OBJ_Pokeball extends SuperObject{
    public OBJ_Pokeball(){
        name = "Pokeball";
        try{
            image = ImageIO.read(new File("./src/res/objects/pokeball.png"));
        }catch(IOException e){
            e.printStackTrace();
        }

    }
}
