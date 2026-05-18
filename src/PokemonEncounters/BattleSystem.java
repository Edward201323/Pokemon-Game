package PokemonEncounters;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import javax.imageio.ImageIO;

import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.Move;
import Pokemon.Pokemon;
import Pokemon.TypeChart;

// Turn-based battle: pick action -> pick move -> resolve both sides by speed
// -> animate HP bars -> repeat until a faint or a successful run.
public class BattleSystem {
    private static final Random RNG = new Random();
    // Pacing: each beat (text, drain, faint, fade) gets its own breathing room.
    private static final int MESSAGE_MIN_FRAMES = 70;   // hold any line at least this long
    private static final int DAMAGE_DELAY_FRAMES = 40;  // text-only beat before HP starts draining
    private static final int POST_DAMAGE_HOLD = 30;     // additional hold after the bar settles
    private static final int FAINT_FRAMES = 60;         // sink-into-ground duration
    private static final int POST_FAINT_HOLD = 10;      // pause after sink before FINISHED
    private static final double BAR_DRAIN_FULL_FRAMES = 90.0; // a full bar drains in ~1.5s

    // Catch animation
    private static final int CATCH_THROW_FRAMES = 30;   // ball arcs from player toward enemy
    private static final int CATCH_SHAKE_FRAMES = 28;   // one full left-right wiggle
    private static final int CATCH_NO_SHAKE_HOLD = 30;  // pause when the ball breaks immediately
    private static final int BALL_START_X = 200;
    private static final int BALL_START_Y = 380;
    private static final int BALL_END_X = 647;          // centered on the enemy sprite at (600,150,175,175)
    private static final int BALL_END_Y = 197;
    private static final int BALL_SIZE = 80;
    private static final int BALL_ARC_HEIGHT = 140;

    private enum Phase { ENTRY, CHOOSE_ACTION, CHOOSE_MOVE, MESSAGE, FAINT_ANIM, CATCH_THROW, CATCH_SHAKE, FINISHED }

    private final GamePanel gp;
    private final KeyHandler keyH;

    private Phase phase = Phase.ENTRY;

    // Edge-detection: a key only counts as "just pressed" on the frame it goes
    // from up to down. Without this, holding Z to fight would auto-select move 1.
    private boolean prevZ, prevX, prevC, prevV;
    private boolean justZ, justX, justC, justV;

    // Animated HP-bar values that ease toward each Pokemon's currentHP.
    private double displayedPlayerHP;
    private double displayedEnemyHP;

    // Queued messages plus the action that fires when the queue drains.
    private final Deque<String> messageQueue = new ArrayDeque<>();
    private String currentMessage = "";
    private int messageFrame = 0;
    private Runnable afterAllMessages;

    // Faint sink animation: which side is sinking, plus elapsed frames since it started.
    private boolean playerFainting;
    private boolean enemyFainting;
    private int faintFrame;

    // Damage to apply mid-message so the "used Y!" line is on screen briefly before HP drains.
    private Pokemon pendingDamageTarget;
    private int pendingDamage;
    // -1 = no damage in current message. Otherwise the messageFrame at which damage hit.
    private int damageAppliedFrame = -1;

    // Catch state. ballOnEnemy keeps the ball drawn (and the enemy sprite hidden) across the
    // shake phase and the result message until either the encounter ends or the ball breaks.
    private final BufferedImage pokeballImg;
    private int catchFrame;
    private int catchShakeCount;
    private int catchShakesNeeded;
    private boolean catchSuccess;
    private boolean ballOnEnemy;

