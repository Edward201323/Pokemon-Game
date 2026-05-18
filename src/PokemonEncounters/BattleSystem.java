package PokemonEncounters;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
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
    private static final double BAR_DRAIN_FULL_FRAMES = 20.0; // a full bar drains in ~2.5s
    // Catch animation timing — ball flies in an arc, sits briefly, wobbles N times, then resolves.
    private static final int THROW_FLY_FRAMES = 42;
    private static final int BALL_LAND_HOLD = 14;
    private static final int WOBBLE_FRAMES = 38;
    private static final int POST_WOBBLE_HOLD = 18;
    // Where the ball ends up on the enemy and where it starts (lower-left, off-trainer).
    private static final int BALL_START_X = 140, BALL_START_Y = 580;
    private static final int BALL_LAND_X  = 685, BALL_LAND_Y  = 235;
    private static final int BALL_DRAW_SIZE = 56;
    private static final double THROW_ARC_HEIGHT = 240.0;
    private static final double WOBBLE_MAX_TILT = 0.40; // radians (~23 degrees)

    private enum Phase { ENTRY, CHOOSE_ACTION, CHOOSE_MOVE, MESSAGE, BALL_THROW, FAINT_ANIM, FINISHED }

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

    // Catch state. enemyHidden hides the wild sprite once the ball "lands"; the rest is
    // pre-rolled at throw time so the wobble count and result are committed before animating.
    private int throwFrame;
    private boolean ballOnEnemy;     // ball has finished its arc and is resting on the enemy
    private boolean ballVisible;     // ball drawn this frame (cleared when it pops open on a miss)
    private boolean enemyHidden;
    private boolean ballThrowResolved;
    private boolean catchSuccess;
    private int shakeCount;          // 0-3 = wobbles before break, 4 = caught
    private Move postCatchEnemyMove; // a failed catch uses your turn — enemy attacks once after
    private BufferedImage ballImage;

    public BattleSystem(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        try {
            this.ballImage = ImageIO.read(new File("./src/res/objects/pokeball.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        throwFrame = 0;
        ballOnEnemy = false;
        ballVisible = false;
        enemyHidden = false;
        ballThrowResolved = false;
        catchSuccess = false;
        shakeCount = 0;
        postCatchEnemyMove = null;
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

    // 0 = upright, 1 = fully sunk. Used by PokemonEncounter to translate+clip the sprite.
    public double enemyFaintFraction() {
        if (!enemyFainting) return 0.0;
        return Math.min(1.0, faintFrame / (double) FAINT_FRAMES);
    }
    public double playerFaintFraction() {
        if (!playerFainting) return 0.0;
        return Math.min(1.0, faintFrame / (double) FAINT_FRAMES);
    }

    // True while the thrown ball is sitting on the enemy. PokemonEncounter checks this
    // to skip drawing the wild sprite so the ball reads as "containing" it.
    public boolean enemyHiddenByBall() { return enemyHidden; }

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
        if (phase == Phase.BALL_THROW) {
            throwFrame++;
            if (throwFrame == THROW_FLY_FRAMES) {
                // Ball reached the enemy: lock it there and pull the enemy sprite off-screen.
                ballOnEnemy = true;
                enemyHidden = true;
            }
            int wobbles = displayedWobbleCount();
            int resolveAt = THROW_FLY_FRAMES + BALL_LAND_HOLD + wobbles * WOBBLE_FRAMES + POST_WOBBLE_HOLD;
            if (throwFrame >= resolveAt && !ballThrowResolved) {
                ballThrowResolved = true;
                beginCatchResolution();
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
        else if (justX) startBallThrow();                              // Bag = throw Poke Ball
        else if (justC) queue("You don't have another Pokemon!",   () -> phase = Phase.CHOOSE_ACTION);
        else if (justV) queue("You got away safely!",              () -> phase = Phase.FINISHED);
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

    // ----- catch flow -----

    // Throw a Poke Ball. Roll the catch result up-front so the wobble count and outcome are
    // committed before the animation plays, then transition to BALL_THROW for the visual.
    private void startBallThrow() {
        Pokemon e = enemy();
        // Pre-pick the enemy's response now — a failed catch still uses the turn.
        postCatchEnemyMove = pickAIMove(e);
        shakeCount = rollCatch(e);
        catchSuccess = shakeCount >= 4;
        throwFrame = 0;
        ballOnEnemy = false;
        ballVisible = true;
        enemyHidden = false;
        ballThrowResolved = false;
        currentMessage = "";
        messageFrame = 0;
        damageAppliedFrame = -1;
        phase = Phase.BALL_THROW;
    }

    // Show three wobbles on success (per the games), or however many shake checks passed on a miss.
    private int displayedWobbleCount() {
        return catchSuccess ? 3 : Math.min(shakeCount, 3);
    }

    // Ultra Ball bonus from Gen 1 onward.
    private static final double ULTRA_BALL_BONUS = 2.0;

    // Gen 5+ catch formula. HP scaling is built into the (3*HPmax - 2*HPcur) term, so a
    // weakened pokemon is easier to catch. No status conditions in this game, so statusBonus = 1.
    //   a = ((3*HPmax - 2*HPcur) * captureRate * ballBonus) / (3*HPmax)
    //   b = 65536 / (255/a)^(3/16)
    // Then roll four 16-bit ints; each one under b is a successful "shake". 4 in a row = caught.
    private int rollCatch(Pokemon e) {
        int hpMax = Math.max(1, e.maxHP);
        int hpCur = Math.max(1, e.currentHP);
        int cr = Math.max(1, e.captureRate);
        double a = ((3.0 * hpMax - 2.0 * hpCur) * cr * ULTRA_BALL_BONUS) / (3.0 * hpMax);
        if (a >= 255) return 4;
        if (a < 1) a = 1;
        double b = 65536.0 / Math.pow(255.0 / a, 3.0 / 16.0);
        int shakes = 0;
        for (int i = 0; i < 4; i++) {
            if (RNG.nextInt(65536) < b) shakes++;
            else break;
        }
        return shakes;
    }

    private void beginCatchResolution() {
        Pokemon e = enemy();
        if (catchSuccess) {
            // Ball stays sitting on the (hidden) enemy through the verdict and the fade.
            queue("Gotcha! " + e.name + " was caught!", () -> {
                gp.playerPokemon.addPokemonCaught();
                phase = Phase.FINISHED;
            });
        } else {
            // Ball pops open: hide the ball, bring the enemy back, then the canonical breakaway line.
            ballVisible = false;
            ballOnEnemy = false;
            enemyHidden = false;
            queue(breakFreeLine(shakeCount), () -> {
                doEnemyAttack(postCatchEnemyMove);
                then(this::afterEnemyAttack);
            });
        }
    }

    // Mainline Pokemon's shake-count-keyed break-free lines (Gen 4/5 wording).
    private static String breakFreeLine(int shakes) {
        switch (shakes) {
            case 0:  return "Oh no! The Pokemon broke free!";
            case 1:  return "Aww! It appeared to be caught!";
            case 2:  return "Aargh! Almost had it!";
            case 3:  return "Gah! It was so close, too!";
            default: return "Oh no! The Pokemon broke free!";
        }
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

        if (phase == Phase.CHOOSE_ACTION) {
            drawActionMenu(g2, encounterAssets, bigFont);
        } else if (phase == Phase.CHOOSE_MOVE) {
            drawMoveMenu(g2, encounterAssets, smallFont);
        } else if ((phase == Phase.MESSAGE || phase == Phase.FAINT_ANIM || phase == Phase.FINISHED)
                   && !currentMessage.isEmpty()) {
            drawDialog(g2, currentMessage, bigFont);
        }
        // The ball overlays everything else (incl. the dialog box on "Gotcha!") whenever it's in play.
        drawBall(g2);
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
        g2.drawString((int) Math.ceil(displayedPlayerHP) + "/" + p.maxHP, 640, 428);
    }

    private void drawActionMenu(Graphics2D g2, BufferedImage[] assets, Font font) {
        g2.drawImage(assets[1], 500, 497, 364, 175, null);
        Pokemon p = player();

        // FIGHT/BAG/POK&MON/RUN and the Z/X/C/V key cues are baked into 1_FBPR.png.
        g2.setFont(font);
        g2.setColor(Color.black);
        g2.drawString("What will " + p.name, 40, gp.screenHeight - 110);
        g2.drawString("do?", 40, gp.screenHeight - 70);

        g2.setColor(Color.white);
        g2.drawString("What will " + p.name, 38, gp.screenHeight - 112);
        g2.drawString("do?", 38, gp.screenHeight - 72);
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

    // Draw the pokeball in whatever state matches the current throwFrame: parabolic arc while
    // flying, then a sinusoidal tilt for each wobble. Pivot at the ball center so the tilt looks
    // like the ball rocking on the ground.
    private void drawBall(Graphics2D g2) {
        if (ballImage == null || !ballVisible) return;
        int cx, cy;
        double rotation;
        if (!ballOnEnemy) {
            double t = Math.min(1.0, throwFrame / (double) THROW_FLY_FRAMES);
            cx = (int) (BALL_START_X + (BALL_LAND_X - BALL_START_X) * t);
            double base = BALL_START_Y + (BALL_LAND_Y - BALL_START_Y) * t;
            cy = (int) (base - Math.sin(Math.PI * t) * THROW_ARC_HEIGHT);
            rotation = t * Math.PI * 4; // two full spins mid-air
        } else {
            cx = BALL_LAND_X;
            cy = BALL_LAND_Y;
            int sinceLand = throwFrame - THROW_FLY_FRAMES - BALL_LAND_HOLD;
            int wobbles = displayedWobbleCount();
            if (sinceLand >= 0 && sinceLand < wobbles * WOBBLE_FRAMES) {
                int wobbleT = sinceLand % WOBBLE_FRAMES;
                // One sine cycle per wobble: right, back, left, back to upright.
                rotation = Math.sin(2 * Math.PI * wobbleT / WOBBLE_FRAMES) * WOBBLE_MAX_TILT;
            } else {
                rotation = 0;
            }
        }
        // Clip to the field area so the arc never bleeds onto the bottom dialog bar.
        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(0, 0, gp.screenWidth, gp.screenHeight - 175);
        AffineTransform saved = g2.getTransform();
        g2.translate(cx, cy);
        g2.rotate(rotation);
        g2.drawImage(ballImage, -BALL_DRAW_SIZE / 2, -BALL_DRAW_SIZE / 2,
                                 BALL_DRAW_SIZE, BALL_DRAW_SIZE, null);
        g2.setTransform(saved);
        g2.setClip(oldClip);
    }

    private void drawDialog(Graphics2D g2, String text, Font font) {
        g2.setFont(font);
        g2.setColor(Color.black);
        g2.drawString(text, 40, gp.screenHeight - 110);
        g2.setColor(Color.white);
        g2.drawString(text, 38, gp.screenHeight - 112);
    }
}
