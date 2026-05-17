package PlayerInventory;
import java.util.ArrayList;
import Items.PokemonItems;
public class PlayerInventory {
    ArrayList<PokemonItems> playerBag;
    int cash;
    public PlayerInventory(){
        playerBag = new ArrayList<>();
        this.cash = 0;
    }

    
    public void addItem(PokemonItems pokemonItem){
        playerBag.add(pokemonItem);
    }
    
    public void removeItem(PokemonItems pokemonItem){
        playerBag.remove(pokemonItem);
    }
    
    
    public void addCash(int cashAdded){
        this.cash += cashAdded;
    }

    public void removeCash(int cashRemoved){
        this.cash -= cashRemoved;
    }

}
