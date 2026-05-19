package object;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import Main.GamePanel;
public class AssetSetter {
    
    GamePanel gp;

    public AssetSetter(GamePanel gp){
        this.gp = gp;
    }

    public void setObject(){
        gp.obj.add(new OBJ_Pokeball());
        gp.obj.get(gp.obj.size()-1).worldX = 37 * gp.tileSize;
        gp.obj.get(gp.obj.size()-1).worldY = 51 * gp.tileSize; //Y object is set + 1

        gp.obj.add(new OBJ_Pokeball());
        gp.obj.get(gp.obj.size()-1).worldX = 10 * gp.tileSize;
        gp.obj.get(gp.obj.size()-1).worldY = 10 * gp.tileSize;

        // Trainer boss "????" in the top-right walkable strip. End-game roster: six
        // legendaries at level 100. Optimal movesets fall out of the type-based picker
        // automatically (top-half tier bias at Lv 100 selects only end-game moves).
        // NOTE: every species name here must exist in PokemonStats.csv or it'll be
        // silently skipped during the roster build.
        gp.obj.add(OBJ_Boss.withTeam(100,
            "Xerneas", "Groudon", "Kyogre", "Rayquaza", "Mewtwo", "Arceus"));
        gp.obj.get(gp.obj.size()-1).worldX = 38 * gp.tileSize;
        gp.obj.get(gp.obj.size()-1).worldY = 11 * gp.tileSize;

        loadBushes("Route1.txt", 5);
    }

    private void loadBushes(String fileName, int tileIndexToBush){
        File file = new File("./src/res/maps/"+fileName);
        try {
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            int col = 0;
            int row = 0;

            while(col < gp.maxWorldCol && row < gp.maxWorldRow){
                String line = bufferedReader.readLine();

                while(col < gp.maxWorldCol){
                    String numbers[] = line.split(" ");
                    int num = Integer.parseInt(numbers[col]);
                    
                    if(num == tileIndexToBush){
                        gp.obj.add(new OBJ_BushRoute1());
                        gp.obj.get(gp.obj.size()-1).worldX = col * gp.tileSize;
                        gp.obj.get(gp.obj.size()-1).worldY = row * gp.tileSize;
                    }
                    col++;
                }
                if(col == gp.maxWorldCol){
                    col = 0;
                    row++;
                }
            }
            bufferedReader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
