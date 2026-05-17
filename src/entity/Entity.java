package entity;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
public class Entity{
    public int worldX, worldY;
    public int speed;
    public BufferedImage playerDown1, playerDown2, playerDown3, playerLeft1, playerLeft2, playerLeft3,
    playerRight1, playerRight2, playerRight3, playerUp1, playerUp2, playerUp3,
    dplayerDown1, dplayerDown2, dplayerDown3, dplayerLeft1, dplayerLeft2, dplayerLeft3,
    dplayerRight1, dplayerRight2, dplayerRight3, dplayerUp1, dplayerUp2, dplayerUp3;
    public String direction;
    public int spriteCounter = 0;
    public int spriteNum = 1;
    public Rectangle solidArea;
    public int solidAreaDefaultX, solidAreaDefaultY;
    public boolean collisionOn = true;
}