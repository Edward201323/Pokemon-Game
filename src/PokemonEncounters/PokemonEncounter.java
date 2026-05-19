package PokemonEncounters;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import Animations.Animations;
import Main.GamePanel;
import Main.KeyHandler;
import Pokemon.GetPokemonImages;
import Pokemon.Pokemon;
public class PokemonEncounter {
    // Shared positions for the battle arena, sized for the full-screen background.
    // Player pokemon sits lower-left, enemy upper-right, trainer overlaps the player's
    // spot (so the throw animation lines up with where the new pokemon emerges).
    public static final int PLAYER_X = 35,  PLAYER_Y = 260, PLAYER_W = 360, PLAYER_H = 360;
    public static final int ENEMY_X  = 550, ENEMY_Y  = 185,  ENEMY_W  = 230, ENEMY_H  = 230;
    public static final int TRAINER_X = 70, TRAINER_Y = 270, TRAINER_W = 260, TRAINER_H = 260;

    GamePanel gp;
    KeyHandler keyH;
 
    Graphics2D g2;
 
    Font MaruMonica, MaruMonicaSmall;
 
    BufferedImage[] encounterBackgrounds;
 
    BufferedImage[] encounterAssets;
 
    GetPokemonImages getPokemonImages;
 
    Animations animations;
 
    int counterA = 0;
    int counterB = 0;
    int counterC = 0;
 
    int animationFrame = 1;
    int blinkCount = 0;
 
    boolean ballThrown = false;
    boolean endEncounter = false;

    boolean chooseAction = false;

    BattleSystem battle;
    boolean battleStarted = false;

    public PokemonEncounter(GamePanel gp, KeyHandler keyH){
        this.gp = gp;
        this.keyH = keyH;
        this.animations = new Animations(this.gp);
        this.getPokemonImages = new GetPokemonImages();

        getFont();

        this.encounterBackgrounds = new BufferedImage[15];
        getEncounterBackgrounds();

        this.encounterAssets = new BufferedImage[15];
        getEncounterAssets();

        this.battle = new BattleSystem(gp, keyH);
    }
 
 
    public void draw(Graphics2D g2){
        this.g2 = g2;
        if(gp.gameState == gp.pokemonTransition){
            drawPokemonTransition();
        }
        if(gp.gameState == gp.beforePokemonEncounter){
            drawBeforePokemonEncounter();
        }
        if(gp.gameState == gp.pokemonEncounter){
            drawPokemonEncounter();
        }
    }
 
 
    private void drawPokemonTransition(){
        counterA++;
        if(counterA<=13){
            if(animationFrame == 1){
                g2.drawImage(encounterAssets[7], 0, 0, gp.screenWidth, gp.screenHeight, null);
            }
            else if(animationFrame == 2){
                g2.drawImage(encounterAssets[8], 0, 0, gp.screenWidth, gp.screenHeight, null);
            }
            else if(animationFrame == 3){
 
            }
        }
        if(counterA>=13){
            if(animationFrame == 1){
                animationFrame = 2;
            }
            else if(animationFrame == 2){
                animationFrame = 3;
            }
            else if(animationFrame == 3){
               animationFrame = 1;
            }
            counterA = 0;
            blinkCount++;
            if(blinkCount == 7){
                animationFrame = 1;
                blinkCount = 0;
                gp.gameState = gp.beforePokemonEncounter;
            }
        }
    }
 
 
 
