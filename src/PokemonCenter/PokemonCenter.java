package PokemonCenter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.GetPokemonImages;
import Pokemon.Pokemon;

// Nurse Joy's Pokemon Center. Owns: intro dialog -> menu (Heal / PC / Exit) -> heal animation
// (HP bars refill smoothly with sound + pulse) -> outro dialog -> back to playState.
// Also hosts the minimalist PC viewer (Up/Down to scroll, Z/Esc to leave).
public class PokemonCenter {
    private static final int MESSAGE_MIN_FRAMES = 70;
    private static final int HEAL_BAR_FRAMES = 90;           // ~1.5s per refill at 60 FPS
    private static final int HEAL_BAR_GAP_FRAMES = 8;        // small beat between pokemon
    private static final int CHIME_INTERVAL_FRAMES = 18;     // soft pulse cadence

    private enum Phase { CLOSED, INTRO, MENU, HEAL_PRE, HEAL_ANIM, HEAL_OUTRO, PC_VIEW }
    private static final String[] MENU_LABELS = { "HEAL", "PC", "EXIT" };

    private final GamePanel gp;
    private final KeyHandler keyH;
    private final GetPokemonImages pokemonImages = new GetPokemonImages();

    private Phase phase = Phase.CLOSED;

    // Message queue (mirrors BattleSystem's style — single line, Z to advance, optional auto-pace).
    private final Deque<String> queue = new ArrayDeque<>();
    private String currentMessage = "";
    private int messageFrame = 0;
    private Runnable afterAllMessages;
    private boolean autoAdvance; // true = run to end on a timer; false = Z to advance

    // Menu state.
    private int menuIndex;

    // Heal animation state.
    private int healIndex;       // which party slot is currently filling
    private int healSubFrame;    // 0..HEAL_BAR_FRAMES
    private double healStartHp;  // currentHP captured when this pokemon starts filling
    private int chimeFrame;

    // PC viewer state.
    private int pcIndex;
    private List<Pokemon> pcCache;

    // Edge detection so held keys don't auto-fire.
    private boolean prevZ, prevX, prevUp, prevDown, prevP;
    private boolean justZ, justX, justUp, justDown, justP;

    private Font bigFont, smallFont;

    public PokemonCenter(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        loadFonts();
    }

