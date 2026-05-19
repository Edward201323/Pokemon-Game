package Save;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.GetPokemon;
import Pokemon.GetPokemonImages;
import Pokemon.Pokemon;

// One-time starter pick shown after picking an empty slot from the title screen.
// Horizontally scrolls through the canonical Gen-1..Gen-6 starters at Lv 5. Enter
// commits the choice into the (empty) party and drops the player into playState.
public class StarterSelection {
    public static final int STARTER_LEVEL = 5;

    // Gen 1-6 canonical starters. All 18 are confirmed present in PokemonStats.csv.
    // Gen 7 (Rowlet/Litten/Popplio) is absent from the CSV so they're omitted.
    private static final List<String> STARTERS = Arrays.asList(
        "Bulbasaur", "Charmander", "Squirtle",
        "Chikorita", "Cyndaquil", "Totodile",
        "Treecko",   "Torchic",   "Mudkip",
        "Turtwig",   "Chimchar",  "Piplup",
        "Snivy",     "Tepig",     "Oshawott",
        "Chespin",   "Fennekin",  "Froakie"
    );

    private final GamePanel gp;
    private final KeyHandler keyH;
    private final GetPokemon getPokemon = new GetPokemon();
    private final GetPokemonImages images = new GetPokemonImages();
    private final Pokemon[] roster = new Pokemon[STARTERS.size()];

    private int cursor = 0;
    private Font titleFont, nameFont, hintFont;

    private boolean prevLeft, prevRight, prevUp, prevDown, prevEnter;

    public StarterSelection(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        loadFonts();
        // Pre-build a Pokemon for each entry once so sprite lookups + display info are
        // available without re-parsing the CSV every frame.
        for (int i = 0; i < STARTERS.size(); i++) {
            roster[i] = getPokemon.findPokemon(STARTERS.get(i), STARTER_LEVEL);
        }
    }

    private void loadFonts() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            titleFont = base.deriveFont(Font.BOLD, 56f);
            nameFont  = base.deriveFont(Font.BOLD, 36f);
            hintFont  = base.deriveFont(Font.BOLD, 22f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Seed edge-detection so a key held when the screen opens doesn't fire instantly.
    public void refreshInput() {
        prevLeft  = keyH.leftPressed;
        prevRight = keyH.rightPressed;
        prevUp    = keyH.upPressed;
        prevDown  = keyH.downPressed;
        prevEnter = keyH.enterPressed;
        cursor = 0;
    }

    public void update() {
        boolean justLeft  = keyH.leftPressed  && !prevLeft;
        boolean justRight = keyH.rightPressed && !prevRight;
        boolean justUp    = keyH.upPressed    && !prevUp;
        boolean justDown  = keyH.downPressed  && !prevDown;
        boolean justEnter = keyH.enterPressed && !prevEnter;
        prevLeft  = keyH.leftPressed;
        prevRight = keyH.rightPressed;
        prevUp    = keyH.upPressed;
        prevDown  = keyH.downPressed;
        prevEnter = keyH.enterPressed;

        int n = roster.length;
        // Left/Right scrolls by one; Up/Down jumps a row (3 starters = one generation)
        // so flipping between gens is fast for keyboard users.
        if (justLeft)  cursor = (cursor + n - 1) % n;
        if (justRight) cursor = (cursor + 1) % n;
        if (justUp)    cursor = (cursor + n - 3) % n;
        if (justDown)  cursor = (cursor + 3) % n;
        if (justEnter) commitStarter();
    }

    private void commitStarter() {
        Pokemon chosen = roster[cursor];
        if (chosen == null) return;
        gp.playerPokemon.clearAll();
        gp.playerPokemon.pokemonEquipped.add(chosen);
        // Hand off to overworld. Music swaps from 10_The Gate to the zone-appropriate
        // overworld track; Player.update will re-pick on later route crossings.
        gp.playMusic(gp.overworldMusicIndex());
        gp.gameState = gp.playState;
    }

    public void draw(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Backdrop
        g2.setPaint(new GradientPaint(0, 0, new Color(8, 16, 28),
                                       0, gp.screenHeight, new Color(20, 38, 60)));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        // Title
        if (titleFont != null) g2.setFont(titleFont);
        g2.setColor(Color.white);
        String title = "Choose your starter!";
        int titleW = g2.getFontMetrics().stringWidth(title);
        g2.drawString(title, (gp.screenWidth - titleW) / 2, 90);

        Pokemon focused = roster[cursor];

        // Big sprite panel
        int spriteW = 360, spriteH = 360;
        int spriteX = (gp.screenWidth - spriteW) / 2;
        int spriteY = 120;
        drawPanel(g2, spriteX - 16, spriteY - 16, spriteW + 32, spriteH + 32, true);
        BufferedImage img = images.getPokemonFront(focused);
        if (img != null) {
            g2.drawImage(img, spriteX, spriteY, spriteW, spriteH, null);
        }

        // Name + level + type chips
        if (nameFont != null) g2.setFont(nameFont);
        g2.setColor(Color.white);
        String label = (focused != null ? focused.name : "?") + "   Lv " + STARTER_LEVEL;
        int labelW = g2.getFontMetrics().stringWidth(label);
        g2.drawString(label, (gp.screenWidth - labelW) / 2, spriteY + spriteH + 70);

        if (focused != null) {
            String types = focused.type1
                + (focused.type2 != null && !focused.type2.equalsIgnoreCase("none")
                    && !focused.type2.equalsIgnoreCase(focused.type1)
                        ? "  /  " + focused.type2 : "");
            if (hintFont != null) g2.setFont(hintFont);
            g2.setColor(new Color(180, 200, 220));
            int tw = g2.getFontMetrics().stringWidth(types);
            g2.drawString(types.toUpperCase(), (gp.screenWidth - tw) / 2, spriteY + spriteH + 96);
        }

        // Hint bar
        if (hintFont != null) g2.setFont(hintFont);
        g2.setColor(new Color(180, 200, 220));
        String hint = "Left/Right scroll   Up/Down jump generation   Enter choose";
        int hintW = g2.getFontMetrics().stringWidth(hint);
        g2.drawString(hint, (gp.screenWidth - hintW) / 2, gp.screenHeight - 24);

        // Position pip strip — one small dot per starter, current one highlighted.
        drawPositionPips(g2, gp.screenHeight - 60);
    }

    private void drawPositionPips(Graphics2D g2, int yCenter) {
        int n = roster.length;
        int dot = 10, gap = 6;
        int totalW = n * dot + (n - 1) * gap;
        int x0 = (gp.screenWidth - totalW) / 2;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (dot + gap);
            boolean here = (i == cursor);
            g2.setColor(here ? new Color(255, 220, 60) : new Color(120, 140, 170, 180));
            g2.fillOval(x, yCenter - dot / 2, dot, dot);
        }
    }

    private void drawPanel(Graphics2D g2, int x, int y, int w, int h, boolean highlight) {
        g2.setPaint(new GradientPaint(0, y, new Color(18, 28, 40, 230),
                                       0, y + h, new Color(8, 14, 22, 230)));
        g2.fillRoundRect(x, y, w, h, 22, 22);
        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(highlight ? 3f : 2f));
        g2.setColor(highlight ? new Color(255, 220, 60) : new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, w, h, 22, 22);
        g2.setStroke(prev);
    }
}
