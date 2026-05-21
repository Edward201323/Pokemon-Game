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

// Boot-time slot picker. SLOT_COUNT + 0 options laid out vertically: each existing slot
// loads when chosen, each empty slot starts a fresh new world saved into that slot. The
// game launches into this screen and stays here until the player picks one.
public class TitleScreen {
    private final GamePanel gp;
    private final KeyHandler keyH;
    private Font titleFont, rowFont, hintFont;

    // Selection cursor: 0..SLOT_COUNT-1 over slot rows. No "exit" option — the player
    // commits to one of the slots to start playing.
    private int cursor = 0;

    // When true, the focused slot is showing a delete-confirmation prompt. Enter
    // confirms, Esc cancels. Cleared on any cursor move so changing slots aborts.
    private boolean confirmingDelete = false;

    // prevDeleteCombo tracks the edge of Shift+D so a held combo only deletes once.
    private boolean prevUp, prevDown, prevEnter, prevDeleteCombo, prevEsc;

    public TitleScreen(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        loadFonts();
        refreshInput();
    }

    private void loadFonts() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            titleFont = base.deriveFont(Font.BOLD, 72f);
            rowFont   = base.deriveFont(Font.BOLD, 32f);
            hintFont  = base.deriveFont(Font.BOLD, 22f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Seed edge-detection so a key already held when the screen opens doesn't fire.
    public void refreshInput() {
        prevUp          = keyH.upPressed;
        prevDown        = keyH.downPressed;
        prevEnter       = keyH.enterPressed;
        prevDeleteCombo = keyH.shiftPressed && keyH.dPressed;
        prevEsc         = keyH.escPressed;
    }

    public void update() {
        // Shift+D = delete the focused slot. Edge-detected as a combo so the prompt only
        // fires once per press and Shift-alone or D-alone do nothing.
        boolean deleteCombo = keyH.shiftPressed && keyH.dPressed;
        boolean justUp     = keyH.upPressed    && !prevUp;
        boolean justDown   = keyH.downPressed  && !prevDown;
        boolean justEnter  = keyH.enterPressed && !prevEnter;
        boolean justDelete = deleteCombo       && !prevDeleteCombo;
        boolean justEsc    = keyH.escPressed   && !prevEsc;
        prevUp          = keyH.upPressed;
        prevDown        = keyH.downPressed;
        prevEnter       = keyH.enterPressed;
        prevDeleteCombo = deleteCombo;
        prevEsc         = keyH.escPressed;

        int n = SaveManager.SLOT_COUNT;
        int slot = cursor + 1;

        // Delete-confirmation prompt takes over Enter/Esc; navigation cancels it.
        if (confirmingDelete) {
            if (justEnter) {
                SaveManager.delete(slot);
                confirmingDelete = false;
            } else if (justEsc || justUp || justDown) {
                confirmingDelete = false;
            }
            // Don't fall through to navigation on the same frame the cursor moved.
            return;
        }

        if (justUp)   cursor = (cursor + n - 1) % n;
        if (justDown) cursor = (cursor + 1) % n;
        if (justDelete && SaveManager.slotExists(slot)) {
            confirmingDelete = true;
        } else if (justEnter) {
            commitSlot(slot); // slot numbers are 1-indexed on disk
        }
    }

    private void commitSlot(int slot) {
        gp.currentSaveSlot = slot;
        if (SaveManager.slotExists(slot)) {
            SaveManager.load(slot, gp);
            // Loaded saves already have a party — swap straight to overworld music.
            gp.playMusic(gp.overworldMusicIndex());
            gp.gameState = gp.playState;
        } else {
            // New world: prompt for a name first, then hand off to the starter pick.
            // NameEntry stores gp.playerName and transitions to starterSelectionState
            // on confirm; StarterSelection then transitions to playState.
            gp.nameEntry.open();
            gp.playMusic(10); // 10_The Gate.wav — plays through both NameEntry + StarterSelection
            gp.gameState = gp.nameEntryState;
        }
    }

    public void draw(Graphics2D g2) {
        // Backdrop: vertical dark gradient over the full panel.
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, new Color(8, 16, 28),
                                       0, gp.screenHeight, new Color(20, 38, 60)));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        // Title
        if (titleFont != null) g2.setFont(titleFont);
        g2.setColor(Color.white);
        String title = "POKEMON BRONZE BRICK";
        int titleW = g2.getFontMetrics().stringWidth(title);
        g2.drawString(title, (gp.screenWidth - titleW) / 2, 130);

        if (hintFont != null) g2.setFont(hintFont);
        g2.setColor(new Color(180, 200, 220));
        String hint = confirmingDelete
            ? "Enter confirm delete   Esc cancel"
            : "Up/Down navigate   Enter select   Shift+D delete";
        int hintW = g2.getFontMetrics().stringWidth(hint);
        g2.drawString(hint, (gp.screenWidth - hintW) / 2, 170);

        // Slot rows
        int rowH = 90;
        int totalH = SaveManager.SLOT_COUNT * rowH;
        int startY = (gp.screenHeight - totalH) / 2 + 40;
        int rowW = gp.screenWidth - 160;
        int rowX = 80;

        for (int i = 0; i < SaveManager.SLOT_COUNT; i++) {
            int slot = i + 1;
            int y = startY + i * rowH;
            boolean focused = (cursor == i);
            drawSlotRow(g2, rowX, y, rowW, rowH - 16, slot, focused);
        }
    }

    private void drawSlotRow(Graphics2D g2, int x, int y, int w, int h, int slot, boolean focused) {
        // Panel
        g2.setPaint(new GradientPaint(0, y, new Color(18, 28, 40, 230),
                                       0, y + h, new Color(8, 14, 22, 230)));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(focused ? 3f : 2f));
        g2.setColor(focused ? new Color(255, 220, 60) : new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, w, h, 18, 18);
        g2.setStroke(prev);

        // Text — minimal: just slot number and whether it's been played. When the focused
        // slot is in delete-confirm mode, the right-side label becomes "Delete?" in red.
        if (rowFont != null) g2.setFont(rowFont);
        SaveManager.SlotInfo info = SaveManager.readSlotInfo(slot);
        boolean confirmHere = focused && confirmingDelete;
        String head = "Slot " + slot;
        String body;
        if (confirmHere) {
            body = "Delete?";
        } else if (info == null) {
            body = "Empty";
        } else if (info.playerName != null && !info.playerName.isEmpty()) {
            body = info.playerName;
        } else {
            body = "Played";
        }
        g2.setColor(Color.white);
        g2.drawString(head, x + 24, y + 40);
        g2.setColor(confirmHere ? new Color(255, 110, 110) : Color.white);
        g2.drawString(body, x + w - g2.getFontMetrics().stringWidth(body) - 24, y + 40);
    }
}
