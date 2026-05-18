package PlayerInventory;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.GetPokemonImages;
import Pokemon.Pokemon;
import PokemonEncounters.PokemonEncounter;

public class OpenPlayerInventory {
    private final GamePanel gp;
    private final KeyHandler keyH;
    private final BufferedImage[] inventoryAssets = new BufferedImage[3];
    private final GetPokemonImages pokemonImages = new GetPokemonImages();

    // Layout of the six slots inside 1_PokemonEquipped.png, as fractions of the image
    // (and thus the screen, since we scale the image to fill). Two columns, three rows.
    // The asset is asymmetric: the right column is ~32px wider AND ~70px lower than the
    // left in the 1404x1120 source, so positions are per-column. Values measured by pixel
    // brightness sampling against the source.
    private static final double[] COL_X = { 0.0214, 0.520 };
    private static final double[] COL_W = { 0.456, 0.463 };
    private static final double[][] ROW_Y = {
        { 0.129, 0.379, 0.629 }, // left column
        { 0.191, 0.441, 0.691 }, // right column
    };
    private static final double SLOT_H = 0.188;

    private Font slotName;
    private Font slotMeta;

    // Edge detection so a held key doesn't oscillate open/close every frame.
    private boolean prevP, prevI;

    public OpenPlayerInventory(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        PokemonEncounter.loadIndexedImages("./src/res/PlayerInventoryAssets", inventoryAssets);
        loadFonts();
    }

    private void loadFonts() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            slotName = base.deriveFont(Font.BOLD, 30f);
            slotMeta = base.deriveFont(Font.BOLD, 24f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void draw(Graphics2D g2) {
        openInventory();
        if (gp.gameState == gp.InventoryPokemonState) {
            drawInventoryPokemon(g2);
        }
        if (gp.gameState == gp.InventoryBagState) {
            drawInventoryBag(g2);
        }
    }

    // P toggles the party menu, I toggles the bag. Edge-detected so holding the key down
    // doesn't ping-pong the state every frame.
    private void openInventory() {
        boolean justP = keyH.pPressed && !prevP;
        boolean justI = keyH.iPressed && !prevI;
        prevP = keyH.pPressed;
        prevI = keyH.iPressed;

        if (gp.gameState == gp.playState) {
            if (justP)      gp.gameState = gp.InventoryPokemonState;
            else if (justI) gp.gameState = gp.InventoryBagState;
        } else if (gp.gameState == gp.InventoryPokemonState && justP) {
            gp.gameState = gp.playState;
        } else if (gp.gameState == gp.InventoryBagState && justI) {
            gp.gameState = gp.playState;
        }
    }

    private void drawInventoryPokemon(Graphics2D g2) {
        BufferedImage bg = inventoryAssets[1];
        if (bg != null) {
            g2.drawImage(bg, 0, 0, gp.screenWidth, gp.screenHeight, null);
        }
        for (int i = 0; i < 6; i++) {
            // Rows-first ordering: top-left=1, top-right=2, middle-left=3, etc.
            int col = i % 2;
            int row = i / 2;
            int x = (int) (COL_X[col] * gp.screenWidth);
            int y = (int) (ROW_Y[col][row] * gp.screenHeight);
            int w = (int) (COL_W[col] * gp.screenWidth);
            int h = (int) (SLOT_H * gp.screenHeight);

            if (i < gp.playerPokemon.pokemonEquipped.size()) {
                drawSlot(g2, gp.playerPokemon.pokemonEquipped.get(i), x, y, w, h);
            }
        }
    }

    // One party slot: front sprite on the left, then name + level + HP bar/text on the right.
    private void drawSlot(Graphics2D g2, Pokemon p, int x, int y, int w, int h) {
        int spriteSize = h - 12;
        int spriteX = x + 8;
        int spriteY = y + (h - spriteSize) / 2;
        BufferedImage sprite = pokemonImages.getPokemonFront(p);
        if (sprite != null) {
            g2.drawImage(sprite, spriteX, spriteY, spriteSize, spriteSize, null);
        }

        int textX = spriteX + spriteSize + 12;
        int nameBaseline = y + 36;

        if (slotName != null) g2.setFont(slotName);
        g2.setColor(Color.white);
        g2.drawString(p.name, textX, nameBaseline);

        if (slotMeta != null) g2.setFont(slotMeta);
        g2.setColor(new Color(220, 220, 220));
        g2.drawString("Lv " + p.level, textX, nameBaseline + 24);

        int barX = textX;
        int barY = y + h - 50;
        int barW = (x + w) - barX - 12;
        int barH = 10;
        drawHpBar(g2, p, barX, barY, barW, barH);
        g2.setColor(Color.white);
        g2.drawString(p.currentHP + "/" + p.maxHP, barX, barY + barH + 18);
    }

    private void drawHpBar(Graphics2D g2, Pokemon p, int x, int y, int w, int h) {
        if (p.maxHP <= 0 || w <= 0) return;
        double ratio = Math.max(0.0, Math.min(1.0, p.currentHP / (double) p.maxHP));
        int filled = (int) Math.round(w * ratio);
        Color fill;
        if (ratio > 0.5)      fill = new Color(80, 200, 80);
        else if (ratio > 0.2) fill = new Color(230, 200, 60);
        else                  fill = new Color(220, 60, 60);
        g2.setColor(new Color(20, 20, 20));
        g2.fillRect(x, y, w, h);
        g2.setColor(fill);
        g2.fillRect(x, y, filled, h);
    }

    private void drawInventoryBag(Graphics2D g2) {
        // TODO: bag inventory screen
    }
}
