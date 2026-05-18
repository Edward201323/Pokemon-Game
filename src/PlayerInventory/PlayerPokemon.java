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
        addPokemon("Mewtwo", 100);
    }

    public void addPokemon(String pokemonName, int level) {
        addToInventory(getPokemon.findPokemon(pokemonName, level));
    }

    // A caught pokemon goes to the party. If the party is already full we discard it for now
    // (no PC/box UI yet); revisit when storage lands.
    public void addPokemonCaught() {
        Pokemon pokemon = gp.wildPokemon;
        if (pokemon == null) return;
        if (pokemonEquipped.size() < PARTY_LIMIT) {
            pokemonEquipped.add(pokemon);
        }
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