    private void loadFonts() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            bigFont = base.deriveFont(Font.BOLD, 32f);
            smallFont = base.deriveFont(Font.BOLD, 24f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isActive() {
        return phase != Phase.CLOSED;
    }

    // External entry: stepping onto a tile adjacent to tile 193 opens this.
    public void open() {
        if (phase != Phase.CLOSED) return;
        gp.gameState = gp.pokemonCenterState;
        phase = Phase.INTRO;
        // Seed prev* with current state so a key already held when opening must be released first.
        prevZ = keyH.zPressed;
        prevX = keyH.xPressed;
        prevP = keyH.pPressed;
        prevUp = keyH.upPressed;
        prevDown = keyH.downPressed;
        menuIndex = 0;
        queueLines(new String[] {
            "Hello, and welcome to the Pokemon Center.",
            "What would you like to do?",
        }, () -> phase = Phase.MENU);
        autoAdvance = false;
    }

    public void update() {
        sampleEdges();
        if (phase == Phase.CLOSED) return;

        // INTRO / HEAL_PRE / HEAL_OUTRO all use the message queue.
        if (phase == Phase.INTRO || phase == Phase.HEAL_PRE || phase == Phase.HEAL_OUTRO) {
            messageFrame++;
            boolean ready = autoAdvance
                ? messageFrame >= MESSAGE_MIN_FRAMES
                : (messageFrame >= 15 && justZ);
            if (ready) advanceMessage();
            return;
        }
        if (phase == Phase.MENU) {
            handleMenuInput();
            return;
        }
        if (phase == Phase.HEAL_ANIM) {
            tickHealAnimation();
            return;
        }
        if (phase == Phase.PC_VIEW) {
            handlePcInput();
            return;
        }
    }

    public void draw(Graphics2D g2) {
        if (phase == Phase.CLOSED) return;
        if (phase == Phase.PC_VIEW) {
            drawPcView(g2);
            return;
        }
        // Heal animation pulses the screen white-ish in time with the chime.
        if (phase == Phase.HEAL_ANIM) {
            double t = chimeFrame / (double) CHIME_INTERVAL_FRAMES;
            int alpha = (int) (Math.max(0, Math.sin(t * Math.PI)) * 55);
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
            drawHealParty(g2);
        }
        drawDialog(g2);
        if (phase == Phase.MENU) drawMenu(g2);
    }

    // ----- input helpers -----

    private void sampleEdges() {
        justZ = keyH.zPressed && !prevZ;
        justX = keyH.xPressed && !prevX;
        justP = keyH.pPressed && !prevP;
        justUp = keyH.upPressed && !prevUp;
        justDown = keyH.downPressed && !prevDown;
        prevZ = keyH.zPressed;
        prevX = keyH.xPressed;
        prevP = keyH.pPressed;
        prevUp = keyH.upPressed;
        prevDown = keyH.downPressed;
    }

    private void handleMenuInput() {
        if (justUp)   menuIndex = (menuIndex + MENU_LABELS.length - 1) % MENU_LABELS.length;
        if (justDown) menuIndex = (menuIndex + 1) % MENU_LABELS.length;
        if (justX) { close(); return; }
        if (!justZ) return;
        switch (menuIndex) {
            case 0: startHeal(); break;
            case 1: openPc(); break;
            case 2: close(); break;
        }
    }

    private void handlePcInput() {
        if (pcCache == null || pcCache.isEmpty()) {
            if (justZ || justX || justP) close();
            return;
        }
        if (justUp)   pcIndex = (pcIndex + pcCache.size() - 1) % pcCache.size();
        if (justDown) pcIndex = (pcIndex + 1) % pcCache.size();
        if (justX || justP) close();
        else if (justZ) close();
    }

    private void close() {
        phase = Phase.CLOSED;
        queue.clear();
        currentMessage = "";
        afterAllMessages = null;
        pcCache = null;
        gp.gameState = gp.playState;
    }

    // ----- heal -----

    private void startHeal() {
        // Lead-in dialog auto-paces; the actual refill runs in HEAL_ANIM.
        queueLines(new String[] { "Okay, I'll take your Pokemon for a few seconds." },
                   this::beginHealAnim);
        autoAdvance = true;
        phase = Phase.HEAL_PRE;
    }

    private void beginHealAnim() {
        phase = Phase.HEAL_ANIM;
        healIndex = 0;
        healSubFrame = 0;
        chimeFrame = 0;
        currentMessage = "Healing your Pokemon...";
        seedHealForCurrent();
    }

    private void seedHealForCurrent() {
        List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        if (party.isEmpty() || healIndex >= party.size()) return;
        Pokemon p = party.get(healIndex);
        healStartHp = (p == null) ? 0 : p.currentHP;
        chimeFrame = 0;
    }

    private void tickHealAnimation() {
        List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        if (party.isEmpty()) { finishHeal(); return; }

        Pokemon p = party.get(healIndex);
        if (p == null) { healIndex++; healSubFrame = 0; advanceHealIfDone(party); return; }

        // Ease HP up linearly over HEAL_BAR_FRAMES.
        healSubFrame++;
        double t = Math.min(1.0, healSubFrame / (double) HEAL_BAR_FRAMES);
        p.currentHP = (int) Math.round(healStartHp + (p.maxHP - healStartHp) * t);

        chimeFrame++;
        if (chimeFrame >= CHIME_INTERVAL_FRAMES) {
            chimeFrame = 0;
            // Visual pulse only; no SE here (the pokeball-open clip felt wrong for healing).
        }

        if (healSubFrame >= HEAL_BAR_FRAMES + HEAL_BAR_GAP_FRAMES) {
            p.currentHP = p.maxHP; // snap in case of rounding
            healIndex++;
            healSubFrame = 0;
            advanceHealIfDone(party);
        }
    }

    private void advanceHealIfDone(List<Pokemon> party) {
        if (healIndex >= party.size()) {
            finishHeal();
        } else {
            seedHealForCurrent();
        }
    }

    private void finishHeal() {
        gp.playerPokemon.healAll();
        queueLines(new String[] {
            "Thank you for waiting.",
            "Your Pokemon are fully healed.",
            "We hope to see you again!",
        }, this::close);
        autoAdvance = false;
        phase = Phase.HEAL_OUTRO;
    }

    // ----- PC -----

    private void openPc() {
        pcCache = gp.playerPokemon.allCaughtPokemon();
        pcIndex = 0;
        currentMessage = "";
        phase = Phase.PC_VIEW;
        gp.gameState = gp.pcViewState;
    }

    // ----- queue helpers -----

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
        int boxH = 130;
        int boxY = gp.screenHeight - boxH - 12;
        // Drop-shadow box (rounded translucent rectangle so it sits over the world cleanly).
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRoundRect(20, boxY, gp.screenWidth - 40, boxH, 18, 18);
        g2.setColor(new Color(245, 245, 245));
        g2.drawRoundRect(20, boxY, gp.screenWidth - 40, boxH, 18, 18);

        if (bigFont != null) g2.setFont(bigFont);
        g2.setColor(Color.white);
        g2.drawString(currentMessage, 44, boxY + 56);

        if (!autoAdvance && messageFrame > 15 && smallFont != null) {
            g2.setFont(smallFont);
            g2.setColor(new Color(220, 220, 220));
            g2.drawString("Press Z", gp.screenWidth - 130, boxY + boxH - 22);
        }
    }

