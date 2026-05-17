package PokemonEncounters;
import java.util.Random;

import Main.GamePanel;
import Pokemon.GetPokemon;
import Pokemon.Pokemon;

public class PlayerObjectInteraction {
    private static final Random RNG = new Random();
    private static final String[] ROUTE1_POKEMON = {
        "Zapdos", "Articuno", "Moltres", "Absol", "Pikachu",
        "Fletchling", "Pancham", "Houndour", "Buneary",
        "Zigzagoon", "Bunnelby", "Sentret", "Aipom", "Starly"
    };
    // Cumulative-probability table aligned with ROUTE1_POKEMON.
    private static final double[] ROUTE1_CUM_PROB = {
        0.0005, 0.0010, 0.0015, 0.01, 0.05,
        0.09, 0.14, 0.24, 0.34,
        0.47, 0.60, 0.73, 0.86, 1.0
    };

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
        gp.wildPokemon = getWildPokemonRoute1();
    }

    private void changeMusic() {
        gp.stopMusicResumeLater();
        gp.playMusic(RNG.nextDouble() < 0.5 ? 5 : 6);
    }

    private Pokemon getWildPokemonRoute1() {
        double roll = RNG.nextDouble();
        int level = RNG.nextInt(3) + 3;
        for (int i = 0; i < ROUTE1_CUM_PROB.length; i++) {
            if (roll <= ROUTE1_CUM_PROB[i]) {
                return getWildPokemon.findPokemon(ROUTE1_POKEMON[i], level);
            }
        }
        return null;
    }
}
