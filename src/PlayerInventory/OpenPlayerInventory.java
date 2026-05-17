package PlayerInventory;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import Main.GamePanel;
import Main.KeyHandler;
import PokemonEncounters.PokemonEncounter;

public class OpenPlayerInventory {
    private final GamePanel gp;
    private final KeyHandler keyH;
    private final BufferedImage[] inventoryAssets = new BufferedImage[3];

    public OpenPlayerInventory(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        PokemonEncounter.loadIndexedImages("./src/res/PlayerInventoryAssets", inventoryAssets);
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

    private void openInventory() {
        if (gp.gameState == gp.playState) {
            if (keyH.pPressed) gp.gameState = gp.InventoryPokemonState;
            if (keyH.iPressed) gp.gameState = gp.InventoryBagState;
        }
    }

    private void drawInventoryPokemon(Graphics2D g2) {
        // TODO: pokemon inventory screen
    }

    private void drawInventoryBag(Graphics2D g2) {
        // TODO: bag inventory screen
    }
}
