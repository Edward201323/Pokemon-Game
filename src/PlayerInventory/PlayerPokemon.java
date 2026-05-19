package PlayerInventory;
import java.util.ArrayList;

import Main.GamePanel;
import Pokemon.GetPokemon;
import Pokemon.Pokemon;

public class PlayerPokemon {
    private static final int PARTY_LIMIT = 6;

    public final ArrayList<Pokemon> pokemonEquipped = new ArrayList<>();
    public final ArrayList<Pokemon> pokemonInPC = new ArrayList<>();
    private final GamePanel gp;
    private final GetPokemon getPokemon = new GetPokemon();

    public PlayerPokemon(GamePanel gp) {
        this.gp = gp;
        // Testing seed: a full party so the center heal animation, blackout flow, and PC
        // viewer all have data to drive without grinding encounters first.
        // Charmander at Lv 15 is one level away from evolving (Lv 16 → Charmeleon), so
        // one battle's worth of XP triggers the evolution flow for testing.
        addPokemon("Kyurem",     100); // 4x on Rayquaza, 2x on Groudon
        addPokemon("Magnezone",  100); // 4x on Kyogre, resists Xerneas
        addPokemon("Ferrothorn", 100); // 4x on Kyogre+Groudon, resists Xerneas
        addPokemon("Bisharp",    100); // 2x on Mewtwo, resists Xerneas
        addPokemon("Lucario",    100); // 2x on Arceus, 2x on Xerneas
        addPokemon("Hydreigon",  100); // immune to Mewtwo's Psychic
    }

    public void addPokemon(String pokemonName, int level) {
        addToInventory(getPokemon.findPokemon(pokemonName, level));
    }

    // A caught pokemon goes to the party; if the party is already full it falls through to
    // PC storage (like the mainline games), where the PC viewer can show it.
    public void addPokemonCaught() {
        addToInventory(gp.wildPokemon);
    }

    // Pokemon-Center heal: restore all party members to full HP.
    public void healAll() {
        for (Pokemon p : pokemonEquipped) {
            if (p == null) continue;
            p.currentHP = p.maxHP;
        }
    }

    // True when every party member is at 0 HP — used to trigger the blackout.
    public boolean isAllFainted() {
        if (pokemonEquipped.isEmpty()) return false;
        for (Pokemon p : pokemonEquipped) {
            if (p != null && p.currentHP > 0) return false;
        }
        return true;
    }

    // Concatenate party + PC for the PC viewer (party first, then box).
    public java.util.List<Pokemon> allCaughtPokemon() {
        java.util.List<Pokemon> out = new java.util.ArrayList<>(pokemonEquipped.size() + pokemonInPC.size());
        out.addAll(pokemonEquipped);
        out.addAll(pokemonInPC);
        return out;
    }

    private void addToInventory(Pokemon pokemon) {
        if (pokemon == null) return;
        if (pokemonEquipped.size() < PARTY_LIMIT) {
            pokemonEquipped.add(pokemon);
        } else {
            pokemonInPC.add(pokemon);
        }
    }
}
