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

    // 1% of all wild encounters roll into the legendary/mythical pool; the other 99% draw
    // from the normal pool. The split is overall, not per-pool, so adding more legendaries
    // doesn't change the total legendary encounter rate.
    private static final double LEGENDARY_ENCOUNTER_RATE = 0.01;

    private void touchBushRoute1(double encounterRate) {
        if (RNG.nextDouble() > encounterRate) return;
        changeMusic();
        gp.gameState = gp.pokemonTransition;
        if (RNG.nextDouble() < LEGENDARY_ENCOUNTER_RATE) {
            // Legendaries scale to the lead pokemon: +10..+20, capped at 100.
            int leadLevel = gp.playerPokemon.pokemonEquipped.get(0).level;
            int legendaryLevel = Math.min(100, leadLevel + 10 + RNG.nextInt(11));
            gp.wildPokemon = getWildPokemon.findRandomLegendaryPokemon(legendaryLevel);
        } else {
            int level = RNG.nextInt(3) + 3;
            gp.wildPokemon = getWildPokemon.findRandomNormalPokemon(level);
        }
    }

    private void changeMusic() {
        gp.stopMusicResumeLater();
        gp.playMusic(RNG.nextDouble() < 0.5 ? 5 : 6);
    }
}
