package Save;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.io.File;

import Main.GamePanel;
import Main.KeyHandler;

// "What's your name?" prompt that runs between TitleScreen's New-World pick and the
// starter selection. Reads typed characters from KeyHandler's free-text buffer; Enter
// commits, Esc bails back to a default. The committed name is stored on gp.playerName
// and persisted by SaveManager.
public class NameEntry {
    public static final int MAX_NAME_LEN = 12;

    private final GamePanel gp;
    private final KeyHandler keyH;
    private Font titleFont, inputFont, hintFont;

    private boolean prevEnter, prevEsc;
    // Frame counter for the blinking caret in the input box.
    private int cursorBlink;

    public NameEntry(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        loadFonts();
    }

    private void loadFonts() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            titleFont = base.deriveFont(Font.BOLD, 56f);
            inputFont = base.deriveFont(Font.BOLD, 44f);
            hintFont  = base.deriveFont(Font.BOLD, 22f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void open() {
        keyH.typedBuffer.setLength(0);
        keyH.captureText = true;
        prevEnter = keyH.enterPressed;
        prevEsc   = keyH.escPressed;
        cursorBlink = 0;
    }

    public void update() {
        // Clamp the buffer to our display limit so the typed-but-not-shown chars don't
        // silently survive into the committed name.
        if (keyH.typedBuffer.length() > MAX_NAME_LEN) {
            keyH.typedBuffer.setLength(MAX_NAME_LEN);
        }
        cursorBlink++;

        boolean justEnter = keyH.enterPressed && !prevEnter;
        boolean justEsc   = keyH.escPressed   && !prevEsc;
        prevEnter = keyH.enterPressed;
        prevEsc   = keyH.escPressed;

        if (justEsc) {
            commit("Player");
            return;
        }
        if (justEnter) {
            String name = keyH.typedBuffer.toString().trim();
            if (name.isEmpty()) name = "Player";
            commit(name);
        }
    }

    private void commit(String name) {
        gp.playerName = name;
        keyH.captureText = false;
        keyH.typedBuffer.setLength(0);
        // Hand off to the starter pick. Music stays on 10_The Gate until the starter
        // is committed (StarterSelection swaps to overworld).
        gp.starterSelection.refreshInput();
        gp.gameState = gp.starterSelectionState;
    }

    public void draw(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, new Color(8, 16, 28),
                                       0, gp.screenHeight, new Color(20, 38, 60)));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        if (titleFont != null) g2.setFont(titleFont);
        g2.setColor(Color.white);
        String title = "What's your name?";
        int titleW = g2.getFontMetrics().stringWidth(title);
        g2.drawString(title, (gp.screenWidth - titleW) / 2, 220);

        // Input box
        int boxW = 520, boxH = 90;
        int boxX = (gp.screenWidth - boxW) / 2;
        int boxY = 280;
        g2.setPaint(new GradientPaint(0, boxY, new Color(18, 28, 40, 230),
                                       0, boxY + boxH, new Color(8, 14, 22, 230)));
        g2.fillRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(255, 220, 60));
        g2.drawRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        g2.setStroke(prev);

        String typed = keyH.typedBuffer.toString();
        boolean showCursor = (cursorBlink / 30) % 2 == 0;
        String display = typed + (showCursor ? "|" : "");
        if (inputFont != null) g2.setFont(inputFont);
        g2.setColor(Color.white);
        int dw = g2.getFontMetrics().stringWidth(display);
        g2.drawString(display, boxX + (boxW - dw) / 2, boxY + 62);

        if (hintFont != null) g2.setFont(hintFont);
        g2.setColor(new Color(180, 200, 220));
        String hint = "Type your name   Backspace delete   Enter confirm";
        int hw = g2.getFontMetrics().stringWidth(hint);
        g2.drawString(hint, (gp.screenWidth - hw) / 2, boxY + boxH + 50);
    }
}
