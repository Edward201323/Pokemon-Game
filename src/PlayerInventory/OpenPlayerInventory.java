package PlayerInventory;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import Main.GamePanel;
import Main.KeyHandler;
public class OpenPlayerInventory {
    GamePanel gp;
    KeyHandler keyH;
    Graphics2D g2;

    BufferedImage[] inventoryAssets;

    int countA = 0;
    public OpenPlayerInventory(GamePanel gp, KeyHandler keyH){
        this.gp = gp;
        this.keyH = keyH;
        this.inventoryAssets = new BufferedImage[3];

        getInventoryAssets();
        draw(g2);
    }

    public void draw(Graphics2D g2){
        this.g2 = g2;
        openInventory();
        // closeInventory();
        if(gp.gameState == gp.InventoryPokemonState){
            drawInventoryPokemon();
        }
        if(gp.gameState == gp.InventoryBagState){
            drawInventoryBag();
        }
    }

    private void openInventory(){
        if(gp.gameState == gp.playState){
            if(keyH.pPressed == true){
                gp.gameState = gp.InventoryPokemonState;
            }
            if(keyH.iPressed == true){
                gp.gameState = gp.InventoryBagState;
            }
        }
    }


    private void drawInventoryPokemon(){

    }

    private void drawInventoryBag(){

    }

    private void getInventoryAssets(){
        File directory = new File("./src/res/PlayerInventoryAssets");
        File[] files = directory.listFiles();
        for(File file: files){
            String fileName = file.getName();
            String[] parts = fileName.split("_");
            int fileIndex = Integer.parseInt(parts[0]);
            try {
                inventoryAssets[fileIndex] = ImageIO.read(new File("./src/res/PlayerInventoryAssets/"+fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
