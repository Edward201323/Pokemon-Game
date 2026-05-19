package MoveTutor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.Move;
import Pokemon.Moves;
import Pokemon.Pokemon;

// Move Tutor NPC at tile 153. Flow:
//   INTRO dialog -> PICK_POKEMON (uses party selector) -> PICK_MOVE (own list UI showing
//   getLearnableMoves) -> PICK_SLOT (own list UI showing the pokemon's 4 current moves) ->
//   OUTRO dialog -> back to playState.
// All menus use the same dark-gradient rounded panel as BattleSystem.drawDialogPanel.
public class MoveTutor {
    private static final int MESSAGE_MIN_FRAMES = 70;

    private enum Phase { CLOSED, INTRO, PICK_POKEMON, PICK_MOVE, PICK_SLOT, OUTRO }

    private final GamePanel gp;
    private final KeyHandler keyH;

    private Phase phase = Phase.CLOSED;

    // Message queue (single-line dialogs, advance with Enter/Z).
    private final Deque<String> queue = new ArrayDeque<>();
    private String currentMessage = "";
    private int messageFrame = 0;
    private Runnable afterAllMessages;
    private boolean autoAdvance;

    // Selection state.
    private int selectedPokemonIndex = -1;
    private List<Move> learnableCache;
    private int moveCursor;
    private int slotCursor;
    private Move selectedMove;

    // Edge detection. Enter is the only confirm; Z is intentionally ignored.
    private boolean prevEsc, prevP, prevUp, prevDown, prevEnter;
    private boolean justConfirm, justEsc, justP, justUp, justDown;

    private Font bigFont, smallFont, tinyFont;

    public MoveTutor(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        loadFonts();
    }

