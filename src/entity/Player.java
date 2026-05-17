package entity;
import Main.GamePanel;
import Main.KeyHandler;
import PokemonEncounters.PlayerObjectInteraction;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class Player extends Entity{
    GamePanel gp;
    KeyHandler keyH;
    PlayerObjectInteraction playerObjectInteraction;
    public final int screenX;
    public final int screenY;
    public Player(GamePanel gp, KeyHandler keyH){
        this.gp = gp;
        this.keyH = keyH;

        screenX = gp.screenWidth/2 - (gp.tileSize/2);
        screenY = gp.screenHeight/2 - (gp.tileSize/2);

        //character collision area
        solidArea = new Rectangle();
        solidArea.x = 16;
        solidArea.y = 8;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
        solidArea.width = 16;
        solidArea.height = 10;

        playerObjectInteraction = new PlayerObjectInteraction(gp);

        setDefaultValues();
        getPlayerImage();
    }

    public void setDefaultValues(){
        //where the character starts on the map(ex: worldX = gp.tileSize(x value), worldY = gp.tileSize(y value))
        worldX = gp.tileSize*10;
        worldY = gp.tileSize*93;
        speed = 10; //player speed
        direction = "down";
    }
    
    public void getPlayerImage(){
        try{
            playerDown1 = ImageIO.read(new File("./src/res/player/player_down_1.png"));
            playerDown2 = ImageIO.read(new File("./src/res/player/player_down_2.png"));
            playerDown3 = ImageIO.read(new File("./src/res/player/player_down_3.png"));

            playerLeft1 = ImageIO.read(new File("./src/res/player/player_left_1.png"));
            playerLeft2 = ImageIO.read(new File("./src/res/player/player_left_2.png"));
            playerLeft3 = ImageIO.read(new File("./src/res/player/player_left_3.png"));

            playerRight1 = ImageIO.read(new File("./src/res/player/player_right_1.png"));
            playerRight2 = ImageIO.read(new File("./src/res/player/player_right_2.png"));
            playerRight3 = ImageIO.read(new File("./src/res/player/player_right_3.png"));

            playerUp1 = ImageIO.read(new File("./src/res/player/player_up_1.png"));
            playerUp2 = ImageIO.read(new File("./src/res/player/player_up_2.png"));
            playerUp3 = ImageIO.read(new File("./src/res/player/player_up_3.png"));

            
            dplayerDown1 = ImageIO.read(new File("./src/res/player/!player_down_1.png"));
            dplayerDown2 = ImageIO.read(new File("./src/res/player/!player_down_2.png"));
            dplayerDown3 = ImageIO.read(new File("./src/res/player/!player_down_3.png"));

            dplayerLeft1 = ImageIO.read(new File("./src/res/player/!player_left_1.png"));
            dplayerLeft2 = ImageIO.read(new File("./src/res/player/!player_left_2.png"));
            dplayerLeft3 = ImageIO.read(new File("./src/res/player/!player_left_3.png"));

            dplayerRight1 = ImageIO.read(new File("./src/res/player/!player_right_1.png"));
            dplayerRight2 = ImageIO.read(new File("./src/res/player/!player_right_2.png"));
            dplayerRight3 = ImageIO.read(new File("./src/res/player/!player_right_3.png"));

            dplayerUp1 = ImageIO.read(new File("./src/res/player/!player_up_1.png"));
            dplayerUp2 = ImageIO.read(new File("./src/res/player/!player_up_2.png"));
            dplayerUp3 = ImageIO.read(new File("./src/res/player/!player_up_3.png"));

        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void update(){
        if(keyH.upPressed == true || keyH.downPressed == true || keyH.leftPressed == true || keyH.rightPressed == true){
             if(keyH.upPressed == true){
                 direction = "up";
             }
             else if(keyH.downPressed == true){
                 direction = "down";
             }
             else if(keyH.leftPressed == true){
                 direction = "left";
             }
             else if(keyH.rightPressed == true){
                 direction = "right";
             }
             
             // check tile collision
             collisionOn = false;
             gp.cChecker.checkTile(this);
             
             //Check Object Collision
             int objIndex = gp.cChecker.checkObject(this, true);
             playerObjectInteraction.objectInteraction(objIndex);

             //If collision is false, player can move
             if(collisionOn == false){
                if (direction.equals("up")) {
                    worldY -= speed;
                } else if (direction.equals("down")) {
                    worldY += speed;
                } else if (direction.equals("left")) {
                    worldX -= speed;
                } else if (direction.equals("right")) {
                    worldX += speed;
                }
                
             }

             spriteCounter++;
             if(spriteCounter>12){
                 if(spriteNum == 1){
                     spriteNum = 2;
                 }
                 else if(spriteNum == 2){
                     spriteNum = 3;
                 }
                 else if(spriteNum == 3){
                    spriteNum = 1;
                 }
                 spriteCounter = 0;
             }
        }
    }

    public void drawPlayerHalf(Graphics2D g2, boolean drawTop){
        BufferedImage image = null;
        if (direction == "up") {
            if (spriteNum == 1) {
                if (drawTop == true) {
                    image = playerUp1;
                } else {
                    image = dplayerUp1;
                }
            } else if (spriteNum == 2) {
                if (drawTop == true) {
                    image = playerUp2;
                } else {
                    image = dplayerUp2;
                }
            } else if (spriteNum == 3) {
                if (drawTop == true) {
                    image = playerUp3;
                } else {
                    image = dplayerUp3;
                }
            }
        } else if (direction == "down") {
            if (spriteNum == 1) {
                if (drawTop == true) {
                    image = playerDown1;
                } else {
                    image = dplayerDown1;
                }
            } else if (spriteNum == 2) {
                if (drawTop == true) {
                    image = playerDown2;
                } else {
                    image = dplayerDown2;
                }
            } else if (spriteNum == 3) {
                if (drawTop == true) {
                    image = playerDown3;
                } else {
                    image = dplayerDown3;
                }
            }
        } else if (direction == "left") {
            if (spriteNum == 1) {
                if (drawTop == true) {
                    image = playerLeft1;
                } else {
                    image = dplayerLeft1;
                }
            } else if (spriteNum == 2) {
                if (drawTop == true) {
                    image = playerLeft2;
                } else {
                    image = dplayerLeft2;
                }
            } else if (spriteNum == 3) {
                if (drawTop == true) {
                    image = playerLeft3;
                } else {
                    image = dplayerLeft3;
                }
            }
        } else if (direction == "right") {
            if (spriteNum == 1) {
                if (drawTop == true) {
                    image = playerRight1;
                } else {
                    image = dplayerRight1;
                }
            } else if (spriteNum == 2) {
                if (drawTop == true) {
                    image = playerRight2;
                } else {
                    image = dplayerRight2;
                }
            } else if (spriteNum == 3) {
                if (drawTop == true) {
                    image = playerRight3;
                } else {
                    image = dplayerRight3;
                }
            }
        }
        
        if(drawTop == true){
            int newTileSize = (int) (gp.tileSize / 1.5);  // calculate new tile size by dividing by 1.5
            g2.drawImage(image, screenX, screenY-16, gp.tileSize, newTileSize, null);        
        }else{
            g2.drawImage(image, screenX, screenY+16, gp.tileSize, gp.tileSize/3, null); 
        }
    }
}