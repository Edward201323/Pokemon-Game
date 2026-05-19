package BossIntro;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.GetPokemon;
import Pokemon.Pokemon;
import PokemonEncounters.BattleSystem;
import object.OBJ_Boss;

// Overworld pre-fight dialog for the trainer boss. Stays on the world view (darkened),
// shows the ominous "I am you..." lines one per Enter press, then transitions into the
// actual encounter (PokemonTransition → beforePokemonEncounter → pokemonEncounter).
public class BossIntro {
    private static final int MESSAGE_MIN_FRAMES = 15;

    private enum Phase { CLOSED, DIALOG }

    private final GamePanel gp;
    private final KeyHandler keyH;
    private final GetPokemon getPokemon = new GetPokemon();

    private Phase phase = Phase.CLOSED;
    private OBJ_Boss boss;

    private final Deque<String> queue = new ArrayDeque<>();
    private String currentMessage = "";
    private int messageFrame;
    private Runnable afterAll;

    // Enter is the only confirm key — Z is intentionally ignored (matches PokemonCenter).
    private boolean prevEnter;

    private Font bigFont, smallFont;

    public BossIntro(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            bigFont = base.deriveFont(Font.BOLD, 32f);
            smallFont = base.deriveFont(Font.BOLD, 24f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isActive() { return phase != Phase.CLOSED; }

    // Triggered by PlayerObjectInteraction.touchBoss. Music has already been switched to
    // the boss theme by the caller so the dialog plays under it.
    public void open(OBJ_Boss boss) {
        if (phase != Phase.CLOSED) return;
        this.boss = boss;
        gp.gameState = gp.bossIntroState;
        phase = Phase.DIALOG;
        prevEnter = keyH.enterPressed;
        // Overworld lines only — the actual "sent out X!" beat happens *inside* the
        // encounter (with the visible pokeball effect) so this dialog stays pure cutscene.
        String[] lines = {
            ".....",
            "I've been waiting for you.",
            "Don't you recognize me?",
            "I am you. You are me.",
            OBJ_Boss.DISPLAY_NAME + " would like to battle!",
        };
        queueLines(lines, this::startEncounter);
    }

    private void queueLines(String[] lines, Runnable after) {
        queue.clear();
        for (String s : lines) queue.addLast(s);
        afterAll = after;
        advance();
    }

    private void advance() {
        if (queue.isEmpty()) {
            Runnable r = afterAll;
            afterAll = null;
            phase = Phase.CLOSED;
            currentMessage = "";
            if (r != null) r.run();
            return;
        }
        currentMessage = queue.pollFirst();
        messageFrame = 0;
    }

    public void update() {
        boolean justEnter = keyH.enterPressed && !prevEnter;
        prevEnter = keyH.enterPressed;
        if (phase == Phase.CLOSED) return;
        messageFrame++;
        if (messageFrame >= MESSAGE_MIN_FRAMES && justEnter) {
            advance();
        }
    }

    public void draw(Graphics2D g2) {
        if (phase == Phase.CLOSED) return;
        if (gp.gameState != gp.bossIntroState) return;
        // Darken the world so the ominous tone reads — closer to a cutscene feel.
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        BattleSystem.drawDialogPanel(g2, currentMessage, bigFont,
                                      gp.screenWidth, gp.screenHeight);
        if (messageFrame > 15 && smallFont != null) {
            g2.setFont(smallFont);
            g2.setColor(new Color(180, 200, 220));
            g2.drawString("Press Enter", gp.screenWidth - 160,
                          gp.screenHeight - 30);
        }
    }

    // After the dialog drains: build a fresh roster (HP-reset on rematch) and hand off
    // to the encounter pipeline. The encounter itself shows no extra trainer dialog —
    // all the boss flavor is in the lines above.
    private void startEncounter() {
        if (boss == null) { gp.gameState = gp.playState; return; }
        List<Pokemon> roster = new ArrayList<>(boss.team.size());
        for (OBJ_Boss.Member m : boss.team) {
            Pokemon p = getPokemon.findPokemon(m.name, m.level);
            if (p != null) roster.add(p);
        }
        if (roster.isEmpty()) { gp.gameState = gp.playState; return; }
        gp.bossQueue = roster;
        gp.bossIndex = 0;
        gp.wildPokemon = roster.get(0);
        gp.currentBoss = boss;
        gp.isBossBattle = true;
        gp.gameState = gp.pokemonTransition;
        boss = null;
    }
}
