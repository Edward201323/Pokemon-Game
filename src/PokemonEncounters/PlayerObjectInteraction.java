package PokemonEncounters;
import java.util.Random;

import Main.GamePanel;
import Pokemon.GetPokemon;

public class PlayerObjectInteraction {
    private static final Random RNG = new Random();

    private final GamePanel gp;
    private final GetPokemon getWildPokemon = new GetPokemon();

    public PlayerObjectInteraction(GamePanel gp) {
        this.gp = gp;
    }

    public void objectInteraction(int i) {
        if (i == 999) return; // 999 = no object touched
        String objectName = gp.obj.get(i).name;
        if ("Pokeball".equals(objectName)) {
            touchPokeball(i);
        } else if ("BushRoute1".equals(objectName)) {
            touchBushRoute1(0.015);
        }
    }

    private void touchPokeball(int i) {
        gp.obj.set(i, null);
    }

    private void touchBushRoute1(double encounterRate) {
        if (RNG.nextDouble() > encounterRate) return;
        changeMusic();
        gp.gameState = gp.pokemonTransition;
        int level = RNG.nextInt(3) + 3;
        gp.wildPokemon = getWildPokemon.findRandomPokemon(level);
    }

    private void changeMusic() {
        gp.stopMusicResumeLater();
        gp.playMusic(RNG.nextDouble() < 0.5 ? 5 : 6);
    }
}
