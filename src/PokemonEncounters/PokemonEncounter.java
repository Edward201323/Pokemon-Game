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
        //background
        g2.drawImage(encounterBackgrounds[12], 0, 0, gp.screenWidth, gp.screenHeight-175, null);
 
        //Enemy Pokemon
        g2.drawImage(getPokemonImages.getPokemonFront(gp.wildPokemon), 600, 150, 175, 175, null);
 
        drawBeforeEncounterAssets();
    }
 
 
    public void drawBeforeEncounterAssets() {
        counterB++;
 
        if(ballThrown == false){
            //Draw Dawn
            g2.drawImage(encounterAssets[9],75,298, 200, 200, null);
        }
 
        //Enemy Pokemon HP Bar
        g2.drawImage(encounterAssets[3], 50, 50, 380, 100, null);
 
        //Enemy Gender
        if(gp.wildPokemon.gender.equals("Male")){
            g2.drawImage(encounterAssets[6], 280, 71, 20, 30, null);
        }
        if (gp.wildPokemon.gender.equals("Female")){
            g2.drawImage(encounterAssets[5], 280, 71, 20, 30, null);
        }
 
        g2.setFont(MaruMonicaSmall);
        g2.setColor(Color.black);
        //Name
        g2.drawString(gp.wildPokemon.name, 85, 97);
 
        //Level
        drawLevel(gp.wildPokemon.level, 338, 97);
 
        if(counterB<=150){
            //Bottom UI Bar
            g2.drawImage(encounterAssets[0], 0, gp.screenHeight-175, gp.screenWidth, 175,  null);
            
            g2.setFont(MaruMonica);
            g2.setColor(Color.black);
            g2.drawString("A wild "+gp.wildPokemon.name+" appeared!", 40, gp.screenHeight-110);
            g2.setColor(Color.white);
            g2.drawString("A Wild "+gp.wildPokemon.name+" appeared!", 38, gp.screenHeight-112);
        }
        if(counterB>150){
            drawDawnThrowBall();
            ballThrown = true;
 
            //Bottom UI Bar
            g2.drawImage(encounterAssets[0], 0, gp.screenHeight-175, gp.screenWidth, 175,  null);

            g2.setFont(MaruMonica);
            g2.setColor(Color.black);
            g2.drawString("Go! "+gp.playerPokemon.pokemonEquipped.get(0).name+"!", 38, gp.screenHeight-110);
            g2.setColor(Color.white);
            g2.drawString("Go! "+gp.playerPokemon.pokemonEquipped.get(0).name+"!", 36, gp.screenHeight-112);
        }
 
 
        //Draw fade animation
        animations.FadeOut(g2);
    }
 
 
    private void drawDawnThrowBall(){
        counterA++;
        counterC++;
        if(counterA<=11){
            if(animationFrame == 1){
                g2.drawImage(encounterAssets[10],75,298, 200, 200, null);
            } else if (animationFrame == 2){
                g2.drawImage(encounterAssets[11],75,298, 200, 200, null);
            } else if (animationFrame == 3){
                g2.drawImage(encounterAssets[12],75,298, 200, 200, null);
            } else if (animationFrame == 4){
                g2.drawImage(encounterAssets[13],75,298, 200, 200, null);
            } else if (animationFrame == 5){
                g2.drawImage(getPokemonImages.getPokemonBack(gp.playerPokemon.pokemonEquipped.get(0)), 25, 235, 325, 325, null);
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
        //background
        g2.drawImage(encounterBackgrounds[12], 0, 0, gp.screenWidth, gp.screenHeight-175, null);

        drawPokemonSprite(getPokemonImages.getPokemonFront(gp.wildPokemon),
                          600, 150, 175, 175, battle.enemyFaintFraction());
        drawPokemonSprite(getPokemonImages.getPokemonBack(gp.playerPokemon.pokemonEquipped.get(0)),
                          25, 235, 325, 325, battle.playerFaintFraction());

        drawEncounterAssets();
    }

    // Right-align the level to where a 2-digit number ends at `leftX`, so 1/2/3-digit
    // levels each anchor differently and "100" doesn't push past the UI bar.
    private void drawLevel(int level, int leftX, int y){
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int rightEdge = leftX + fm.stringWidth("99");
        String s = Integer.toString(level);
        g2.drawString(s, rightEdge - fm.stringWidth(s), y);
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
        //draw dialogue bar
        g2.drawImage(encounterAssets[0], 0, gp.screenHeight-175, gp.screenWidth, 175,  null); 
 
        //hp bar
        g2.drawImage(encounterAssets[3], 50, 50, 380, 100, null);
        g2.drawImage(encounterAssets[4], 427, 300, 400, 150, null);
 
        //Enemy Gender
        if(gp.wildPokemon.gender.equals("Male")){
            g2.drawImage(encounterAssets[6], 280, 71, 20, 30, null);
        }
        if (gp.wildPokemon.gender.equals("Female")){
            g2.drawImage(encounterAssets[5], 280, 71, 20, 30, null);
        }
        //My Pokemon Gender
        if(gp.playerPokemon.pokemonEquipped.get(0).gender.equals("Male")){
            g2.drawImage(encounterAssets[6], 698, 350, 25, 30, null);
        }
        if(gp.playerPokemon.pokemonEquipped.get(0).gender.equals("Female")){
            g2.drawImage(encounterAssets[5], 698, 350, 25, 30, null);
        }
 
        g2.setFont(MaruMonicaSmall);
        g2.setColor(Color.black);
        //Name
        g2.drawString(gp.wildPokemon.name, 85, 97);
        g2.drawString(gp.playerPokemon.pokemonEquipped.get(0).name, 500, 376);
 
        //Levels
        drawLevel(gp.wildPokemon.level, 338, 97);
        drawLevel(gp.playerPokemon.pokemonEquipped.get(0).level, 762, 376);
 
        //Draw ui bar
        if(counterA*71 < 497){
            g2.drawImage(encounterAssets[1], 500, counterA*71, 364, 175,  null);
        } else {
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
        gp.gameState = gp.playState;
        gp.stopMusic();
        gp.resumeMusic(3);
 
        resetPokemonEquippedStats();
 
 
        //Reset variables
        ballThrown = false;
        endEncounter = false;
        chooseAction = false;
        battleStarted = false;

        // Heal the party between encounters so the next battle starts fresh.
        for(Pokemon p : gp.playerPokemon.pokemonEquipped){
            p.currentHP = p.maxHP;
        }

        counterA = 0;
        counterB = 0;
        counterC = 0;
 
        animations.counterA = 0;
        animations.counterB = 0;
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