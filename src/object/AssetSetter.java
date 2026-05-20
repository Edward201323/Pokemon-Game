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

        // Trainer boss "????" in the top-right walkable strip. End-game roster: seven
        // legendaries at level 100 with hand-picked movesets. Reshiram uses its
        // auto-generated picker (signatures + type moves) since no override was specified.
        // Movesets shorter than 4 leave the remaining slots empty.
        java.util.List<OBJ_Boss.Member> bossTeam = new java.util.ArrayList<>();
        bossTeam.add(new OBJ_Boss.Member("Reshiram", 100));
        bossTeam.add(new OBJ_Boss.Member("Darkrai", 100,
            new String[] { "Dark Pulse", "Shadow Ball", "Ice Beam", "Thunderbolt" }));
        bossTeam.add(new OBJ_Boss.Member("Groudon", 100,
            new String[] { "Precipice Blades", "Fire Punch", "Solar Beam" }));
        bossTeam.add(new OBJ_Boss.Member("Kyogre", 100,
            new String[] { "Origin Pulse", "Thunderbolt", "Ice Beam" }));
        bossTeam.add(new OBJ_Boss.Member("Rayquaza", 100,
            new String[] { "Dragon Ascent", "Outrage", "Earthquake" }));
        bossTeam.add(new OBJ_Boss.Member("Mewtwo", 100,
            new String[] { "Psystrike", "Shadow Ball", "Fire Blast", "Ice Beam" }));
        bossTeam.add(new OBJ_Boss.Member("Arceus", 100,
            new String[] { "Judgment", "Draco Meteor", "Close Combat", "Flamethrower" }));
        gp.obj.add(new OBJ_Boss(bossTeam));
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
