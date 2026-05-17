package tile;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import javax.imageio.ImageIO;
import Main.GamePanel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
public class TileManager {
    
    GamePanel gp;
    public Tile[] tile;
    public int mapTileNum[][];
    
    public TileManager(GamePanel gp) {
        this.gp = gp;
        this.tile = new Tile[266]; //Number of assets of tiles + 1
        for(int i = 0; i<266; i++){
            tile[i] = new Tile();
        }
        mapTileNum = new int[gp.maxWorldCol][gp.maxWorldRow];
        getTileImage();

        loadMap("Route1.txt");
    }

    public void getTileImage(){
        File directory = new File("./src/res/tiles");
        File[] files = directory.listFiles();
        for(File file: files){
            boolean hasUnderline = false;
            String fileName = file.getName();
            String[] partsDot = fileName.split("\\.");
            String[] partsUnderline = null;
            if(partsDot[0].contains("_")){
                hasUnderline = true;
                partsUnderline = partsDot[0].split("_");
            }

            int imageIndex = 0;
            if(hasUnderline==true){
                imageIndex = Integer.parseInt(partsUnderline[0]);
            }else{
                imageIndex = Integer.parseInt(partsDot[0]);
            }
            hasUnderline = false;

            
            tile[imageIndex].fileName = fileName;
        }

        try {
            for(int i = 0; i<tile.length; i++){
                tile[i].image = ImageIO.read(new File("./src/res/tiles/"+tile[i].fileName));

                BufferedImage scaledImage = new BufferedImage(gp.tileSize, gp.tileSize, tile[i].image.getType());
                Graphics2D g2 = scaledImage.createGraphics();
                g2.drawImage(tile[0].image, 0, 0, gp.tileSize, gp.tileSize, null);
                tile[0].image = scaledImage;

                if(tile[i].fileName.contains("_false")){
                    tile[i].collision = false;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadMap(String fileName){
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

                    mapTileNum[col][row] = num;
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
    
    public void draw(Graphics2D g2){
        int worldCol = 0;
        int worldRow = 0;

        while(worldCol<gp.maxWorldCol&&worldRow<gp.maxWorldRow){

            int tileNum = mapTileNum[worldCol][worldRow];
            int worldX = worldCol * gp.tileSize;
            int worldY = worldRow * gp.tileSize;
            int screenX = worldX - gp.player.worldX + gp.player.screenX;
            int screenY = worldY - gp.player.worldY + gp.player.screenY;

            g2.drawImage(tile[tileNum].image, screenX, screenY, gp.tileSize, gp.tileSize, null);
            worldCol++;

            if(worldCol==gp.maxWorldCol){
                worldCol = 0;
                worldRow++;
            }
        }
    }
}