    private void drawMenu(Graphics2D g2) {
        int w = 220, h = 180;
        int x = gp.screenWidth - w - 32;
        int y = gp.screenHeight - 350;
        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        g2.setColor(Color.white);
        g2.drawRoundRect(x, y, w, h, 18, 18);

        if (bigFont != null) g2.setFont(bigFont);
        for (int i = 0; i < MENU_LABELS.length; i++) {
            boolean selected = (i == menuIndex);
            g2.setColor(selected ? Color.white : new Color(170, 170, 170));
            String label = (selected ? "> " : "  ") + MENU_LABELS[i];
            g2.drawString(label, x + 24, y + 48 + i * 44);
        }
    }

    // Compact party display during the heal — sprite + name + filling HP bar per slot.
    private void drawHealParty(Graphics2D g2) {
        List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        if (party.isEmpty()) return;
        int rowH = 56;
        int totalH = rowH * party.size() + 24;
        int boxW = 540;
        int x = (gp.screenWidth - boxW) / 2;
        int y = 60;

        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRoundRect(x, y, boxW, totalH, 18, 18);
        g2.setColor(Color.white);
        g2.drawRoundRect(x, y, boxW, totalH, 18, 18);

        if (smallFont != null) g2.setFont(smallFont);
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            if (p == null) continue;
            int rowY = y + 12 + i * rowH;
            BufferedImage sprite = pokemonImages.getPokemonFront(p);
            if (sprite != null) {
                g2.drawImage(sprite, x + 12, rowY, 44, 44, null);
            }
            g2.setColor(Color.white);
            g2.drawString(p.name + "  Lv " + p.level, x + 64, rowY + 22);

            int barX = x + 64;
            int barY = rowY + 28;
            int barW = boxW - 80;
            int barH = 10;
            double ratio = p.maxHP > 0 ? Math.min(1.0, p.currentHP / (double) p.maxHP) : 0;
            int filled = (int) Math.round(barW * ratio);
            g2.setColor(new Color(30, 30, 30));
            g2.fillRect(barX, barY, barW, barH);
            g2.setColor(new Color(80, 200, 80));
            g2.fillRect(barX, barY, filled, barH);
        }
    }

    // Minimalist dark PC viewer: list left, sprite right, navigated with arrows.
    private void drawPcView(Graphics2D g2) {
        g2.setColor(Color.black);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        if (pcCache == null || pcCache.isEmpty()) {
            if (bigFont != null) g2.setFont(bigFont);
            g2.setColor(Color.white);
            g2.drawString("Your PC is empty.", gp.screenWidth / 2 - 120, gp.screenHeight / 2);
            if (smallFont != null) g2.setFont(smallFont);
            g2.setColor(new Color(160, 160, 160));
            g2.drawString("Z or Esc to return", gp.screenWidth / 2 - 100, gp.screenHeight / 2 + 40);
            return;
        }

        int leftPanelW = gp.screenWidth * 2 / 5;
        // Left: scrolling list with the selected name centered, dimmer names above/below.
        int centerY = gp.screenHeight / 2;
        int spacing = 44;
        int n = pcCache.size();
        for (int offset = -3; offset <= 3; offset++) {
            int idx = pcIndex + offset;
            if (idx < 0 || idx >= n) continue;
            Pokemon p = pcCache.get(idx);
            if (p == null) continue;
            int y = centerY + offset * spacing;
            int distance = Math.abs(offset);
            int alpha = Math.max(60, 255 - distance * 70);
            g2.setColor(new Color(255, 255, 255, alpha));
            Font f = (offset == 0 && bigFont != null) ? bigFont.deriveFont(36f)
                                                      : (smallFont != null ? smallFont : bigFont);
            if (f != null) g2.setFont(f);
            String label = (offset == 0 ? "> " : "  ") + p.name.toUpperCase();
            g2.drawString(label, 48, y);
        }
        // Position indicator
        if (smallFont != null) g2.setFont(smallFont);
        g2.setColor(new Color(160, 160, 160));
        g2.drawString((pcIndex + 1) + " / " + n, 48, gp.screenHeight - 32);
        g2.drawString("Up/Down  -  Z or Esc to exit", 48, gp.screenHeight - 60);

        // Right: large sprite of selected pokemon, centered in the right panel.
        Pokemon sel = pcCache.get(pcIndex);
        BufferedImage sprite = pokemonImages.getPokemonFront(sel);
        if (sprite != null) {
            int rightX = leftPanelW;
            int rightW = gp.screenWidth - leftPanelW;
            int size = Math.min(rightW - 60, gp.screenHeight - 120);
            int sx = rightX + (rightW - size) / 2;
            int sy = (gp.screenHeight - size) / 2;
            // Subtle outline glow
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillRoundRect(sx - 10, sy - 10, size + 20, size + 20, 24, 24);
            g2.drawImage(sprite, sx, sy, size, size, null);
        }
        if (bigFont != null) g2.setFont(bigFont);
        g2.setColor(Color.white);
        String headline = sel.name + "   Lv " + sel.level;
        int textW = g2.getFontMetrics().stringWidth(headline);
        g2.drawString(headline, leftPanelW + (gp.screenWidth - leftPanelW - textW) / 2, 56);
    }
}
