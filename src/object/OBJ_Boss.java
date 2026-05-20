package object;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import Main.GamePanel;

// The trainer "????" — a static NPC tile that triggers a scripted trainer battle.
// Uses the same two-sprite stack as the player character (player_down_1 + !player_down_1)
// so the boss reads as a doppelganger. Walking into it triggers a six-pokemon battle;
// PlayerObjectInteraction.touchBoss instantiates a fresh team each fight so HP resets.
public class OBJ_Boss extends SuperObject {
    // Shown in pre-battle dialog ("???? would like to battle!").
    public static final String DISPLAY_NAME = "????";

    public static class Member {
        public final String name;
        public final int level;
        // Optional explicit moveset (move names from moves.csv). Null = let the picker
        // auto-generate. If shorter than 4, the remaining move slots stay empty.
        public final String[] moves;
        public Member(String name, int level) { this(name, level, null); }
        public Member(String name, int level, String[] moves) {
            this.name = name; this.level = level; this.moves = moves;
        }
    }

    public final List<Member> team;

    // Sprite halves are static — they're identical for every boss instance and the
    // encounter / battle code needs to render the boss portrait without holding the
    // OBJ_Boss reference. See drawBossPortrait below for the static helper they call.
    public static final BufferedImage TOP_SPRITE = loadOrNull("./src/res/player/player_down_1.png");
    public static final BufferedImage BOTTOM_SPRITE = loadOrNull("./src/res/player/!player_down_1.png");

    private static BufferedImage loadOrNull(String path) {
        try { return ImageIO.read(new File(path)); }
        catch (IOException e) { e.printStackTrace(); return null; }
    }

    public OBJ_Boss(List<Member> team) {
        this.name = "Boss";
        this.team = team;
        this.collision = false; // walking onto the tile triggers the fight
    }

    // Static helper used by the encounter screen + BattleSystem to render the boss
    // portrait at the enemy sprite's footprint during a send-out.
    public static void drawBossPortrait(java.awt.Graphics2D g2, int x, int y, int w, int h) {
        int topH = h * 2 / 3;
        int botH = h - topH;
        if (TOP_SPRITE != null)    g2.drawImage(TOP_SPRITE,    x, y,           w, topH, null);
        if (BOTTOM_SPRITE != null) g2.drawImage(BOTTOM_SPRITE, x, y + topH,    w, botH, null);
    }

    // Convenience for AssetSetter: pass species names and a single level.
    public static OBJ_Boss withTeam(int level, String... names) {
        List<Member> roster = new ArrayList<>(names.length);
        for (String n : names) roster.add(new Member(n, level));
        return new OBJ_Boss(roster);
    }

    // Vararg roster of (name, level) interleaved pairs in case you want mixed levels.
    public static OBJ_Boss mixed(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("pairs must be (name, level)+");
        List<Member> roster = new ArrayList<>(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            roster.add(new Member((String) pairs[i], (Integer) pairs[i + 1]));
        }
        return new OBJ_Boss(roster);
    }

    @Override
    public void draw(Graphics2D g2, GamePanel gp) {
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;
        // Mirror Player.drawPlayerHalf: top is the upper ~2/3 lifted by 16px; bottom is
        // the lower 1/3 dropped by 16px. Keeps the boss visually identical to the player.
        int topH = (int) (gp.tileSize / 1.5);
        if (TOP_SPRITE != null) {
            g2.drawImage(TOP_SPRITE, screenX, screenY - 16, gp.tileSize, topH, null);
        }
        if (BOTTOM_SPRITE != null) {
            g2.drawImage(BOTTOM_SPRITE, screenX, screenY + 16, gp.tileSize, gp.tileSize / 3, null);
        }
    }

    // Avoid unused-import nag if no caller imports Arrays explicitly.
    @SuppressWarnings("unused")
    private static final Class<?> ARRAYS_HINT = Arrays.class;
}