    private void loadFonts() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            bigFont = base.deriveFont(Font.BOLD, 32f);
            smallFont = base.deriveFont(Font.BOLD, 24f);
            tinyFont = base.deriveFont(Font.BOLD, 20f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isActive() { return phase != Phase.CLOSED; }

    // Triggered by Player.update() when entering a tile adjacent to tile 153.
    public void open() {
        if (phase != Phase.CLOSED) return;
        gp.gameState = gp.moveTutorState;
        phase = Phase.INTRO;
        selectedPokemonIndex = -1;
        selectedMove = null;
        learnableCache = null;
        moveCursor = 0;
        slotCursor = 0;
        refreshInput();
        queueLines(new String[] {
            "Hello " + gp.playerName + ", I'm a Move Tutor.",
            "I can teach your Pokemon new moves.",
            "Choose a Pokemon.",
        }, this::enterPickPokemon);
        autoAdvance = false;
    }

    // External hook so the party selector (which freezes our update) can re-sync our
    // edge-detection state when control returns.
    public void refreshInput() {
        prevEnter = keyH.enterPressed;
        prevEsc = keyH.escPressed;
        prevP = keyH.pPressed;
        prevUp = keyH.upPressed;
        prevDown = keyH.downPressed;
    }

    public void update() {
        sampleEdges();
        if (phase == Phase.CLOSED) return;
        if (phase == Phase.INTRO || phase == Phase.OUTRO) {
            messageFrame++;
            boolean ready = autoAdvance
                ? messageFrame >= MESSAGE_MIN_FRAMES
                : (messageFrame >= 15 && justConfirm);
            if (ready) advanceMessage();
            return;
        }
        if (phase == Phase.PICK_MOVE) {
            handleMoveListInput();
            return;
        }
        if (phase == Phase.PICK_SLOT) {
            handleSlotListInput();
            return;
        }
        // PICK_POKEMON is fully owned by OpenPlayerInventory's selectionMode while open,
        // so we don't poll input here — the callback fires on confirm/cancel.
    }

    public void draw(Graphics2D g2) {
        if (phase == Phase.CLOSED) return;
        // Don't draw over the party selector when it's layered on top of us.
        if (gp.gameState != gp.moveTutorState) return;
        if (phase == Phase.PICK_MOVE) {
            drawMoveList(g2);
            return;
        }
        if (phase == Phase.PICK_SLOT) {
            drawSlotList(g2);
            return;
        }
        drawDialog(g2);
    }

    // ----- input -----

    private void sampleEdges() {
        // Enter is the *only* confirm in the tutor — Z is reserved so it can't be
        // accidentally pressed instead of Enter when navigating menus.
        justConfirm = keyH.enterPressed && !prevEnter;
        justEsc = keyH.escPressed && !prevEsc;
        justP = keyH.pPressed && !prevP;
        justUp = keyH.upPressed && !prevUp;
        justDown = keyH.downPressed && !prevDown;
        prevEnter = keyH.enterPressed;
        prevEsc = keyH.escPressed;
        prevP = keyH.pPressed;
        prevUp = keyH.upPressed;
        prevDown = keyH.downPressed;
    }

    private void handleMoveListInput() {
        if (learnableCache == null || learnableCache.isEmpty()) {
            if (justConfirm || justEsc || justP) close();
            return;
        }
        int n = learnableCache.size();
        if (justUp)   moveCursor = (moveCursor + n - 1) % n;
        if (justDown) moveCursor = (moveCursor + 1) % n;
        if (justEsc) {
            // Back to the party selector to pick a different pokemon.
            learnableCache = null;
            enterPickPokemon();
            return;
        }
        if (justConfirm) {
            selectedMove = learnableCache.get(moveCursor);
            Pokemon selected = selectedPokemon();
            // If the pokemon has a free move slot (under 4 known moves), skip the
            // forget-which-move picker entirely and just append the new move.
            if (selected != null && (selected.moves == null || selected.moves.size() < 4)) {
                if (selected.moves == null) selected.moves = new java.util.ArrayList<>();
                selected.moves.add(selectedMove);
                queueLines(new String[] { selected.name + " learned " + selectedMove.name + "!",
                                          "Come back any time!" }, this::close);
                autoAdvance = false;
                phase = Phase.OUTRO;
                return;
            }
            slotCursor = 0;
            phase = Phase.PICK_SLOT;
        }
    }

    private void handleSlotListInput() {
        Pokemon selected = selectedPokemon();
        if (selected == null || selected.moves == null || selected.moves.isEmpty()) {
            close();
            return;
        }
        int n = selected.moves.size();
        if (justUp)   slotCursor = (slotCursor + n - 1) % n;
        if (justDown) slotCursor = (slotCursor + 1) % n;
        if (justEsc) {
            // Back to the move list.
            phase = Phase.PICK_MOVE;
            return;
        }
        if (justConfirm && selectedMove != null) {
            Move oldMove = selected.moves.get(slotCursor);
            selected.moves.set(slotCursor, selectedMove);
            String learned = (oldMove != null ? selected.name + " forgot " + oldMove.name + " and "
                                              : selected.name + " ")
                             + "learned " + selectedMove.name + "!";
            queueLines(new String[] { learned, "Come back any time!" }, this::close);
            autoAdvance = false;
            phase = Phase.OUTRO;
        }
    }

    // ----- transitions -----

    private void enterPickPokemon() {
        phase = Phase.PICK_POKEMON;
        // Hand off to the existing party UI in selection mode. The callbacks re-enter
        // PICK_MOVE on confirm or close the tutor on cancel.
        gp.openPlayerInventory.openInSelectionMode(
            "Choose a Pokemon to teach.",
            true,
            slot -> true,
            idx -> {
                selectedPokemonIndex = idx;
                refreshInput();
                openMoveList();
            },
            () -> { refreshInput(); close(); }
        );
    }

    private void openMoveList() {
        Pokemon p = selectedPokemon();
        if (p == null) { close(); return; }
        learnableCache = Moves.getLearnableMoves(p);
        moveCursor = 0;
        phase = Phase.PICK_MOVE;
        if (learnableCache.isEmpty()) {
            // Brief dialog and bounce back to the pokemon picker.
            queueLines(new String[] { p.name + " has nothing new to learn." },
                       this::enterPickPokemon);
            autoAdvance = true;
            phase = Phase.OUTRO;
        }
    }

    private Pokemon selectedPokemon() {
        if (selectedPokemonIndex < 0) return null;
        java.util.List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        if (selectedPokemonIndex >= party.size()) return null;
        return party.get(selectedPokemonIndex);
    }

    private void close() {
        phase = Phase.CLOSED;
        queue.clear();
        currentMessage = "";
        afterAllMessages = null;
        learnableCache = null;
        selectedMove = null;
        selectedPokemonIndex = -1;
        gp.gameState = gp.playState;
    }

    // ----- message queue -----

    private void queueLines(String[] lines, Runnable after) {
        queue.clear();
        for (String s : lines) queue.addLast(s);
        afterAllMessages = after;
        advanceMessage();
    }

    private void advanceMessage() {
        if (queue.isEmpty()) {
            Runnable next = afterAllMessages;
            afterAllMessages = null;
            if (next != null) next.run();
            return;
        }
        currentMessage = queue.pollFirst();
        messageFrame = 0;
    }

    // ----- drawing -----

    private void drawDialog(Graphics2D g2) {
        if (currentMessage.isEmpty()) return;
        int boxH = 140;
        int boxY = gp.screenHeight - boxH - 12;
        int x = 20;
        int w = gp.screenWidth - 40;
        drawPanel(g2, x, boxY, w, boxH);
        if (bigFont != null) g2.setFont(bigFont);
        g2.setColor(new Color(245, 250, 255));
        g2.drawString(currentMessage, x + 28, boxY + 60);
        if (!autoAdvance && messageFrame > 15 && smallFont != null) {
            g2.setFont(smallFont);
            g2.setColor(new Color(180, 200, 220));
            g2.drawString("Press Enter", x + w - 150, boxY + boxH - 18);
        }
    }

    private void drawMoveList(Graphics2D g2) {
        Pokemon p = selectedPokemon();
        int panelW = gp.screenWidth - 80;
        int panelX = 40;
        int panelY = 40;
        int panelH = gp.screenHeight - 80;
        drawPanel(g2, panelX, panelY, panelW, panelH);

        if (bigFont != null) g2.setFont(bigFont);
        g2.setColor(Color.white);
        String title = (p != null ? p.name : "Pokemon") + "  -  Choose a move to learn";
        g2.drawString(title, panelX + 24, panelY + 38);
        if (smallFont != null) g2.setFont(smallFont);
        g2.setColor(new Color(180, 200, 220));
        g2.drawString("Up/Down navigate   Enter select   ESC back", panelX + 24, panelY + 64);

        if (learnableCache == null || learnableCache.isEmpty()) {
            g2.setColor(Color.white);
            if (bigFont != null) g2.setFont(bigFont);
            g2.drawString("No new moves available.", panelX + 24, panelY + 120);
            return;
        }

        int listX = panelX + 24;
        int listY = panelY + 100;
        int rowH = 38;
        int hlPadX = 12;
        int hlTopOffset = 26;
        int hlBottomOffset = 10;
        int visible = Math.min(learnableCache.size(), (panelH - 120) / rowH);
        int start = Math.max(0, Math.min(learnableCache.size() - visible, moveCursor - visible / 2));
        for (int i = 0; i < visible; i++) {
            int idx = start + i;
            if (idx >= learnableCache.size()) break;
            Move m = learnableCache.get(idx);
            int rowY = listY + i * rowH;
            boolean cursorHere = idx == moveCursor;
            if (cursorHere) {
                int hlY = rowY - hlTopOffset;
                int hlH = hlTopOffset + hlBottomOffset;
                g2.setColor(new Color(255, 220, 60, 55));
                g2.fillRoundRect(listX - hlPadX, hlY, panelW - 48 + hlPadX, hlH, 12, 12);
                Stroke prev = g2.getStroke();
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(255, 220, 60));
                g2.drawRoundRect(listX - hlPadX, hlY, panelW - 48 + hlPadX, hlH, 12, 12);
                g2.setStroke(prev);
            }
            if (smallFont != null) g2.setFont(smallFont);
            g2.setColor(Color.white);
            g2.drawString(m.name, listX, rowY);
            if (tinyFont != null) g2.setFont(tinyFont);
            g2.setColor(new Color(190, 210, 230));
            String meta = upper(m.type) + "   " + (m.physical ? "Phys" : "Spec")
                        + "   Pwr " + m.basePower
                        + "   Acc " + (m.accuracy < 0 ? "—" : m.accuracy)
                        + "   Lv " + m.minLevel;
            g2.drawString(meta, listX + 220, rowY);
        }
    }

