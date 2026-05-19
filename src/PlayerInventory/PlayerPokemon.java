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
        // Empty by default — the title screen decides between New Game (seedStarterParty)
        // and Load Game (SaveManager rebuilds the party from disk). Constructing empty
        // avoids spending RNG / file lookups before that decision is made.
    }

    // SaveManager / StarterSelection hook: drop the current party + PC so the caller can
    // repopulate cleanly.
    public void clearAll() {
        pokemonEquipped.clear();
        pokemonInPC.clear();
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

    // Highest level across party + PC. Used to scale the pokeball catch bonus over the
    // course of a playthrough (low → normal-ball strength, Lv 100 → ultra-ball strength).
    // Returns 1 for an empty inventory so the formula stays well-defined for a fresh save.
    public int highestLevel() {
        int max = 1;
        for (Pokemon p : pokemonEquipped) if (p != null && p.level > max) max = p.level;
        for (Pokemon p : pokemonInPC)     if (p != null && p.level > max) max = p.level;
        return max;
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
            // Pokemon stored in the PC auto-heal — boxing is treated as a rest stop.
            pokemonInPC.add(pokemon);
            healOne(pokemon);
        }
    }

    // PC-side healing: full HP for a single pokemon. Called when something enters the PC
    // (caught with full party, or swapped in from the party menu) so the box is always
    // a fully-healed pool. Callers can also invoke healAllInPC() as a defensive sweep.
    public static void healOne(Pokemon p) {
        if (p == null) return;
        p.currentHP = p.maxHP;
    }

    public void healAllInPC() {
        for (Pokemon p : pokemonInPC) healOne(p);
    }
}
