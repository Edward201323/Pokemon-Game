package Main;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

import PlayerInventory.OpenPlayerInventory;
import PlayerInventory.PlayerInventory;
import PlayerInventory.PlayerPokemon;
import Pokemon.Pokemon;
import PokemonEncounters.PokemonEncounter;
import entity.Player;
import object.AssetSetter;
import object.SuperObject;
import tile.TileManager;
import java.util.ArrayList;
public class GamePanel extends JPanel implements Runnable{
    //screen settings
    final int originalTileSize = 16; //16x16 tile
    final int scale = 3;

    public final int tileSize = originalTileSize*scale; //48x48 tile
    public final int maxScreenCol = 18; 
    public final int maxScreenRow = 14;
    public final int screenWidth = tileSize * maxScreenCol;
    public final int screenHeight = tileSize * maxScreenRow;

    public final int maxWorldCol = 49; //map dimensions
    public final int maxWorldRow = 103;
    
    //FPS
    int FPS = 60;

    //System
    TileManager tileM = new TileManager(this); //Tiles

    KeyHandler keyH = new KeyHandler(); //Player movement

    Sound music = new Sound(); //Sound
    Sound soundEffect = new Sound(); //Sound

    public CollisionChecker cChecker = new CollisionChecker(this); //Collision Checker

    public AssetSetter aSetter = new AssetSetter(this); //Sets objects positions

    public PokemonEncounter encounter = new PokemonEncounter(this, keyH);

    public OpenPlayerInventory openPlayerInventory = new OpenPlayerInventory(this, keyH);

    public Pokemon wildPokemon;

    public PlayerInventory playerInventory = new PlayerInventory();

    public PlayerPokemon playerPokemon = new PlayerPokemon(this);

    Thread gameThread; //adds "time" to the game
    
    public Player player = new Player(this,keyH); //Player Object Interaction
    public ArrayList<SuperObject> obj = new ArrayList<>(); //Objects arraylist

    //Encounter
    public int gameState;
    public final int playState = 0;
    public final int pokemonEncounter = 1;
    public final int pokemonTransition = 3;
    public final int beforePokemonEncounter = 4;
    public final int InventoryPokemonState = 5;
    public final int InventoryBagState = 6;

    public GamePanel(){
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.black);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyH);
        this.setFocusable(true);  //game panel can be focused to receieve input
    }

    public void setupGame(){
        //creates method so we can set up other stuff in the future
        aSetter.setObject();
        //plays sound of music array index i
        playMusic(3);
        gameState = playState;
    }

    public void startGameThread(){
        //starts the run method
        gameThread = new Thread(this);
        gameThread.start();
    }
    
    @Override
    public void run() {
        //Creates game Loop,j core of our game
        //as long as gamethread exist, it repeats whats inside these brackets
        
        double drawInterval = 1000000000/FPS; // 0.016666 seconds
        double nextDrawTime = System.nanoTime() + drawInterval;
        while(gameThread !=null){
            //Updates the information such as character positions
            update();
            //Draws on the screen the updated Information, Redraws the screen at the FPS

            repaint();
            
            try{
                double remainingTime = nextDrawTime - System.nanoTime();
                remainingTime = remainingTime/1000000;

                if(remainingTime<0){
                    remainingTime=0;
                }

                Thread.sleep((long) remainingTime);
                
                nextDrawTime += drawInterval;

            } catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    public void update(){
        if(gameState == playState){
            player.update();
        }
        if(gameState == pokemonEncounter){

        }
        if(gameState == pokemonTransition){

        }
        if(gameState == beforePokemonEncounter){
            
        }
        if(gameState == InventoryBagState){

        }
        if(gameState == InventoryPokemonState){
            
        }
    }

    @Override
    public void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g;

        tileM.draw(g2);

        //Draws player bottom half
        player.drawPlayerHalf(g2, false);

        //Draws object
        for(int i = 0; i < obj.size(); i++){ //forloop scans through the SuperObject array and draws
            if(obj.get(i) != null){
                obj.get(i).draw(g2, this);
            }
        }

        //Draws player top half
        player.drawPlayerHalf(g2, true);
        
        openPlayerInventory.draw(g2);

        encounter.draw(g2);
        

        g2.dispose();
    }

    public void playMusic(int i){
        music.setMusicFile(i);
        music.play();
        music.loop();
    }

    public void stopMusic(){
        music.stop(false);
    }

    public void stopMusicResumeLater(){
        music.stop(true);
    }

    public void resumeMusic(int i){
        music.setMusicFile(i);
        music.resume();
        music.loop();
    }

    public void playSE(int i){
        soundEffect.setSEFile(i);
        soundEffect.play();
    }

}