package PokemonEncounters;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
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
import Pokemon.ExpCurves;
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
    // Targets the enemy sprite's *foot* region (lower portion of ENEMY_*) so the ball
    // visually sits at the pokemon's feet rather than its head/center.
    private static final int BALL_START_X = 140, BALL_START_Y = 600;
    private static final int BALL_LAND_X  = 665, BALL_LAND_Y  = 370;
    private static final int BALL_DRAW_SIZE = 56;
    private static final double THROW_ARC_HEIGHT = 240.0;
    private static final double WOBBLE_MAX_TILT = 0.40; // radians (~23 degrees)

    // Switch-throw animation: reuses the trainer poses (encounterAssets 10..13) so the
    // player visually throws a ball when sending the new pokemon out, matching the
    // initial-encounter send-out.
    private static final int SWITCH_POSE_FRAMES = 14;
    private static final int SWITCH_POSE_COUNT = 4;
    private static final int SWITCH_TOTAL_FRAMES = SWITCH_POSE_FRAMES * SWITCH_POSE_COUNT;

    private enum Phase { ENTRY, CHOOSE_ACTION, CHOOSE_MOVE, MESSAGE, BALL_THROW, SWITCH_THROW, FAINT_ANIM, FINISHED }

    private final GamePanel gp;
    private final KeyHandler keyH;

    private Phase phase = Phase.ENTRY;

    // Edge-detection: a key only counts as "just pressed" on the frame it goes
    // from up to down. Without this, holding Z to fight would auto-select move 1.
    private boolean prevZ, prevX, prevC, prevV, prevEsc;
    private boolean justZ, justX, justC, justV, justEsc;

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

    // Switch state. While true, PokemonEncounter skips drawing the player sprite so the
    // recall/throw animation reads as "the previous pokemon goes back, then the new one
    // emerges" without a hard cut between sprites mid-message.
    private boolean switchHidden;
    private int switchFrame;
    private Runnable afterSwitchThrow;

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
        switchHidden = false;
        switchFrame = 0;
        afterSwitchThrow = null;
        // Initial send-out: skip any fainted lead so we never put a 0-HP pokemon on the field.
        int firstLive = firstLiveIndex();
        if (firstLive < 0) {
            // Shouldn't happen — blackout should fire before a new encounter — but
            // bail out gracefully if it does.
            activeIndex = 0;
            phase = Phase.FINISHED;
            return;
        }
        activeIndex = firstLive;
        // Seed prev* with current state so a key already held when the battle
        // begins must be released and re-pressed before it registers.
        prevZ = keyH.zPressed;
        prevX = keyH.xPressed;
        prevC = keyH.cPressed;
        prevV = keyH.vPressed;
        prevEsc = keyH.escPressed;
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

    // Which party member is actually on the field. PokemonEncounter uses this so the
    // sprite reflects switches (instead of always drawing pokemonEquipped.get(0)).
    public Pokemon activePokemon() {
        java.util.List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        if (party.isEmpty()) return null;
        int idx = Math.max(0, Math.min(activeIndex, party.size() - 1));
        return party.get(idx);
    }

    // True while a switch is mid-animation: the outgoing pokemon has returned to its ball
    // and the new one hasn't been thrown out yet. PokemonEncounter skips the player sprite
    // for these frames so the active sprite doesn't snap from old to new.
    public boolean playerHiddenForSwitch() { return switchHidden; }

    // Active party slot — switching changes this instead of mutating the party list,
    // so other systems (PC viewer, party menu) still see a consistent ordering.
    private int activeIndex = 0;

    private Pokemon player() { return gp.playerPokemon.pokemonEquipped.get(activeIndex); }
    private Pokemon enemy()  { return gp.wildPokemon; }

    private int firstLiveIndex() { return firstLiveExcept(-1); }

    private int firstLiveExcept(int skip) {
        java.util.List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        for (int i = 0; i < party.size(); i++) {
            if (i == skip) continue;
            Pokemon p = party.get(i);
            if (p != null && p.currentHP > 0) return i;
        }
        return -1;
    }

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
            // FAINT_FRAMES of sink, then POST_FAINT_HOLD of just sitting on the fainted line.
            if (faintFrame >= FAINT_FRAMES + POST_FAINT_HOLD) {
                if (playerFainting) {
                    // Force a switch if any teammate can still fight; otherwise FINISHED
                    // (caller's blackout will pick this up).
                    int next = firstLiveExcept(activeIndex);
                    playerFainting = false;
                    faintFrame = 0;
                    if (next < 0) {
                        phase = Phase.FINISHED;
                    } else {
                        openSwitchMenu(false);
                    }
                } else {
                    // Enemy fainted — award EXP to the active pokemon (with possible
                    // level-up) and queue the messages before transitioning to FINISHED.
                    giveExpAndFinish();
                }
            }
            return;
        }
        if (phase == Phase.SWITCH_THROW) {
            switchFrame++;
            // Throw SFX fires partway through, matching drawDawnThrowBall's beat at the
            // start of pose 2 (where the trainer releases the ball).
            if (switchFrame == SWITCH_POSE_FRAMES) {
                gp.playSE(0);
            }
            if (switchFrame >= SWITCH_TOTAL_FRAMES) {
                Runnable r = afterSwitchThrow;
                afterSwitchThrow = null;
                if (r != null) r.run();
            }
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
        justEsc = keyH.escPressed && !prevEsc;
        prevZ = keyH.zPressed;
        prevX = keyH.xPressed;
        prevC = keyH.cPressed;
        prevV = keyH.vPressed;
        prevEsc = keyH.escPressed;
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
        else if (justC) {
            // Pokemon: open the party selector. If no other teammate can fight, surface
            // a message instead of opening an empty menu.
            if (firstLiveExcept(activeIndex) < 0) {
                queue("No other Pokemon can fight!", () -> phase = Phase.CHOOSE_ACTION);
            } else {
                openSwitchMenu(true);
            }
        }
        else if (justV) queue("You got away safely!",              () -> phase = Phase.FINISHED);
    }

    // Open the party menu in selection mode. Voluntary switches are cancellable and cost
    // a turn (enemy gets a free attack); forced switches (after a faint) are not cancellable
    // and the player picks back up at CHOOSE_ACTION.
    private void openSwitchMenu(boolean voluntary) {
        final int currentlyActive = activeIndex;
        final Move enemyResponseMove = voluntary ? pickAIMove(enemy()) : null;
        gp.openPlayerInventory.openInSelectionMode(
            voluntary ? "Choose a Pokemon to send out." : "Choose your next Pokemon.",
            voluntary, // cancellable only for voluntary switches
            idx -> {
                Pokemon p = gp.playerPokemon.pokemonEquipped.get(idx);
                return p != null && p.currentHP > 0 && idx != currentlyActive;
            },
            idx -> performSwitch(idx, enemyResponseMove, !voluntary),
            () -> phase = Phase.CHOOSE_ACTION
        );
    }

    private void performSwitch(int newIndex, Move enemyMove, boolean forced) {
        Pokemon old = gp.playerPokemon.pokemonEquipped.get(activeIndex);
        activeIndex = newIndex;
        Pokemon nw = player();
        displayedPlayerHP = nw.currentHP;
        // Player sprite stays hidden during the recall + trainer throw; we flip it back on
        // when the trainer animation finishes so the new pokemon emerges on the field.
        switchHidden = true;
        Runnable revealNew = forced
            ? () -> {
                  switchHidden = false;
                  queue("Go! " + nw.name + "!", () -> phase = Phase.CHOOSE_ACTION);
              }
            : () -> {
                  switchHidden = false;
                  queue("Go! " + nw.name + "!", () -> {
                      doEnemyAttack(enemyMove);
                      then(this::afterEnemyAttack);
                  });
              };
        if (forced) {
            // Forced switch (post-faint): the previous pokemon already dropped during
            // FAINT_ANIM, so go straight into the trainer-throw animation.
            startSwitchThrow(revealNew);
        } else {
            queue("Come back, " + old.name + "!", () -> startSwitchThrow(revealNew));
        }
    }

    private void startSwitchThrow(Runnable after) {
        phase = Phase.SWITCH_THROW;
        switchFrame = 0;
        afterSwitchThrow = after;
    }

    // After the enemy's faint animation, award EXP to every non-fainted party member
    // (Gen 6+ Exp Share — everyone gets the full amount). Hard-caps the level at 100
    // (totalExp clamped to the curve max). Dialog is a single summary line plus a
    // per-pokemon level-up line for anyone who advanced.
    private void giveExpAndFinish() {
        int gain = ExpCurves.expGained(enemy().expGiven, enemy().level);
        java.util.List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        java.util.List<Pokemon> leveledUp = new java.util.ArrayList<>();
        int sharedCount = 0;
        for (Pokemon p : party) {
            if (p == null || p.currentHP <= 0) continue;
            if (p.level >= 100) continue; // already maxed, no XP to gain
            p.totalExp += gain;
            int curveMax = p.experienceGrowth;
            // Clamp so totalExp can't grow past the curve max once at level 100.
            if (p.totalExp > curveMax) p.totalExp = curveMax;
            int origLevel = p.level;
            while (p.level < 100
                    && p.totalExp >= ExpCurves.expAtLevel(p.level + 1, curveMax)) {
                p.level++;
            }
            if (p.level > origLevel) {
                p.recalcStats();
                leveledUp.add(p);
            }
            sharedCount++;
        }
        // Re-seed the displayed HP so the bar reflects the active pokemon's new maxHP
        // correctly while messages are on screen.
        Pokemon active = player();
        if (active != null) displayedPlayerHP = active.currentHP;

        Runnable finish = () -> phase = Phase.FINISHED;
        if (sharedCount == 0) {
            phase = Phase.FINISHED;
            return;
        }
        String activeName = (active != null) ? active.name : "Your Pokemon";
        String summary = (sharedCount > 1)
            ? activeName + " and your party gained " + gain + " EXP. Points!"
            : activeName + " gained " + gain + " EXP. Points!";
        queue(summary, leveledUp.isEmpty() ? finish : null);
        for (int i = 0; i < leveledUp.size(); i++) {
            Pokemon lp = leveledUp.get(i);
            boolean last = (i == leveledUp.size() - 1);
            queue(lp.name + " grew to Lv. " + lp.level + "!", last ? finish : null);
        }
    }

    private void handleMoveInput() {
        // Escape backs out of move selection — un-commits the FIGHT choice, returns to
        // the action menu (FIGHT / CATCH / POKEMON / RUN).
        if (justEsc) { phase = Phase.CHOOSE_ACTION; return; }
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
        // Custom HP panels reflect the *active* pokemon (post-switch sprites + stats).
        drawEnemyPanel(g2, enemy(), displayedEnemyHP, smallFont);
        drawPlayerPanel(g2, player(), displayedPlayerHP, smallFont);

        if (phase == Phase.CHOOSE_ACTION) {
            drawActionMenu(g2, encounterAssets, bigFont);
        } else if (phase == Phase.CHOOSE_MOVE) {
            drawMoveMenu(g2, encounterAssets, smallFont);
        } else if ((phase == Phase.MESSAGE || phase == Phase.FAINT_ANIM || phase == Phase.FINISHED)
                   && !currentMessage.isEmpty()) {
            drawDialogPanel(g2, currentMessage, bigFont, gp.screenWidth, gp.screenHeight);
        }
        // Switch throw: trainer poses (encounterAssets 10..13) at the player area while
        // the active pokemon is hidden. After the animation the new pokemon emerges.
        if (phase == Phase.SWITCH_THROW) {
            int pose = Math.min(SWITCH_POSE_COUNT - 1, switchFrame / SWITCH_POSE_FRAMES);
            int assetIdx = 10 + pose;
            if (assetIdx < encounterAssets.length && encounterAssets[assetIdx] != null) {
                g2.drawImage(encounterAssets[assetIdx],
                             PokemonEncounter.TRAINER_X, PokemonEncounter.TRAINER_Y,
                             PokemonEncounter.TRAINER_W, PokemonEncounter.TRAINER_H, null);
            }
        }
        // The ball overlays everything else (incl. the dialog box on "Gotcha!") whenever it's in play.
        drawBall(g2);
    }

    // ---- Custom HP panels (static so PokemonEncounter can call them pre-battle too). ----

    // Enemy panel: top-left, name + Lv + HP bar (no number text — matches mainline games).
    public static void drawEnemyPanel(Graphics2D g2, Pokemon enemy, double displayedHp, Font font) {
        if (enemy == null) return;
        int x = 32, y = 32, w = 370, h = 92;
        drawPanelBackground(g2, x, y, w, h);
        if (font != null) g2.setFont(font);
        g2.setColor(Color.white);
        g2.drawString(enemy.name, x + 20, y + 32);
        String lv = "Lv " + enemy.level;
        int lvW = g2.getFontMetrics().stringWidth(lv);
        g2.setColor(new Color(220, 230, 240));
        g2.drawString(lv, x + w - lvW - 18, y + 32);
        drawHpRow(g2, displayedHp, enemy.maxHP, x + 20, y + 58, w - 40, 12, font, false);
    }

    // Player panel: bottom-right corner. Slightly taller than the enemy panel because it
    // also carries the EXP bar (below the HP row).
    public static void drawPlayerPanel(Graphics2D g2, Pokemon p, double displayedHp, Font font) {
        if (p == null) return;
        int x = 490, y = 390, w = 360, h = 118;
        drawPanelBackground(g2, x, y, w, h);
        if (font != null) g2.setFont(font);
        g2.setColor(Color.white);
        g2.drawString(p.name, x + 18, y + 26);
        String lv = "Lv " + p.level;
        int lvW = g2.getFontMetrics().stringWidth(lv);
        g2.setColor(new Color(220, 230, 240));
        g2.drawString(lv, x + w - lvW - 16, y + 26);
        drawHpRow(g2, displayedHp, p.maxHP, x + 18, y + 46, w - 36, 11, font, true);
        drawExpRow(g2, p, x + 18, y + h - 14, w - 36, 6, font);
    }

    private static void drawExpRow(Graphics2D g2, Pokemon p,
                                    int x, int y, int w, int h, Font font) {
        if (font != null) g2.setFont(font);
        g2.setColor(new Color(150, 200, 255));
        g2.drawString("EXP", x, y - 2);
        int labelW = 48;
        int barX = x + labelW;
        int barW = w - labelW;
        double frac = ExpCurves.levelProgress(p);
        int filled = (int) Math.round(barW * frac);
        g2.setColor(new Color(20, 25, 32));
        g2.fillRoundRect(barX, y, barW, h, h, h);
        if (filled > 0) {
            // Blue fill, per the EXP-bar convention in mainline Pokemon.
            g2.setColor(new Color(80, 150, 240));
            g2.fillRoundRect(barX, y, filled, h, h, h);
        }
    }

    private static void drawPanelBackground(Graphics2D g2, int x, int y, int w, int h) {
        g2.setPaint(new GradientPaint(0, y, new Color(22, 32, 46, 235),
                                      0, y + h, new Color(10, 16, 26, 235)));
        g2.fillRoundRect(x, y, w, h, 18, 18);
        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, w, h, 18, 18);
        g2.setColor(new Color(140, 180, 220, 60));
        g2.drawRoundRect(x + 3, y + 3, w - 6, h - 6, 14, 14);
        g2.setStroke(prev);
    }

    private static void drawHpRow(Graphics2D g2, double current, int max,
                                   int x, int y, int w, int h, Font font, boolean withText) {
        // Label
        if (font != null) g2.setFont(font);
        g2.setColor(new Color(180, 220, 255));
        g2.drawString("HP", x, y + h - 1);
        int labelW = 38;
        int barX = x + labelW;
        int barW = w - labelW;
        // Track + fill
        if (max > 0) {
            double ratio = Math.max(0.0, Math.min(1.0, current / (double) max));
            int filled = (int) Math.round(barW * ratio);
            Color fill;
            if (ratio > 0.5)      fill = new Color(80, 200, 80);
            else if (ratio > 0.2) fill = new Color(230, 200, 60);
            else                  fill = new Color(220, 60, 60);
            g2.setColor(new Color(20, 25, 32));
            g2.fillRoundRect(barX, y, barW, h, h, h);
            if (filled > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(barX, y, filled, h, h, h);
            }
        }
        if (withText) {
            String hp = (int) Math.ceil(current) + " / " + max;
            int hpW = g2.getFontMetrics().stringWidth(hp);
            g2.setColor(Color.white);
            g2.drawString(hp, x + w - hpW, y + h + 22);
        }
    }


    // Custom action menu: same dark panel style as the dialog, with the prompt on the left
    // and a 2x2 grid of action buttons (FIGHT/CATCH/POKEMON/RUN) on the right.
    private void drawActionMenu(Graphics2D g2, BufferedImage[] assets, Font font) {
        int panelH = 150;
        int margin = 16;
        int x = margin;
        int y = gp.screenHeight - panelH - 8;
        int w = gp.screenWidth - margin * 2;

        // Panel background — matches drawDialogPanel.
        g2.setPaint(new GradientPaint(0, y, new Color(18, 28, 40, 235),
                                      0, y + panelH, new Color(8, 14, 22, 235)));
        g2.fillRoundRect(x, y, w, panelH, 22, 22);
        Stroke prevStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, w, panelH, 22, 22);
        g2.setColor(new Color(140, 180, 220, 70));
        g2.drawRoundRect(x + 3, y + 3, w - 6, panelH - 6, 18, 18);

        // Prompt on the left half.
        Pokemon p = player();
        g2.setFont(font);
        g2.setColor(new Color(245, 250, 255));
        g2.drawString("What will", x + 32, y + 56);
        g2.drawString(p.name + " do?", x + 32, y + 102);

        // 2x2 button grid on the right half.
        String[][] labels = {
            { "FIGHT (Z)",   "CATCH (X)" },
            { "POKEMON (C)", "RUN (V)" },
        };
        int gridX = x + w / 2 + 8;
        int gridY = y + 18;
        int gridW = (x + w) - gridX - 18;
        int gridH = panelH - 36;
        int gap = 10;
        int cellW = (gridW - gap) / 2;
        int cellH = (gridH - gap) / 2;
        g2.setStroke(new BasicStroke(1.5f));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                int bx = gridX + col * (cellW + gap);
                int by = gridY + row * (cellH + gap);
                g2.setColor(new Color(40, 60, 90, 210));
                g2.fillRoundRect(bx, by, cellW, cellH, 14, 14);
                g2.setColor(new Color(110, 150, 190, 230));
                g2.drawRoundRect(bx, by, cellW, cellH, 14, 14);
                String label = labels[row][col];
                int textW = g2.getFontMetrics().stringWidth(label);
                int tx = bx + (cellW - textW) / 2;
                int ty = by + cellH / 2 + 10;
                g2.setColor(Color.white);
                g2.drawString(label, tx, ty);
            }
        }
        g2.setStroke(prevStroke);
    }

    // Custom move menu: dark panel, 2x2 grid of move buttons, "ESC to go back" hint.
    private void drawMoveMenu(Graphics2D g2, BufferedImage[] assets, Font font) {
        int panelH = 150;
        int margin = 16;
        int x = margin;
        int y = gp.screenHeight - panelH - 8;
        int w = gp.screenWidth - margin * 2;

        // Panel background — same style as action/dialog panels.
        g2.setPaint(new GradientPaint(0, y, new Color(18, 28, 40, 235),
                                      0, y + panelH, new Color(8, 14, 22, 235)));
        g2.fillRoundRect(x, y, w, panelH, 22, 22);
        Stroke prevStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, w, panelH, 22, 22);
        g2.setColor(new Color(140, 180, 220, 70));
        g2.drawRoundRect(x + 3, y + 3, w - 6, panelH - 6, 18, 18);

        // 2x2 grid of move buttons across the panel (leave a small strip for the back hint).
        java.util.List<Move> moves = player().moves;
        String[] keys = { "Z", "X", "C", "V" };
        int gridX = x + 24;
        int gridY = y + 18;
        int gridW = w - 48;
        int gridH = panelH - 50; // reserve ~32px for hint strip
        int gap = 10;
        int cellW = (gridW - gap) / 2;
        int cellH = (gridH - gap) / 2;
        g2.setFont(font);
        for (int i = 0; i < 4; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = gridX + col * (cellW + gap);
            int by = gridY + row * (cellH + gap);
            boolean has = moves != null && i < moves.size() && moves.get(i) != null;
            g2.setColor(new Color(40, 60, 90, has ? 210 : 130));
            g2.fillRoundRect(bx, by, cellW, cellH, 14, 14);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(110, 150, 190, has ? 230 : 140));
            g2.drawRoundRect(bx, by, cellW, cellH, 14, 14);
            String label = has ? (keys[i] + "   " + moves.get(i).name) : (keys[i] + "   —");
            int textW = g2.getFontMetrics().stringWidth(label);
            int tx = bx + (cellW - textW) / 2;
            int ty = by + cellH / 2 + 10;
            g2.setColor(has ? Color.white : new Color(160, 170, 180));
            g2.drawString(label, tx, ty);
        }
        // Hint strip
        g2.setColor(new Color(180, 195, 210));
        String hint = "ESC to go back";
        int hintW = g2.getFontMetrics().stringWidth(hint);
        g2.drawString(hint, x + w - hintW - 24, y + panelH - 14);
        g2.setStroke(prevStroke);
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

    // Custom dialog panel for battle messages — rounded, subtle gradient, soft border.
    // Public + static so PokemonEncounter can render its pre-battle dialog with the same look.
    public static void drawDialogPanel(Graphics2D g2, String text, Font font,
                                        int screenWidth, int screenHeight) {
        int panelH = 150;
        int margin = 16;
        int x = margin;
        int y = screenHeight - panelH - 8;
        int w = screenWidth - margin * 2;

        g2.setPaint(new GradientPaint(0, y, new Color(18, 28, 40, 235),
                                      0, y + panelH, new Color(8, 14, 22, 235)));
        g2.fillRoundRect(x, y, w, panelH, 22, 22);

        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, w, panelH, 22, 22);
        g2.setColor(new Color(140, 180, 220, 70));
        g2.drawRoundRect(x + 3, y + 3, w - 6, panelH - 6, 18, 18);
        g2.setStroke(prev);

        if (font != null) g2.setFont(font);
        g2.setColor(new Color(245, 250, 255));
        g2.drawString(text, x + 32, y + 64);
    }
}
