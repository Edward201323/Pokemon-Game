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
            // Legendaries spawn at the party's max level — never higher, so the player's
            // best pokemon is always at least matched.
            int legendaryLevel = Math.min(100, partyMax);
            gp.wildPokemon = getWildPokemon.findRandomLegendaryPokemon(legendaryLevel);
        } else {
            // Normal wild pokemon scale to the party's best: -5 up to partyMax (never
            // above), so the encounter is always within a tight band of the player's
            // strongest mon. Hard-capped at 80 to keep grindable wilds below the
            // end-game tier for Lv 80+ parties.
            int low  = Math.max(5, partyMax - 5);
            int high = Math.min(80, partyMax);
            if (low > high) low = high;
            int level = low + RNG.nextInt(high - low + 1);
            // Bucket by partyMax so early-game encounters are mostly base forms, mid-game
            // mostly mid-stage, and end-game fully evolved — matching the player's tier.
            gp.wildPokemon = getWildPokemon.findRandomNormalPokemonForPartyMax(level, partyMax);
        }
        // Music has to be picked AFTER the spawn so we can branch on isLegendary.
        changeMusic();
    }

    // Canonical mythical pokemon (Gen 1-6) — a subset of the CSV's is_legendary=YES set.
    // Encountering one swaps to a distinct theme so they feel rarer than a "regular"
    // legendary (Mewtwo / Lugia / Kyogre / etc.).
    private static final java.util.Set<String> MYTHICAL_NAMES = new java.util.HashSet<>(
        java.util.Arrays.asList(
            "Mew", "Celebi", "Jirachi", "Deoxys",
            "Manaphy", "Darkrai", "Arceus",
            "Victini", "Keldeo", "Meloetta",
            "Diancie", "Hoopa (Confined)", "Hoopa (Unbound)", "Volcanion"
        )
    );

    private void changeMusic() {
        gp.stopMusicResumeLater();
        if (gp.wildPokemon == null) {
            gp.playMusic(RNG.nextDouble() < 0.5 ? 5 : 6);
            return;
        }
        if (MYTHICAL_NAMES.contains(gp.wildPokemon.name)) {
            gp.playMusic(17); // 17_HauntedGate.wav — mysterious/mythical vibe
        } else if (gp.wildPokemon.isLegendary) {
            gp.playMusic(20); // 20_Legendary Battle.wav
        } else {
            gp.playMusic(RNG.nextDouble() < 0.5 ? 5 : 6); // WildBattle1 / WildBattle2
        }
    }
}
