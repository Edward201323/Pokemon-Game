package PokemonEncounters;
import java.util.Random;

import Main.GamePanel;
import Pokemon.GetPokemon;
import Pokemon.Pokemon;
public class PlayerObjectInteraction {
    GamePanel gp;
    GetPokemon getWildPokemon;
    public PlayerObjectInteraction(GamePanel gp){
        this.gp = gp;
        this.getWildPokemon = new GetPokemon();
    }
    
    public void objectInteraction(int i){
        //if i = 999, player is not touching obj
        if(i != 999){
            //reactions for each object
            String objectName = gp.obj.get(i).name; //gets object name
            if (objectName.equals("Pokeball")) {
                touchPokeball(i);
            }
            else if (objectName.equals("BushRoute1")) {
                touchBushRoute1(i, 0.015);
            }
        }
    }

    private void touchPokeball(int i) {
        gp.obj.set(i, null); //deletes boject
    }

    private void touchBushRoute1(int i, Double encounterRate) {
        if(Math.random() <= encounterRate){
            changeMusic();

            gp.gameState = gp.pokemonTransition;
            
            gp.wildPokemon = getWildPokemonRoute1();

            // gp.gameState = gp.pokemonEncounter; //Changes gamestate to a pokemon encounter
        }
    }

    private void changeMusic() {
        gp.stopMusicResumeLater(); //Stops musics
        if(Math.random() < 0.5){ //Randomly chooses 1 of 2 pokemon encounter songs
            gp.playMusic(5);
        }else{
            gp.playMusic(6);
        }
    }

    private Pokemon getWildPokemonRoute1() {
        double value = Math.random();
        String[] pokemon = {"Zapdos", "Articuno", "Moltres", "Absol", "Pikachu", "Fletchling", "Pancham", "Houndour", "Buneary", "Zigzagoon", "Bunnelby", "Sentret", "Aipom", "Starly"};
        double[] encounterProbabilities = {0.0005, 0.0010, 0.0015, 0.01, 0.05, 0.09, 0.14, 0.24, 0.34, 0.47, 0.60, 0.73, 0.86, 1.0};

        Random random = new Random();
        int randomLevel = random.nextInt(3)+3;

        for (int i = 0; i < encounterProbabilities.length; i++) {
            if (value <= encounterProbabilities[i]) {
                return getWildPokemon.findPokemon(pokemon[i], randomLevel);
            }
        }
        return null;
    }
    

}