    public void drawBeforePokemonEncounter(){
        // Full-screen background so the arena isn't squashed into the top 497px — the
        // custom dialog panel draws over the bottom area cleanly without needing space reserved.
        g2.drawImage(encounterBackgrounds[12], 0, 0, gp.screenWidth, gp.screenHeight, null);

        // For boss battles the boss sends out *after* the player (handled inside the
        // encounter by BattleSystem). The enemy slot stays empty for the entire
        // pre-encounter so the player throws first into an empty field.
        if (!gp.isBossBattle) {
            g2.drawImage(getPokemonImages.getPokemonFront(gp.wildPokemon),
                         ENEMY_X, ENEMY_Y, ENEMY_W, ENEMY_H, null);
        }

        drawBeforeEncounterAssets();
    }
 
 
    public void drawBeforeEncounterAssets() {
        counterB++;

        // Draw Dawn until she throws (player throw runs the entire pre-encounter window
        // for boss battles; standard wild encounters precede her with the "appeared" line).
        if (ballThrown == false) {
            g2.drawImage(encounterAssets[9], TRAINER_X, TRAINER_Y, TRAINER_W, TRAINER_H, null);
        }

        // Custom enemy HP panel — same look the battle uses, just driven with the wild's
        // current HP directly (no animated easing yet, since the battle hasn't started).
        BattleSystem.drawEnemyPanel(g2, gp.wildPokemon, gp.wildPokemon.currentHP, MaruMonicaSmall);

        // Lead is the first non-fainted party member — skipping a fainted slot-0 here
        // avoids the awkward "Go! Charizard! [forced switch]" beat at battle start.
        // Falls back to slot 0 only if every member is fainted (which the blackout flow
        // should normally have intercepted before we got here).
        Pokemon leadForSendOut = pickLeadForSendOut();

        if (gp.isBossBattle) {
            // Boss flow: *player* sends out first into an empty enemy slot. After the
            // encounter starts, BattleSystem handles the boss's send-out beat (ball +
            // "???? sent out X!" + reveal).
            drawDawnThrowBall();
            ballThrown = true;
            String name = leadForSendOut == null ? "" : leadForSendOut.name;
            BattleSystem.drawDialogPanel(g2, "Go! " + name + "!", MaruMonica,
                                          gp.screenWidth, gp.screenHeight);
        } else {
            // Wild flow: "A wild X appeared!" then player throw + "Go! Y!".
            int introEndFrame = 150;
            if (counterB <= introEndFrame) {
                BattleSystem.drawDialogPanel(g2, "A wild " + gp.wildPokemon.name + " appeared!",
                                              MaruMonica, gp.screenWidth, gp.screenHeight);
            } else {
                drawDawnThrowBall();
                ballThrown = true;
                String name = leadForSendOut == null ? "" : leadForSendOut.name;
                BattleSystem.drawDialogPanel(g2, "Go! " + name + "!", MaruMonica,
                                              gp.screenWidth, gp.screenHeight);
            }
        }


        //Draw fade animation
        animations.FadeOut(g2);
    }

