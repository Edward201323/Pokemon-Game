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
        // Use the strongest party member's level so back-bench pokemon don't drag the
        // encounter difficulty down (and so leveling up your reserve actually matters).
        int partyMax = 1;
        for (Pokemon.Pokemon p : gp.playerPokemon.pokemonEquipped) {
            if (p != null && p.level > partyMax) partyMax = p.level;
        }
        if (RNG.nextDouble() < LEGENDARY_ENCOUNTER_RATE) {
            // Legendaries scale to the party's best: +10..+20, capped at 100.
            int legendaryLevel = Math.min(100, partyMax + 10 + RNG.nextInt(11));
            gp.wildPokemon = getWildPokemon.findRandomLegendaryPokemon(legendaryLevel);
        } else {
            // Normals scale to the party's best: -10..+5, clamped to [5, 100].
            int low  = Math.max(5,   partyMax - 10);
            int high = Math.min(100, partyMax + 5);
            if (high < low) high = low;
            int level = low + RNG.nextInt(high - low + 1);
            gp.wildPokemon = getWildPokemon.findRandomNormalPokemon(level);
        }
    }

    private void changeMusic() {
        gp.stopMusicResumeLater();
        gp.playMusic(RNG.nextDouble() < 0.5 ? 5 : 6);
    }
}
