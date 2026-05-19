package PokemonCenter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.io.File;

import Main.GamePanel;

// Party wipe / blackout: when every party pokemon faints we fade to black, show the
// classic "blacked out" dialog, then teleport the player adjacent to the Pokemon Center
// tile and heal everyone before fading back in.
public class Blackout {
    private static final int FADE_OUT_FRAMES = 50;
    private static final int LINE_HOLD_FRAMES = 110;
    private static final int FADE_IN_FRAMES = 50;
    // Tile 193 (the Pokemon Center) is at col=20, row=67 in Route1.txt. Respawn diagonally
    // (one south + one east) so the player isn't on an orthogonally adjacent tile — otherwise
    // the heal menu would immediately re-trigger from the fresh adjacency.
    private static final int RESPAWN_COL = 21;
    private static final int RESPAWN_ROW = 68;
    private static final String[] DIALOG = {
        "PLAYER is out of usable Pokemon!",
        "PLAYER blacked out!",
    };

    private enum Phase { CLOSED, FADE_OUT, DIALOG_PHASE, FADE_IN }

    private final GamePanel gp;
    private Phase phase = Phase.CLOSED;
    private int frame;
    private int dialogIndex;
    private Font font;

    public Blackout(GamePanel gp) {
        this.gp = gp;
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            font = base.deriveFont(Font.BOLD, 34f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isActive() { return phase != Phase.CLOSED; }

    public void trigger() {
        if (phase != Phase.CLOSED) return;
        phase = Phase.FADE_OUT;
        frame = 0;
        dialogIndex = 0;
        gp.gameState = gp.blackoutState;
    }

    public void update() {
        if (phase == Phase.CLOSED) return;
        frame++;
        switch (phase) {
            case FADE_OUT:
                if (frame >= FADE_OUT_FRAMES) {
                    phase = Phase.DIALOG_PHASE;
                    frame = 0;
                }
                break;
            case DIALOG_PHASE:
                if (frame >= LINE_HOLD_FRAMES) {
                    dialogIndex++;
                    frame = 0;
                    if (dialogIndex >= DIALOG.length) {
                        // Teleport + heal happens between dialog and fade-in so the screen
                        // is still fully black during the move (no jarring teleport flash).
                        respawnAndHeal();
                        // Music was stopped at encounter end; resume now that the party is
                        // fully healed and we're about to fade the world back in.
                        gp.resumeMusic(gp.overworldMusicIndex());
                        phase = Phase.FADE_IN;
                        frame = 0;
                    }
                }
                break;
            case FADE_IN:
                if (frame >= FADE_IN_FRAMES) {
                    phase = Phase.CLOSED;
                    gp.gameState = gp.playState;
                }
                break;
            default:
                break;
        }
    }

    public void draw(Graphics2D g2) {
        if (phase == Phase.CLOSED) return;
        int alpha = 255;
        if (phase == Phase.FADE_OUT) {
            alpha = (int) (255.0 * frame / FADE_OUT_FRAMES);
        } else if (phase == Phase.FADE_IN) {
            alpha = (int) (255.0 * (FADE_IN_FRAMES - frame) / FADE_IN_FRAMES);
        }
        alpha = Math.max(0, Math.min(255, alpha));
        g2.setColor(new Color(0, 0, 0, alpha));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        if (phase == Phase.DIALOG_PHASE && dialogIndex < DIALOG.length && font != null) {
            g2.setFont(font);
            g2.setColor(Color.white);
            String line = DIALOG[dialogIndex];
            int textW = g2.getFontMetrics().stringWidth(line);
            g2.drawString(line, (gp.screenWidth - textW) / 2, gp.screenHeight / 2);
        }
    }

    private void respawnAndHeal() {
        gp.player.worldX = RESPAWN_COL * gp.tileSize;
        gp.player.worldY = RESPAWN_ROW * gp.tileSize;
        gp.playerPokemon.healAll();
    }
}