    private void drawDawnThrowBall(){
        counterA++;
        counterC++;
        if(counterA<=11){
            if(animationFrame == 1){
                g2.drawImage(encounterAssets[10], TRAINER_X, TRAINER_Y, TRAINER_W, TRAINER_H, null);
            } else if (animationFrame == 2){
                g2.drawImage(encounterAssets[11], TRAINER_X, TRAINER_Y, TRAINER_W, TRAINER_H, null);
            } else if (animationFrame == 3){
                g2.drawImage(encounterAssets[12], TRAINER_X, TRAINER_Y, TRAINER_W, TRAINER_H, null);
            } else if (animationFrame == 4){
                g2.drawImage(encounterAssets[13], TRAINER_X, TRAINER_Y, TRAINER_W, TRAINER_H, null);
            } else if (animationFrame == 5){
                Pokemon lead = pickLeadForSendOut();
                g2.drawImage(getPokemonImages.getPokemonBack(lead),
                             PLAYER_X, PLAYER_Y, PLAYER_W, PLAYER_H, null);
            }
        }
        if(counterA>=11){
            if(animationFrame == 1){
                animationFrame = 2;
            } else if (animationFrame == 2){
                gp.playSE(0);
                animationFrame = 3;
            } else if (animationFrame == 3){
                animationFrame = 4;
            } else if (animationFrame == 4){
                animationFrame = 5;
            } else if(animationFrame == 5){
                if(counterC>100){
                    gp.gameState = gp.pokemonEncounter;
 
                    chooseAction = true;
 
                    animationFrame = 1;
 
                    counterA = 0;
                    counterB = 0;
                    counterC = 0;
                }
            }
            counterA = 0;
        }
    }
 
 
 
 
    private void drawPokemonEncounter(){
        // Full-screen background so the arena uses the whole viewport — the custom dialog
        // and HP panels draw on top with their own translucent backgrounds.
        g2.drawImage(encounterBackgrounds[12], 0, 0, gp.screenWidth, gp.screenHeight, null);

        // Skip the enemy sprite once a thrown ball is sitting on it, so the ball reads as containing the wild Pokemon.
        if (!battle.enemyHiddenByBall()) {
            drawPokemonSprite(getPokemonImages.getPokemonFront(gp.wildPokemon),
                              ENEMY_X, ENEMY_Y, ENEMY_W, ENEMY_H, battle.enemyFaintFraction());
        }
        // Draw whichever party member is actually active (not always slot 0), and skip the
        // sprite during a switch so the recall/throw dialog doesn't show the old pokemon.
        Pokemon active = battle.activePokemon();
        if (active != null && !battle.playerHiddenForSwitch()) {
            drawPokemonSprite(getPokemonImages.getPokemonBack(active),
                              PLAYER_X, PLAYER_Y, PLAYER_W, PLAYER_H, battle.playerFaintFraction());
        }

        drawEncounterAssets();
    }

    // While a Pokemon is fainting, translate it downward by `fraction * height` and clip to its
    // original rect so it appears to sink into the ground.
    private void drawPokemonSprite(BufferedImage img, int x, int y, int w, int h, double fraction){
        if (fraction <= 0){
            g2.drawImage(img, x, y, w, h, null);
            return;
        }
        java.awt.Shape oldClip = g2.getClip();
        g2.setClip(x, y, w, h);
        int dy = (int) Math.round(fraction * h);
        g2.drawImage(img, x, y + dy, w, h, null);
        g2.setClip(oldClip);
    }
 
 
    private void drawEncounterAssets() {
        counterA++;
        // HP panels, dialog, and action menu are all custom-drawn by BattleSystem now.
        // counterA still gates EncounterText so the brief pre-battle pause is preserved.
        if (counterA * 71 >= 497) {
            EncounterText();
        }
    }
 
 
    private void EncounterText(){
        if(chooseAction == true){
            if(!battleStarted){
                battle.start();
                battleStarted = true;
            }
            battle.update();
            battle.draw(g2, encounterAssets, MaruMonica, MaruMonicaSmall);
            if(battle.isFinished()){
                chooseAction = false;
                endEncounter = true;
                counterB = 0;
            }
        }

        if(endEncounter == true){
            // Keep the battle's final HP bars + closing message on screen while we count to the fade.
            battle.draw(g2, encounterAssets, MaruMonica, MaruMonicaSmall);
            counterB++;
            if(counterB >= 20){
                drawBeforeEncounterEnd();
            }
        }
    }
 
