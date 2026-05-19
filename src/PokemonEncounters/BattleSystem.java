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
import Pokemon.GetPokemon;
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
    // Targets the enemy sprite's *feet* — most front sprites have transparent padding at
    // the bottom of their bounding box, so the visual feet sit ~80% down the box rather
    // than at the literal bottom edge.
    private static final int BALL_START_X = 140, BALL_START_Y = 600;
    private static final int BALL_LAND_X  = 665, BALL_LAND_Y  = 340;
    private static final int BALL_DRAW_SIZE = 56;
    private static final double THROW_ARC_HEIGHT = 240.0;
    private static final double WOBBLE_MAX_TILT = 0.40; // radians (~23 degrees)

    // Switch-throw animation: reuses the trainer poses (encounterAssets 10..13) so the
    // player visually throws a ball when sending the new pokemon out, matching the
    // initial-encounter send-out.

    private enum Phase { ENTRY, CHOOSE_ACTION, CHOOSE_MOVE, MESSAGE, BALL_THROW, FAINT_ANIM, FINISHED }

    private final GamePanel gp;
    private final KeyHandler keyH;

    private Phase phase = Phase.ENTRY;

    // Edge-detection: a key only counts as "just pressed" on the frame it goes
    // from up to down. Without this, holding Z to fight would auto-select move 1.
    private boolean prevEsc;
    private boolean justEsc;
    // Arrow-key + Enter edges for the action / move menu cursors.
    private boolean prevUp, prevDown, prevLeft, prevRight, prevEnter;
    private boolean justUp, justDown, justLeft, justRight, justEnter;
    // 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right. Persisted across
    // CHOOSE_ACTION ↔ CHOOSE_MOVE so the cursor stays where it was last frame.
    private int actionCursor;
    private int moveCursor;

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
    // Greyscale variant of the pokeball used in the boss roster strip to indicate a
    // fainted member. Lazily created from ballImage on construction.
    private BufferedImage ballImageGrey;

    // Switch state. While true, PokemonEncounter skips drawing the player sprite so the
    // recall/throw animation reads as "the previous pokemon goes back, then the new one
    // emerges" without a hard cut between sprites mid-message.
    private boolean switchHidden;

    // Boss mid-battle send-out: while true, the enemy sprite is hidden and a static
    // pokeball is drawn at the enemy position (matches the pre-encounter boss throw).
    private boolean bossBallShowing;

    // Player mid-battle send-out (trainer battles only): static pokeball at the player
    // slot while "Go! X!" plays during a voluntary switch. Mirrors bossBallShowing on
    // the player side. Wild encounters keep the simpler ball-less switch.
    private boolean playerBallShowing;

    public BattleSystem(GamePanel gp, KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;
        try {
            this.ballImage = ImageIO.read(new File("./src/res/objects/pokeball.png"));
            this.ballImageGrey = greyscale(this.ballImage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Convert a color image into a desaturated, slightly dimmed grey version. Used to
    // render fainted-pokemon pokeballs in the boss roster strip (matches mainline games).
    private static BufferedImage greyscale(BufferedImage src) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgba = src.getRGB(x, y);
                int a = (rgba >> 24) & 0xff;
                int r = (rgba >> 16) & 0xff;
                int g = (rgba >>  8) & 0xff;
                int b =  rgba        & 0xff;
                int grey = (int) ((r + g + b) / 3 * 0.72); // dim a hair so it reads as "off"
                if (grey > 255) grey = 255;
                out.setRGB(x, y, (a << 24) | (grey << 16) | (grey << 8) | grey);
            }
        }
        return out;
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
        playerBallShowing = false;
        // Boss battles open with the boss-throw beat — hide the enemy from frame 1 so
        // the first draw doesn't flash the pokemon before the ENTRY transition fires.
        bossBallShowing = gp.isBossBattle;
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
        prevEsc = keyH.escPressed;
        prevUp    = keyH.upPressed;
        prevDown  = keyH.downPressed;
        prevLeft  = keyH.leftPressed;
        prevRight = keyH.rightPressed;
        prevEnter = keyH.enterPressed;
        actionCursor = 0;
        moveCursor = 0;
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
    public boolean enemyHiddenByBall() { return enemyHidden || bossBallShowing; }

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
    public boolean playerHiddenForSwitch() { return switchHidden || playerBallShowing; }

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
            // Boss battles open with the boss's send-out beat (ball + "???? sent out X!").
            // Wild encounters jump straight to the action menu.
            if (gp.isBossBattle) {
                initBossSendOut();
            } else {
                phase = Phase.CHOOSE_ACTION;
            }
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
        justEsc   = keyH.escPressed   && !prevEsc;
        justUp    = keyH.upPressed    && !prevUp;
        justDown  = keyH.downPressed  && !prevDown;
        justLeft  = keyH.leftPressed  && !prevLeft;
        justRight = keyH.rightPressed && !prevRight;
        justEnter = keyH.enterPressed && !prevEnter;
        prevEsc   = keyH.escPressed;
        prevUp    = keyH.upPressed;
        prevDown  = keyH.downPressed;
        prevLeft  = keyH.leftPressed;
        prevRight = keyH.rightPressed;
        prevEnter = keyH.enterPressed;
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
        // 2x2 cursor: 0=FIGHT, 1=CATCH, 2=POKEMON, 3=RUN.
        if (justUp    && actionCursor >= 2)             actionCursor -= 2;
        if (justDown  && actionCursor < 2)              actionCursor += 2;
        if (justLeft  && (actionCursor & 1) == 1)       actionCursor -= 1;
        if (justRight && (actionCursor & 1) == 0)       actionCursor += 1;
        if (!justEnter) return;
        switch (actionCursor) {
            case 0: // FIGHT
                phase = Phase.CHOOSE_MOVE;
                break;
            case 1: // CATCH — disabled in trainer battles
                if (gp.isBossBattle) {
                    queue("You can't catch a trainer's Pokemon!", () -> phase = Phase.CHOOSE_ACTION);
                } else {
                    startBallThrow();
                }
                break;
            case 2: // POKEMON — open switch menu; no-op if no other teammate can fight
                if (firstLiveExcept(activeIndex) < 0) {
                    queue("No other Pokemon can fight!", () -> phase = Phase.CHOOSE_ACTION);
                } else {
                    openSwitchMenu(true);
                }
                break;
            case 3: // RUN — disabled in trainer battles
                if (gp.isBossBattle) {
                    queue("You can't run from a trainer battle!", () -> phase = Phase.CHOOSE_ACTION);
                } else {
                    queue("You got away safely!", () -> phase = Phase.FINISHED);
                }
                break;
        }
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
        // Voluntary switches hide the player sprite while the "Come back" line plays, then
        // reveal the new pokemon when the "Go!" line starts. Forced switches (post-faint)
        // just send the new one out — the previous already fainted into the ground.
        if (forced) {
            // Post-faint send-out: same ball + SE beat as a voluntary trainer-battle
            // switch. Sprite stays hidden via playerBallShowing until the message drains.
            switchHidden = false;
            playerBallShowing = true;
            queue("Go! " + nw.name + "!", () -> {
                playerBallShowing = false;
                gp.playSE(0); // pokeball open SFX as the pokemon emerges
                phase = Phase.CHOOSE_ACTION;
            });
        } else if (gp.isBossBattle) {
            // Trainer battles: throw a pokeball into the player slot before the new mon
            // appears, mirroring the boss's mid-battle send-out beat. SE fires when the
            // "Go! Y!" message drains so the click syncs with the pokemon emerging.
            switchHidden = true;
            queue("Come back, " + old.name + "!", () -> {
                playerBallShowing = true;
                queue("Go! " + nw.name + "!", () -> {
                    playerBallShowing = false;
                    switchHidden = false;
                    gp.playSE(0); // pokeball open SFX as the pokemon emerges
                    doEnemyAttack(enemyMove);
                    then(this::afterEnemyAttack);
                });
            });
        } else {
            switchHidden = true;
            queue("Come back, " + old.name + "!", () -> {
                gp.playSE(0); // pokeball recall click
                switchHidden = false;
                queue("Go! " + nw.name + "!", () -> {
                    gp.playSE(0); // pokeball open SFX as the new pokemon emerges
                    doEnemyAttack(enemyMove);
                    then(this::afterEnemyAttack);
                });
            });
        }
    }

    // After the enemy's faint animation, award EXP to every non-fainted party member
    // (Gen 6+ Exp Share — everyone gets a per-receiver scaled amount, 2x..5x of base).
    // Hard-caps the level at 100. Each level-up that crosses an evolution threshold
    // chains an evolution and queues a "X evolved into Y!" line in the dialog.
    private void giveExpAndFinish() {
        Pokemon active = player();
        java.util.List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        java.util.List<String> followLines = new java.util.ArrayList<>();
        int sharedCount = 0;
        int activeGain = 0;
        for (Pokemon p : party) {
            if (p == null || p.currentHP <= 0) continue;
            if (p.level >= 100) continue;
            int gain = ExpCurves.expGained(enemy().expGiven, enemy().level, p.level);
            if (p == active) activeGain = gain;
            p.totalExp += gain;
            int curveMax = p.experienceGrowth;
            if (p.totalExp > curveMax) p.totalExp = curveMax;
            int origLevel = p.level;
            while (p.level < 100
                    && p.totalExp >= ExpCurves.expAtLevel(p.level + 1, curveMax)) {
                p.level++;
            }
            if (p.level > origLevel) {
                p.recalcStats();
                followLines.add(p.name + " grew to Lv. " + p.level + "!");
                // Chain evolutions: if the new level crosses multiple evolution thresholds
                // (rare, but possible with big XP jumps), evolve all the way through.
                while (p.evolveLevel > 0 && p.level >= p.evolveLevel
                        && p.evolvesInto != null && !p.evolvesInto.isEmpty()) {
                    String oldName = p.name;
                    if (!evolvePokemon(p)) break;
                    followLines.add(oldName + " evolved into " + p.name + "!");
                }
            }
            sharedCount++;
        }
        if (active != null) displayedPlayerHP = active.currentHP;

        // In a boss battle, the encounter only ends after every roster member is down.
        // Otherwise we send out the boss's next pokemon and resume CHOOSE_ACTION.
        boolean bossHasMore = gp.isBossBattle
                && gp.bossQueue != null
                && gp.bossIndex + 1 < gp.bossQueue.size();

        Runnable finish;
        if (bossHasMore) {
            finish = this::sendNextBossPokemon;
        } else if (gp.isBossBattle) {
            // Boss's last pokemon just fainted — multi-line defeat speech, then FINISHED.
            finish = () -> {
                queue(object.OBJ_Boss.DISPLAY_NAME + " was defeated!", null);
                queue("...impossible.", null);
                queue("How could I lose... to myself?", null);
                queue("We will meet again, in another life.",
                      () -> phase = Phase.FINISHED);
            };
        } else {
            finish = () -> phase = Phase.FINISHED;
        }

        if (sharedCount == 0) {
            finish.run();
            return;
        }
        String activeName = (active != null) ? active.name : "Your Pokemon";
        int displayGain = activeGain > 0 ? activeGain : ExpCurves.expGained(enemy().expGiven, enemy().level, 50);
        String summary = (sharedCount > 1)
            ? activeName + " and your party gained " + displayGain + " EXP. Points!"
            : activeName + " gained " + displayGain + " EXP. Points!";
        queue(summary, followLines.isEmpty() ? finish : null);
        for (int i = 0; i < followLines.size(); i++) {
            boolean last = (i == followLines.size() - 1);
            queue(followLines.get(i), last ? finish : null);
        }
    }

    // Mid-battle: swap in the next pokemon from the boss's roster.
    private void sendNextBossPokemon() {
        gp.bossIndex++;
        gp.wildPokemon = gp.bossQueue.get(gp.bossIndex);
        initBossSendOut();
    }

    // Boss-throw beat for whichever pokemon is currently at gp.wildPokemon. Pokeball is
    // visible (and the pokemon hidden) while the "???? sent out X!" line plays; on drain
    // the ball clears, the SE plays, and we hand control to CHOOSE_ACTION.
    private void initBossSendOut() {
        Pokemon e = enemy();
        displayedEnemyHP = e.currentHP;
        enemyHidden = false;
        enemyFainting = false;
        faintFrame = 0;
        bossBallShowing = true;
        queue(object.OBJ_Boss.DISPLAY_NAME + " sent out " + e.name + "!", () -> {
            bossBallShowing = false;
            gp.playSE(0); // pokeball open SFX as the pokemon emerges
            phase = Phase.CHOOSE_ACTION;
        });
    }

    // In-place evolve: replace `p`'s species data with the evolved form (looked up by
    // name via GetPokemon) and recompute stats. Keeps currentHP, totalExp, level, and
    // IVs; only the species-derived fields change (base stats, types, moves, sprite key,
    // capture rate, XP yield, next evolution).
    private boolean evolvePokemon(Pokemon p) {
        Pokemon evolved = new GetPokemon().findPokemon(p.evolvesInto, p.level);
        if (evolved == null) return false;
        p.name = evolved.name;
        p.NameInFile = evolved.NameInFile;
        p.NameInFileGif = evolved.NameInFileGif;
        p.pokedexNumber = evolved.pokedexNumber;
        p.type1 = evolved.type1;
        p.type2 = evolved.type2;
        p.currentType1 = evolved.type1;
        p.currentType2 = evolved.type2;
        p.baseHP        = evolved.baseHP;
        p.baseAttack    = evolved.baseAttack;
        p.baseDefense   = evolved.baseDefense;
        p.baseSpAttack  = evolved.baseSpAttack;
        p.baseSpDef     = evolved.baseSpDef;
        p.baseSpeed     = evolved.baseSpeed;
        p.captureRate   = evolved.captureRate;
        p.experienceGrowth = evolved.experienceGrowth;
        p.expGiven      = evolved.expGiven;
        p.evolvesInto   = evolved.evolvesInto;
        p.evolveLevel   = evolved.evolveLevel;
        // Keep p.moves as-is so player customizations survive evolution. If the evolved
        // form gains a new type, the player can pick up moves of that type at the tutor.
        p.recalcStats();
        return true;
    }

    private void handleMoveInput() {
        // Escape backs out of move selection — un-commits the FIGHT choice, returns to
        // the action menu (FIGHT / CATCH / POKEMON / RUN).
        if (justEsc) { phase = Phase.CHOOSE_ACTION; return; }
        // 2x2 cursor over move slots 0..3. Enter commits.
        if (justUp    && moveCursor >= 2)             moveCursor -= 2;
        if (justDown  && moveCursor < 2)              moveCursor += 2;
        if (justLeft  && (moveCursor & 1) == 1)       moveCursor -= 1;
        if (justRight && (moveCursor & 1) == 0)       moveCursor += 1;
        if (!justEnter) return;
        Move chosen = moveAt(moveCursor);
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
        // Smart AI: skip moves the player is immune to (0x effectiveness) so an Electric
        // enemy facing a Ground player doesn't keep picking dead Electric moves. Every
        // pokemon's guaranteed Normal slot means there's almost always a non-immune
        // option except against a Ghost defender. Falls back to a random pick if every
        // known move happens to be a no-op.
        Pokemon defender = player();
        java.util.List<Move> usable = new java.util.ArrayList<>(enemy.moves.size());
        for (Move m : enemy.moves) {
            if (m == null) continue;
            double eff = TypeChart.effectiveness(m.type, defender.currentType1, defender.currentType2);
            if (eff > 0) usable.add(m);
        }
        if (usable.isEmpty()) return enemy.moves.get(RNG.nextInt(enemy.moves.size()));
        return usable.get(RNG.nextInt(usable.size()));
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

    // Ball bonus scales with progression: starts at normal-ball strength (1.0x) and
    // ramps linearly to ultra-ball strength (2.0x) as the player's best pokemon reaches
    // Lv 100. Highest is computed across party + PC so the bonus survives stashing your
    // strongest mon in the box.
    private static final double NORMAL_BALL_BONUS = 1.0;
    private static final double ULTRA_BALL_BONUS  = 2.0;

    private double currentBallBonus() {
        int highest = Math.max(1, Math.min(100, gp.playerPokemon.highestLevel()));
        double t = (highest - 1) / 99.0; // 0 at Lv 1, 1 at Lv 100
        return NORMAL_BALL_BONUS + t * (ULTRA_BALL_BONUS - NORMAL_BALL_BONUS);
    }

    // Gen 5+ catch formula. HP scaling is built into the (3*HPmax - 2*HPcur) term, so a
    // weakened pokemon is easier to catch. No status conditions in this game, so statusBonus = 1.
    //   a = ((3*HPmax - 2*HPcur) * captureRate * ballBonus) / (3*HPmax)
    //   b = 65536 / (255/a)^(3/16)
    // Then roll four 16-bit ints; each one under b is a successful "shake". 4 in a row = caught.
    private int rollCatch(Pokemon e) {
        int hpMax = Math.max(1, e.maxHP);
        int hpCur = Math.max(1, e.currentHP);
        int cr = Math.max(1, e.captureRate);
        double a = ((3.0 * hpMax - 2.0 * hpCur) * cr * currentBallBonus()) / (3.0 * hpMax);
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
            // Catches award no EXP — the player gets the pokemon itself as the reward,
            // not a free level on the active mon (or party via Exp Share).
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
        // Boss-only: row of pokeballs showing how many roster members remain.
        if (gp.isBossBattle && gp.bossQueue != null) drawBossPokeballs(g2, smallFont);

        if (phase == Phase.CHOOSE_ACTION) {
            drawActionMenu(g2, encounterAssets, bigFont);
        } else if (phase == Phase.CHOOSE_MOVE) {
            drawMoveMenu(g2, encounterAssets, smallFont);
        } else if ((phase == Phase.MESSAGE || phase == Phase.FAINT_ANIM || phase == Phase.FINISHED)
                   && !currentMessage.isEmpty()) {
            drawDialogPanel(g2, currentMessage, bigFont, gp.screenWidth, gp.screenHeight);
        }
        // The ball overlays everything else (incl. the dialog box on "Gotcha!") whenever it's in play.
        drawBall(g2);
        // Boss send-out mid-battle: pokeball only at the enemy slot (no boss portrait).
        // Mirrors PokemonEncounter.drawBossSendOutBall for the initial send-out so every
        // send-out (initial + each subsequent) reads the same.
        if (bossBallShowing && ballImage != null) {
            int size = 80;
            int cx = PokemonEncounter.ENEMY_X + PokemonEncounter.ENEMY_W / 2;
            int cy = PokemonEncounter.ENEMY_Y + PokemonEncounter.ENEMY_H / 2;
            g2.drawImage(ballImage, cx - size / 2, cy - size / 2, size, size, null);
        }
        // Player switch send-out (trainer battles): same static-ball treatment as the
        // boss side so the player throw reads symmetrically.
        if (playerBallShowing && ballImage != null) {
            int size = 80;
            int cx = PokemonEncounter.PLAYER_X + PokemonEncounter.PLAYER_W / 2;
            int cy = PokemonEncounter.PLAYER_Y + PokemonEncounter.PLAYER_H / 2;
            g2.drawImage(ballImage, cx - size / 2, cy - size / 2, size, size, null);
        }
    }

    // Pokeball roster strip drawn above the enemy HP panel during boss battles.
    // Red = still alive, grey = fainted. Slot order mirrors gp.bossQueue.
    private void drawBossPokeballs(Graphics2D g2, Font font) {
        java.util.List<Pokemon> roster = gp.bossQueue;
        if (roster == null || roster.isEmpty()) return;
        int n = roster.size();
        int ballSize = 22;
        int gap = 6;
        int padX = 12, padY = 4;
        int barW = n * ballSize + (n - 1) * gap + padX * 2;
        int barH = ballSize + padY * 2;
        int x = 32;
        int y = 4;
        // Background plate (matches the dark panel style elsewhere).
        g2.setPaint(new java.awt.GradientPaint(0, y, new Color(18, 28, 40, 230),
                                                0, y + barH, new Color(8, 14, 22, 230)));
        g2.fillRoundRect(x, y, barW, barH, 12, 12);
        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(90, 130, 170, 200));
        g2.drawRoundRect(x, y, barW, barH, 12, 12);
        g2.setStroke(prev);

        for (int i = 0; i < n; i++) {
            Pokemon p = roster.get(i);
            boolean alive = p != null && p.currentHP > 0;
            BufferedImage img = alive ? ballImage : ballImageGrey;
            int bx = x + padX + i * (ballSize + gap);
            int by = y + padY;
            if (img != null) {
                g2.drawImage(img, bx, by, ballSize, ballSize, null);
            }
        }
    }

    // ---- Custom HP panels (static so PokemonEncounter can call them pre-battle too). ----

    // Enemy panel: top-left, name + gender + Lv + HP bar (no number text — matches mainline games).
    public static void drawEnemyPanel(Graphics2D g2, Pokemon enemy, double displayedHp, Font font) {
        if (enemy == null) return;
        int x = 32, y = 32, w = 370, h = 92;
        drawPanelBackground(g2, x, y, w, h);
        if (font != null) g2.setFont(font);
        g2.setColor(Color.white);
        g2.drawString(enemy.name, x + 20, y + 32);
        int nameW = g2.getFontMetrics().stringWidth(enemy.name);
        drawGender(g2, enemy.gender, x + 20 + nameW + 8, y + 32);
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
        int nameW = g2.getFontMetrics().stringWidth(p.name);
        drawGender(g2, p.gender, x + 18 + nameW + 8, y + 26);
        String lv = "Lv " + p.level;
        int lvW = g2.getFontMetrics().stringWidth(lv);
        g2.setColor(new Color(220, 230, 240));
        g2.drawString(lv, x + w - lvW - 16, y + 26);
        drawHpRow(g2, displayedHp, p.maxHP, x + 18, y + 46, w - 36, 11, font, true);
        drawExpRow(g2, p, x + 18, y + h - 14, w - 36, 6, font);
    }

    // Small gender glyph next to the name. Drawn in the system Dialog font so the ♂/♀
    // characters render even if MaruMonica's glyph set doesn't cover them. No-op for
    // genderless pokemon.
    public static void drawGender(Graphics2D g2, String gender, int x, int y) {
        if (gender == null) return;
        String sym;
        Color col;
        if ("Male".equalsIgnoreCase(gender))        { sym = "♂"; col = new Color(110, 180, 255); }
        else if ("Female".equalsIgnoreCase(gender)) { sym = "♀"; col = new Color(255, 130, 180); }
        else return;
        Font prev = g2.getFont();
        int pt = prev != null ? prev.getSize() : 22;
        g2.setFont(new Font(Font.DIALOG, Font.BOLD, pt));
        g2.setColor(col);
        g2.drawString(sym, x, y);
        if (prev != null) g2.setFont(prev);
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

        // 2x2 button grid on the right half. Order matches actionCursor indices
        // (0=top-left FIGHT, 1=top-right CATCH, 2=bottom-left POKEMON, 3=bottom-right RUN).
        String[] labels = { "FIGHT", "CATCH", "POKEMON", "RUN" };
        int gridX = x + w / 2 + 8;
        int gridY = y + 18;
        int gridW = (x + w) - gridX - 18;
        int gridH = panelH - 36;
        int gap = 10;
        int cellW = (gridW - gap) / 2;
        int cellH = (gridH - gap) / 2;
        for (int i = 0; i < 4; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = gridX + col * (cellW + gap);
            int by = gridY + row * (cellH + gap);
            boolean focused = (i == actionCursor);
            g2.setColor(new Color(focused ? 70 : 40, focused ? 90 : 60, focused ? 130 : 90, 230));
            g2.fillRoundRect(bx, by, cellW, cellH, 14, 14);
            g2.setStroke(new BasicStroke(focused ? 3f : 1.5f));
            g2.setColor(focused ? new Color(255, 220, 60) : new Color(110, 150, 190, 230));
            g2.drawRoundRect(bx, by, cellW, cellH, 14, 14);
            String label = labels[i];
            int textW = g2.getFontMetrics().stringWidth(label);
            int tx = bx + (cellW - textW) / 2;
            int ty = by + cellH / 2 + 10;
            g2.setColor(Color.white);
            g2.drawString(label, tx, ty);
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
            boolean focused = (i == moveCursor) && has;
            g2.setColor(new Color(focused ? 70 : 40, focused ? 90 : 60, focused ? 130 : 90,
                                  has ? 230 : 130));
            g2.fillRoundRect(bx, by, cellW, cellH, 14, 14);
            g2.setStroke(new BasicStroke(focused ? 3f : 1.5f));
            g2.setColor(focused ? new Color(255, 220, 60)
                                : new Color(110, 150, 190, has ? 230 : 140));
            g2.drawRoundRect(bx, by, cellW, cellH, 14, 14);
            String label = has ? moves.get(i).name : "—";
            int textW = g2.getFontMetrics().stringWidth(label);
            int tx = bx + (cellW - textW) / 2;
            int ty = by + cellH / 2 + 10;
            g2.setColor(has ? Color.white : new Color(160, 170, 180));
            g2.drawString(label, tx, ty);
        }
        // Hint strip
        g2.setColor(new Color(180, 195, 210));
        String hint = "Arrows navigate   Enter select   ESC back";
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
