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
        } else if ("Boss".equals(objectName)) {
            touchBoss((object.OBJ_Boss) gp.obj.get(i));
        }
    }

    private void touchPokeball(int i) {
        gp.obj.set(i, null);
    }

    // Trainer boss encounter. Plays the boss music + hands off to BossIntro for the
    // overworld dialog ("I am you..."). When the dialog drains, BossIntro builds the
    // fresh roster and transitions into the actual encounter.
    private void touchBoss(object.OBJ_Boss boss) {
        gp.stopMusicResumeLater();
        gp.playMusic(27); // 27_BossBattle2.wav
        gp.bossIntro.open(boss);
    }

    // 2% of all wild encounters roll into the legendary/mythical pool; the other 98% draw
    // from the normal pool. The split is overall, not per-pool, so adding more legendaries
    // doesn't change the total legendary encounter rate.
    private static final double LEGENDARY_ENCOUNTER_RATE = 0.02;

    private void touchBushRoute1(double encounterRate) {
        if (RNG.nextDouble() > encounterRate) return;
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
            // Normal wild pokemon scale to the party's best: -10..+5, but both ends are
            // clamped to [5, 80]. The hard ceiling of 80 keeps grindable wilds below the
            // player's end-game tier — without clamping `low` too, a Lv-100 party would
            // push the floor to 90 and the safeguard would yank the ceiling back to 90.
            int low  = Math.min(80, Math.max(5, partyMax - 10));
            int high = Math.min(80, partyMax + 5);
            if (high < low) high = low;
            int level = low + RNG.nextInt(high - low + 1);
            gp.wildPokemon = getWildPokemon.findRandomNormalPokemon(level);
        }
        // Music has to be picked AFTER the spawn so we can branch on isLegendary.
        changeMusic();
    }

    private void changeMusic() {
        gp.stopMusicResumeLater();
        if (gp.wildPokemon != null && gp.wildPokemon.isLegendary) {
            gp.playMusic(20); // 20_Legendary Battle.wav
        } else {
            gp.playMusic(RNG.nextDouble() < 0.5 ? 5 : 6); // WildBattle1 / WildBattle2
        }
    }
}
