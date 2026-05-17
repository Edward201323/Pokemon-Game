package PlayerInventory;
import Main.GamePanel;
import Pokemon.GetPokemon;
import Pokemon.Pokemon;
import java.util.ArrayList;
public class PlayerPokemon {
    public ArrayList<Pokemon> pokemonEquipped;
    public ArrayList<Pokemon> pokemonInPC;
    GamePanel gp;
    public PlayerPokemon(GamePanel gp){
        pokemonEquipped = new ArrayList<>();
        pokemonInPC = new ArrayList<>();
        addPokemon("Ditto", 100);
        this.gp = gp;
    }

    public void addPokemon(String pokemonName, int level){
        GetPokemon getPokemon = new GetPokemon();
        if(pokemonEquipped.size()<6){
            pokemonEquipped.add(getPokemon.findPokemon(pokemonName, level));
        } else {
            pokemonInPC.add(getPokemon.findPokemon(pokemonName, level));
        }
    }

    public void addPokemonCaught(){
        if(pokemonEquipped.size()<6){
            
            pokemonEquipped.add(gp.wildPokemon);
        } else {
            pokemonInPC.add(gp.wildPokemon);
        }
    }
}