    public BattleSystem(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        BufferedImage ball = null;
        try {
            ball = ImageIO.read(new File("./src/res/objects/pokeball.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.pokeballImg = ball;
    }

    public void start() {
        phase = Phase.ENTRY;
        messageQueue.clear();
        currentMessage = "";
        messageFrame = 0;
        afterAllMessages = null;
        playerFainting = false;
        enemyFainting = false;
        faintFrame = 0;
        pendingDamageTarget = null;
        pendingDamage = 0;
        damageAppliedFrame = -1;
        catchFrame = 0;
        catchShakeCount = 0;
        catchShakesNeeded = 0;
        catchSuccess = false;
        ballOnEnemy = false;
        // Seed prev* with current state so a key already held when the battle
        // begins must be released and re-pressed before it registers.
        prevZ = keyH.zPressed;
        prevX = keyH.xPressed;
        prevC = keyH.cPressed;
        prevV = keyH.vPressed;
        displayedPlayerHP = player().currentHP;
        displayedEnemyHP = enemy().currentHP;
    }

    public boolean isFinished() { return phase == Phase.FINISHED; }
    public boolean playerWon()  { return enemy().currentHP <= 0 && player().currentHP > 0; }

    // True while a thrown pokeball is sitting on the enemy (shake + result message). The
    // encounter uses this to suppress the enemy sprite so the ball isn't drawn over top of it.
    public boolean enemyHiddenByBall() { return ballOnEnemy; }

    // 0 = upright, 1 = fully sunk. Used by PokemonEncounter to translate+clip the sprite.
    public double enemyFaintFraction() {
        if (!enemyFainting) return 0.0;
        return Math.min(1.0, faintFrame / (double) FAINT_FRAMES);
    }
    public double playerFaintFraction() {
        if (!playerFainting) return 0.0;
        return Math.min(1.0, faintFrame / (double) FAINT_FRAMES);
    }

    private Pokemon player() { return gp.playerPokemon.pokemonEquipped.get(0); }
    private Pokemon enemy()  { return gp.wildPokemon; }

    // ----- frame update -----

    public void update() {
        sampleEdges();
        easeHpBars();

        if (phase == Phase.ENTRY) {
            phase = Phase.CHOOSE_ACTION;
            return;
        }
        if (phase == Phase.MESSAGE) {
            messageFrame++;
            if (pendingDamageTarget != null && messageFrame >= DAMAGE_DELAY_FRAMES) {
                pendingDamageTarget.currentHP = Math.max(0, pendingDamageTarget.currentHP - pendingDamage);
                pendingDamageTarget = null;
                pendingDamage = 0;
                damageAppliedFrame = messageFrame;
            }
            boolean hpSettled = displayedPlayerHP <= player().currentHP + 0.5
                             && displayedEnemyHP  <= enemy().currentHP  + 0.5;
            // After damage hits, keep the line up until the bar has settled AND POST_DAMAGE_HOLD
            // extra frames have passed, so the drain reads as its own beat.
            boolean postDamageOk = (damageAppliedFrame < 0)
                || (hpSettled && messageFrame >= damageAppliedFrame + POST_DAMAGE_HOLD);
            if (messageFrame >= MESSAGE_MIN_FRAMES && postDamageOk) {
                advanceMessage();
            }
            return;
        }
        if (phase == Phase.FAINT_ANIM) {
            faintFrame++;
            // FAINT_FRAMES of sink, then POST_FAINT_HOLD of just sitting on the fainted line
            // before we hand control back to PokemonEncounter for the fade.
            if (faintFrame >= FAINT_FRAMES + POST_FAINT_HOLD) phase = Phase.FINISHED;
            return;
        }
        if (phase == Phase.CATCH_THROW) {
            catchFrame++;
            if (catchFrame >= CATCH_THROW_FRAMES) {
                phase = Phase.CATCH_SHAKE;
                catchFrame = 0;
                ballOnEnemy = true;
            }
            return;
        }
        if (phase == Phase.CATCH_SHAKE) {
            catchFrame++;
            // 0 shakes = ball pops open instantly; hold briefly so the player sees it land first.
            if (catchShakesNeeded == 0) {
                if (catchFrame >= CATCH_NO_SHAKE_HOLD) resolveCatch();
                return;
            }
            if (catchFrame >= CATCH_SHAKE_FRAMES) {
                catchShakeCount++;
                catchFrame = 0;
                if (catchShakeCount >= catchShakesNeeded) resolveCatch();
            }
            return;
        }
        if (phase == Phase.CHOOSE_ACTION) handleActionInput();
        else if (phase == Phase.CHOOSE_MOVE) handleMoveInput();
    }

    private void startFaint(boolean isPlayer) {
        if (isPlayer) playerFainting = true;
        else          enemyFainting  = true;
        faintFrame = 0;
        phase = Phase.FAINT_ANIM;
    }

    private void sampleEdges() {
        justZ = keyH.zPressed && !prevZ;
        justX = keyH.xPressed && !prevX;
        justC = keyH.cPressed && !prevC;
        justV = keyH.vPressed && !prevV;
        prevZ = keyH.zPressed;
        prevX = keyH.xPressed;
        prevC = keyH.cPressed;
        prevV = keyH.vPressed;
    }

    private void easeHpBars() {
        // Linear drain proportional to max HP so a full-bar hit takes BAR_DRAIN_FULL_FRAMES (~1s)
        // and small hits stay quick. Old logic was percentage-of-remaining and finished in ~7 frames.
        double pTarget = player().currentHP;
        double eTarget = enemy().currentHP;
        double pRate = Math.max(0.5, player().maxHP / BAR_DRAIN_FULL_FRAMES);
        double eRate = Math.max(0.5, enemy().maxHP  / BAR_DRAIN_FULL_FRAMES);
        if (displayedPlayerHP > pTarget) {
            displayedPlayerHP = Math.max(pTarget, displayedPlayerHP - pRate);
        }
        if (displayedEnemyHP > eTarget) {
            displayedEnemyHP = Math.max(eTarget, displayedEnemyHP - eRate);
        }
    }

    private void advanceMessage() {
        if (messageQueue.isEmpty()) {
            Runnable next = afterAllMessages;
            afterAllMessages = null;
            // Keep currentMessage so the final line stays on screen after FINISHED.
            if (next != null) next.run();
            // The runnable above may have queued the next attacker's "used Y!" line
            // (e.g. doPlayerAttack from the player-second branch). Pop it now so its
            // messageFrame resets to 0 — otherwise the next-tick pending-damage check
            // would fire against the previous message's frame and the defender's bar
            // would drain before the new line ever shows.
            if (phase == Phase.MESSAGE && !messageQueue.isEmpty()) {
                currentMessage = messageQueue.pollFirst();
                messageFrame = 0;
                damageAppliedFrame = -1;
            }
            return;
        }
        currentMessage = messageQueue.pollFirst();
        messageFrame = 0;
        damageAppliedFrame = -1;
    }

    // ----- input -----

    private void handleActionInput() {
        if (justZ)      phase = Phase.CHOOSE_MOVE;                     // Fight
        else if (justX) startCatch();                                  // Catch (was Bag)
        else if (justC) queue("You don't have another Pokemon!",   () -> phase = Phase.CHOOSE_ACTION);
        else if (justV) queue("You got away safely!",              () -> phase = Phase.FINISHED);
    }

    // Decide the catch outcome up front, then play the animation that's consistent with it
    // (a successful catch always shows 3 shakes + click; a failure shakes 0–3 times then breaks).
    private void startCatch() {
        phase = Phase.CATCH_THROW;
        catchFrame = 0;
        catchShakeCount = 0;
        ballOnEnemy = false;
        Pokemon e = enemy();
        double maxHp = Math.max(1, e.maxHP);
        double hpFactor = (3.0 * maxHp - 2.0 * e.currentHP) / (3.0 * maxHp); // 1/3 at full HP, 1 at 0 HP
        double chance = Math.max(0.05, Math.min(0.95, hpFactor * 0.85));
        catchSuccess = RNG.nextDouble() < chance;
        catchShakesNeeded = catchSuccess ? 3 : RNG.nextInt(4);
    }

    private void resolveCatch() {
        if (catchSuccess) {
            queue("Gotcha! " + enemy().name + " was caught!", () -> phase = Phase.FINISHED);
        } else {
            queue("Aw! It broke free!", () -> {
                ballOnEnemy = false;
                phase = Phase.CHOOSE_ACTION;
            });
        }
    }

    private void handleMoveInput() {
        Move chosen = null;
        if      (justZ) chosen = moveAt(0);
        else if (justX) chosen = moveAt(1);
        else if (justC) chosen = moveAt(2);
        else if (justV) chosen = moveAt(3);
        if (chosen != null) resolveTurn(chosen);
    }

    private Move moveAt(int i) {
        java.util.List<Move> ms = player().moves;
        return (ms != null && i < ms.size()) ? ms.get(i) : null;
    }

    // ----- turn resolution -----

    private void resolveTurn(Move playerMove) {
        Pokemon p = player();
        Pokemon e = enemy();
        Move enemyMove = pickAIMove(e);

        boolean playerFirst = (p.currentSpeed > e.currentSpeed)
            || (p.currentSpeed == e.currentSpeed && RNG.nextBoolean());

        if (playerFirst) {
            doPlayerAttack(playerMove);
            then(() -> {
                if (e.currentHP <= 0) {
                    queue("Wild " + e.name + " fainted!", () -> startFaint(false));
                } else {
                    doEnemyAttack(enemyMove);
                    then(this::afterEnemyAttack);
                }
            });
        } else {
            doEnemyAttack(enemyMove);
            then(() -> {
                if (p.currentHP <= 0) {
                    queue(p.name + " fainted!", () -> startFaint(true));
                } else {
                    doPlayerAttack(playerMove);
                    then(this::afterPlayerAttack);
                }
            });
        }
    }

    private void afterPlayerAttack() {
        if (enemy().currentHP <= 0) {
            queue("Wild " + enemy().name + " fainted!", () -> startFaint(false));
        } else {
            phase = Phase.CHOOSE_ACTION;
        }
    }

    private void afterEnemyAttack() {
        if (player().currentHP <= 0) {
            queue(player().name + " fainted!", () -> startFaint(true));
        } else {
            phase = Phase.CHOOSE_ACTION;
        }
    }

    private void doPlayerAttack(Move move) {
        doAttack(player(), enemy(), move, player().name);
    }

    private void doEnemyAttack(Move move) {
        if (move == null) {
            queue("Wild " + enemy().name + " has no moves and does nothing.", null);
            return;
        }
        doAttack(enemy(), player(), move, "Wild " + enemy().name);
    }

    private void doAttack(Pokemon attacker, Pokemon defender, Move move, String attackerLabel) {
        queue(attackerLabel + " used " + move.name + "!", null);
        if (!rollHit(move)) {
            queue("But it missed!", null);
            return;
        }
        double eff = TypeChart.effectiveness(move.type, defender.currentType1, defender.currentType2);
        if (eff == 0.0) {
            queue("It doesn't affect " + defender.name + "...", null);
            return;
        }
        // Hold the damage until DAMAGE_DELAY_FRAMES into the "used Y!" message so the
        // bar drain feels reactive to the move name instead of simultaneous.
        pendingDamageTarget = defender;
        pendingDamage = computeDamage(attacker, defender, move, eff);
        if (eff >= 2.0) {
            queue("It's super effective!", null);
        } else if (eff < 1.0) {
            queue("It's not very effective...", null);
        }
    }

    private boolean rollHit(Move move) {
        return move.accuracy < 0 || RNG.nextInt(100) < move.accuracy;
    }

    private int computeDamage(Pokemon attacker, Pokemon defender, Move move, double effectiveness) {
        int atk = move.physical ? attacker.currentAttack : attacker.currentSpAttack;
        int def = move.physical ? defender.currentDefense : defender.currentSpDef;
        if (def <= 0) def = 1;
        double base = (((2.0 * attacker.level / 5.0 + 2.0) * move.basePower * atk / def) / 50.0) + 2.0;
        if (sameType(attacker.type1, move.type) || sameType(attacker.type2, move.type)) {
            base *= 1.5; // STAB
        }
        base *= effectiveness;
        base *= 0.85 + RNG.nextDouble() * 0.15;
        return Math.max(1, (int) base);
    }

    private static boolean sameType(String pokemonType, String moveType) {
        return pokemonType != null && pokemonType.equalsIgnoreCase(moveType);
    }

    private Move pickAIMove(Pokemon enemy) {
        if (enemy.moves == null || enemy.moves.isEmpty()) return null;
        return enemy.moves.get(RNG.nextInt(enemy.moves.size()));
    }

    // ----- message queue helpers -----

    // Enqueue one message; the queued `after` runs once the WHOLE queue drains.
    // Multiple queue() calls in a row chain into one sequence then fire `after`.
    private void queue(String text, Runnable after) {
        messageQueue.addLast(text);
        if (after != null) afterAllMessages = after;
        if (phase != Phase.MESSAGE) {
            phase = Phase.MESSAGE;
            advanceMessage();
        }
    }

    private void then(Runnable action) {
        afterAllMessages = action;
    }

    // ----- drawing -----

    public void draw(Graphics2D g2, BufferedImage[] encounterAssets, Font bigFont, Font smallFont) {
        // Rect dimensions match the green pill baked into 3_EnemyHpBar / 4_MyHpBar so the
        // dark background fully covers the static green; the colored fill then drains over it.
        drawHpFill(g2, displayedEnemyHP,  enemy().maxHP,  193, 113, 163, 9);
        drawHpFill(g2, displayedPlayerHP, player().maxHP, 609, 391, 171, 9);
        drawPlayerHpText(g2, smallFont);

        if (shouldDrawCatchBall()) drawCatchBall(g2);

        if (phase == Phase.CHOOSE_ACTION) {
            drawActionMenu(g2, encounterAssets, bigFont);
        } else if (phase == Phase.CHOOSE_MOVE) {
            drawMoveMenu(g2, encounterAssets, smallFont);
        } else if ((phase == Phase.MESSAGE || phase == Phase.FAINT_ANIM || phase == Phase.FINISHED)
                   && !currentMessage.isEmpty()) {
            drawDialog(g2, currentMessage, bigFont);
        }
    }

    private boolean shouldDrawCatchBall() {
        if (phase == Phase.CATCH_THROW || phase == Phase.CATCH_SHAKE) return true;
        // During the "Gotcha!" / "broke free!" message the ball stays put until the callback fires.
        return phase == Phase.MESSAGE && ballOnEnemy;
    }

    private void drawCatchBall(Graphics2D g2) {
        if (pokeballImg == null) return;
        int x, y;
        if (phase == Phase.CATCH_THROW) {
            double t = catchFrame / (double) CATCH_THROW_FRAMES;
            x = (int) Math.round(BALL_START_X + (BALL_END_X - BALL_START_X) * t);
            // Parabolic arc peaking at t=0.5, pulling the midpoint BALL_ARC_HEIGHT pixels up.
            double arc = -BALL_ARC_HEIGHT * (4.0 * t * (1.0 - t));
            y = (int) Math.round(BALL_START_Y + (BALL_END_Y - BALL_START_Y) * t + arc);
        } else if (phase == Phase.CATCH_SHAKE && catchShakesNeeded > 0) {
            double s = (catchFrame / (double) CATCH_SHAKE_FRAMES) * Math.PI * 2.0;
            x = BALL_END_X + (int) Math.round(Math.sin(s) * 10);
            y = BALL_END_Y;
        } else {
            x = BALL_END_X;
            y = BALL_END_Y;
        }
        g2.drawImage(pokeballImg, x, y, BALL_SIZE, BALL_SIZE, null);
    }

    private void drawHpFill(Graphics2D g2, double current, int max, int x, int y, int w, int h) {
        if (max <= 0) return;
        double ratio = Math.max(0.0, Math.min(1.0, current / (double) max));
        int filled = (int) Math.round(w * ratio);
        Color c;
        if (ratio > 0.5) c = new Color(80, 200, 80);
        else if (ratio > 0.2) c = new Color(230, 200, 60);
        else c = new Color(220, 60, 60);
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(x, y, w, h);
        g2.setColor(c);
        g2.fillRect(x, y, filled, h);
    }

    private void drawPlayerHpText(Graphics2D g2, Font small) {
        g2.setFont(small);
        g2.setColor(Color.black);
        Pokemon p = player();
        g2.drawString((int) Math.ceil(displayedPlayerHP) + "/" + p.maxHP, 640, 425);
    }

    private void drawActionMenu(Graphics2D g2, BufferedImage[] assets, Font font) {
        g2.drawImage(assets[1], 500, 497, 364, 175, null);
        Pokemon p = player();

        // "What will X do?" prompt on the left dialog half.
        g2.setFont(font);
        g2.setColor(Color.black);
        g2.drawString("What will " + p.name, 40, gp.screenHeight - 110);
        g2.drawString("do?", 40, gp.screenHeight - 70);
        g2.setColor(Color.white);
        g2.drawString("What will " + p.name, 38, gp.screenHeight - 112);
        g2.drawString("do?", 38, gp.screenHeight - 72);

        // Key cues placed to the right of each baked label in 1_FBPR.png. The label baselines
        // sit at y=582 (top row) and y=636 (bottom row) when the asset is drawn at 500,497,364,175.
        g2.setFont(font.deriveFont(Font.BOLD, 22f));
        g2.setColor(new Color(200, 40, 40));
        g2.drawString("(Z)", 625, 582);   // after FIGHT
        g2.drawString("(X)", 815, 582);   // after CATCH
        g2.drawString("(C)", 632, 636);   // after POKéMON
        g2.drawString("(V)", 780, 636);   // after RUN
    }

    private void drawMoveMenu(Graphics2D g2, BufferedImage[] assets, Font font) {
        // 2_ChooseMove.png is 240x47 with 4 move cells on the left and a PP/TYPE sidebar on the right.
        // Draw it across the full bottom dialog area so its aspect is preserved.
        int barY = gp.screenHeight - 175;
        if (assets[2] != null) {
            g2.drawImage(assets[2], 0, barY, gp.screenWidth, 175, null);
        }

        java.util.List<Move> moves = player().moves;
        String[] labels = { "Z", "X", "C", "V" };
        // Cell baselines tuned to the asset's 4-quadrant layout (left ~80% of width).
        int[] xs = { 40, 360, 40, 360 };
        int[] ys = { barY + 67, barY + 67, barY + 141, barY + 141 };

        g2.setFont(font);
        for (int i = 0; i < 4; i++) {
            String text = (moves != null && i < moves.size())
                ? labels[i] + " " + moves.get(i).name
                : labels[i] + " -";
            g2.setColor(new Color(40, 40, 40));
            g2.drawString(text, xs[i], ys[i]);
        }
    }

    private void drawDialog(Graphics2D g2, String text, Font font) {
        g2.setFont(font);
        g2.setColor(Color.black);
        g2.drawString(text, 40, gp.screenHeight - 110);
        g2.setColor(Color.white);
        g2.drawString(text, 38, gp.screenHeight - 112);
    }
}