    private void drawBeforeEncounterEnd(){
        counterC++;
        animations.FadeIn(g2);
        if(counterC == 50){
            endEncounter();
        }
    }
 
 
    private void endEncounter(){
        gp.stopMusic();
        // On a normal end, return to overworld music immediately. On a blackout, leave
        // the music silent — Blackout will resume it once the party has been healed.
        if (!gp.playerPokemon.isAllFainted()) {
            gp.resumeMusic(gp.overworldMusicIndex());
        }
        // If this was a boss battle and the player survived, the boss is defeated — remove
        // it from the map so it can't be rebattled. On a loss the boss stays (you can try
        // again after the blackout heal).
        if (gp.isBossBattle && !gp.playerPokemon.isAllFainted() && gp.currentBoss != null) {
            gp.obj.remove(gp.currentBoss);
            // Boss reward: pretend the player "caught" a max-level Arceus. Lands in the
            // party if there's a slot, otherwise the PC — same routing as a normal catch.
            gp.playerPokemon.addPokemon("Arceus", 100);
        }
        gp.isBossBattle = false;
        gp.bossQueue = null;
        gp.bossIndex = 0;
        gp.currentBoss = null;

        resetPokemonEquippedStats();


        //Reset variables
        ballThrown = false;
        endEncounter = false;
        chooseAction = false;
        battleStarted = false;

        counterA = 0;
        counterB = 0;
        counterC = 0;

        animations.counterA = 0;
        animations.counterB = 0;

        // Blackout if every party member fainted; otherwise return to play. Healing now
        // happens at the Pokemon Center (or implicitly via the blackout teleport).
        if (gp.playerPokemon.isAllFainted()) {
            gp.blackout.trigger();
        } else {
            gp.gameState = gp.playState;
        }
    }
 
 
 
    private void getFont() {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, new File("./src/res/Font/MaruMonica.ttf"));
            MaruMonica = base.deriveFont(Font.BOLD, 35f);
            MaruMonicaSmall = base.deriveFont(Font.BOLD, 30f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

 
    private void getEncounterBackgrounds(){
        loadIndexedImages("./src/res/EncounterBackgrounds", encounterBackgrounds);
    }

    private void getEncounterAssets(){
        loadIndexedImages("./src/res/EncounterAssets", encounterAssets);
    }

    // Loads files named "<index>_<label>.png" into the given slot array.
    public static void loadIndexedImages(String dirPath, BufferedImage[] dest) {
        File directory = new File(dirPath);
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.startsWith(".")) continue;
            int underscore = fileName.indexOf('_');
            if (underscore < 0) continue;
            int index;
            try {
                index = Integer.parseInt(fileName.substring(0, underscore));
            } catch (NumberFormatException e) {
                continue;
            }
            if (index < 0 || index >= dest.length) continue;
            try {
                dest[index] = ImageIO.read(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
 
 
    // First non-fainted party member, or slot 0 as a fallback. Used for the pre-encounter
    // "Go! X!" beat so a fainted slot-0 doesn't get thrown out just to be switched immediately.
    private Pokemon pickLeadForSendOut() {
        java.util.List<Pokemon> party = gp.playerPokemon.pokemonEquipped;
        if (party.isEmpty()) return null;
        for (Pokemon p : party) {
            if (p != null && p.currentHP > 0) return p;
        }
        return party.get(0);
    }

    //Use whens switch pokemon, run, etc.
    private void resetPokemonEquippedStats(){
        for(Pokemon pokemon: gp.playerPokemon.pokemonEquipped){
            pokemon.currentAttack = pokemon.attack;
            pokemon.currentSpAttack = pokemon.spAttack;
            pokemon.currentSpDef = pokemon.spDef;
            pokemon.currentSpeed = pokemon.speed;
            pokemon.currentType1 = pokemon.type1;
            pokemon.currentType2 = pokemon.type2;
            pokemon.currentEvasion = 1;
            pokemon.currentAccuracy = 1;
        }
    }
 
    //Use before pokemon added to inventory
    // private void resetEnemyPokemonStats(){
    //     gp.wildPokemon.currentHP = gp.wildPokemon.hp;
    //     gp.wildPokemon.currentAttack = gp.wildPokemon.attack;
    //     gp.wildPokemon.currentSpAttack = gp.wildPokemon.spAttack;
    //     gp.wildPokemon.currentSpDef = gp.wildPokemon.spDef;
    //     gp.wildPokemon.currentSpeed = gp.wildPokemon.speed;  
    //     gp.wildPokemon.currentType1 = gp.wildPokemon.type1;
    //     gp.wildPokemon.currentType2 = gp.wildPokemon.type2;
    //     gp.wildPokemon.currentEvasion = 1;
    //     gp.wildPokemon.currentAccuracy = 1;
    // }
 
}