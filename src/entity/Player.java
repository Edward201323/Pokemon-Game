package entity;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import Main.GamePanel;
import Main.KeyHandler;
import PokemonEncounters.PlayerObjectInteraction;

public class Player extends Entity {
    private static final String UP = "up";
    private static final String DOWN = "down";
    private static final String LEFT = "left";
    private static final String RIGHT = "right";
    private static final String[] DIRECTIONS = {UP, DOWN, LEFT, RIGHT};
    private static final int SPRITE_FRAMES = 3;

    private final GamePanel gp;
    private final KeyHandler keyH;
    private final PlayerObjectInteraction playerObjectInteraction;
    public final int screenX;
    public final int screenY;

    // Indexed [direction][frame]. "top" = upper half, "bottom" = lower half.
    private final BufferedImage[][] topSprites = new BufferedImage[4][SPRITE_FRAMES];
    private final BufferedImage[][] bottomSprites = new BufferedImage[4][SPRITE_FRAMES];

    public Player(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;

        screenX = gp.screenWidth / 2 - (gp.tileSize / 2);
        screenY = gp.screenHeight / 2 - (gp.tileSize / 2);

        solidArea = new Rectangle(16, 8, 16, 10);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;

        playerObjectInteraction = new PlayerObjectInteraction(gp);

        setDefaultValues();
        loadPlayerImages();
    }

    private void setDefaultValues() {
        worldX = gp.tileSize * 10;
        worldY = gp.tileSize * 93;
        speed = 10;
        direction = DOWN;
    }

    private void loadPlayerImages() {
        for (int d = 0; d < DIRECTIONS.length; d++) {
            for (int f = 0; f < SPRITE_FRAMES; f++) {
                topSprites[d][f] = readImage("player_" + DIRECTIONS[d] + "_" + (f + 1) + ".png");
                bottomSprites[d][f] = readImage("!player_" + DIRECTIONS[d] + "_" + (f + 1) + ".png");
            }
        }
    }

    private BufferedImage readImage(String name) {
        try {
            return ImageIO.read(new File("./src/res/player/" + name));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void update() {
        if (!(keyH.upPressed || keyH.downPressed || keyH.leftPressed || keyH.rightPressed)) {
            return;
        }
        if (keyH.upPressed) direction = UP;
        else if (keyH.downPressed) direction = DOWN;
        else if (keyH.leftPressed) direction = LEFT;
        else if (keyH.rightPressed) direction = RIGHT;

        collisionOn = false;
        gp.cChecker.checkTile(this);

        int objIndex = gp.cChecker.checkObject(this, true);
        playerObjectInteraction.objectInteraction(objIndex);

        if (!collisionOn) {
            switch (direction) {
                case UP:    worldY -= speed; break;
                case DOWN:  worldY += speed; break;
                case LEFT:  worldX -= speed; break;
                case RIGHT: worldX += speed; break;
            }
        }

        spriteCounter++;
        if (spriteCounter > 12) {
            spriteNum = spriteNum % SPRITE_FRAMES + 1;
            spriteCounter = 0;
        }
    }

    public void drawPlayerHalf(Graphics2D g2, boolean drawTop) {
        int d = directionIndex(direction);
        int frame = Math.max(0, Math.min(SPRITE_FRAMES - 1, spriteNum - 1));
        BufferedImage image = drawTop ? topSprites[d][frame] : bottomSprites[d][frame];

        if (drawTop) {
            int newTileSize = (int) (gp.tileSize / 1.5);
            g2.drawImage(image, screenX, screenY - 16, gp.tileSize, newTileSize, null);
        } else {
            g2.drawImage(image, screenX, screenY + 16, gp.tileSize, gp.tileSize / 3, null);
        }
    }

    private static int directionIndex(String dir) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i].equals(dir)) return i;
        }
        return 1; // default DOWN
    }
}