    private void drawSlotList(Graphics2D g2) {
        Pokemon p = selectedPokemon();
        int panelW = gp.screenWidth - 80;
        int panelX = 40;
        int panelY = 40;
        int panelH = gp.screenHeight - 80;
        drawPanel(g2, panelX, panelY, panelW, panelH);

        if (bigFont != null) g2.setFont(bigFont);
        g2.setColor(Color.white);
        String title = (p != null ? p.name : "Pokemon")
                + "  -  Which move to replace with "
                + (selectedMove != null ? selectedMove.name : "?") + "?";
        g2.drawString(title, panelX + 24, panelY + 38);
        if (smallFont != null) g2.setFont(smallFont);
        g2.setColor(new Color(180, 200, 220));
        g2.drawString("Up/Down navigate   Enter confirm   ESC back", panelX + 24, panelY + 64);

        if (p == null || p.moves == null || p.moves.isEmpty()) {
            g2.setColor(Color.white);
            if (bigFont != null) g2.setFont(bigFont);
            g2.drawString("This Pokemon has no moves to replace.", panelX + 24, panelY + 120);
            return;
        }

        int listX = panelX + 24;
        int listY = panelY + 116;
        int rowH = 64;            // taller rows so name + meta both fit cleanly
        int hlPadX = 12;
        int hlTopOffset = 32;     // how far above the name baseline the highlight starts
        int hlBottomOffset = 28;  // how far below the name baseline the highlight ends
        for (int i = 0; i < p.moves.size(); i++) {
            Move m = p.moves.get(i);
            int rowY = listY + i * rowH;
            boolean cursorHere = i == slotCursor;
            if (cursorHere) {
                int hlY = rowY - hlTopOffset;
                int hlH = hlTopOffset + hlBottomOffset;
                g2.setColor(new Color(255, 220, 60, 55));
                g2.fillRoundRect(listX - hlPadX, hlY, panelW - 48 + hlPadX, hlH, 14, 14);
                Stroke prev = g2.getStroke();
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(255, 220, 60));
                g2.drawRoundRect(listX - hlPadX, hlY, panelW - 48 + hlPadX, hlH, 14, 14);
                g2.setStroke(prev);
            }
            if (bigFont != null) g2.setFont(bigFont);
            g2.setColor(Color.white);
            String label = "Slot " + (i + 1) + ":  " + (m != null ? m.name : "—");
            g2.drawString(label, listX, rowY);
            if (m != null && tinyFont != null) {
                g2.setFont(tinyFont);
                g2.setColor(new Color(190, 210, 230));
                String meta = upper(m.type) + "   " + (m.physical ? "Phys" : "Spec")
                            + "   Pwr " + m.basePower
                            + "   Acc " + (m.accuracy < 0 ? "—" : m.accuracy);
                g2.drawString(meta, listX + 28, rowY + 22);
            }
        }
    }

    private void drawPanel(Graphics2D g2, int x, int y, int w, int h) {
        g2.setPaint(new GradientPaint(0, y, new Color(18, 28, 40, 235),
                                      0, y + h, new Color(8, 14, 22, 235)));
        g2.fillRoundRect(x, y, w, h, 22, 22);
        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, w, h, 22, 22);
        g2.setColor(new Color(140, 180, 220, 70));
        g2.drawRoundRect(x + 3, y + 3, w - 6, h - 6, 18, 18);
        g2.setStroke(prev);
    }

    private static String upper(String s) { return s == null ? "" : s.toUpperCase(); }
}
