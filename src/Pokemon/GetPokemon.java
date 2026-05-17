package Pokemon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Random;
public class GetPokemon {
    Pokemon pokemon;
    public GetPokemon(){
        this.pokemon = new Pokemon();
    }

    public Pokemon findPokemon(String pokemonName, int level){
        File file = new File("./src/res/PokemonData/PokemonStats.csv");
                
        try {
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while((line = bufferedReader.readLine()) != null){
                if(line.contains(pokemonName+",")){
                    break;
                }
            }
            bufferedReader.close();

            assignPokemonInfo(line, level);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return this.pokemon;
    }

    private void assignPokemonInfo(String pokemonInfo, int level){
        String[] pokemonInfoSplit = pokemonInfo.split(",");

        //Assign Name
        pokemon.name = pokemonInfoSplit[0];

        //Assign level
        pokemon.level = level;

        //Assign name in file for PNG
        String NameInFile = pokemon.name.substring(0, 1).toLowerCase() + pokemon.name.substring(1);
        pokemon.NameInFile = NameInFile+".png";
        pokemon.NameInFileGif = NameInFile+".gif";

        //Assign base stats
        pokemon.hp = Integer.parseInt(pokemonInfoSplit[1]);
        pokemon.attack = Integer.parseInt(pokemonInfoSplit[2]);
        pokemon.defense = Integer.parseInt(pokemonInfoSplit[3]);
        pokemon.spAttack = Integer.parseInt(pokemonInfoSplit[4]);
        pokemon.spDef = Integer.parseInt(pokemonInfoSplit[5]);
        pokemon.speed = Integer.parseInt(pokemonInfoSplit[6]);
        
        //Assign current stats
        pokemon.currentHP = pokemon.hp;
        pokemon.currentAttack = pokemon.attack;
        pokemon.currentDefense = pokemon.defense;
        pokemon.currentSpAttack = pokemon.spAttack;
        pokemon.currentSpDef = pokemon.spDef;
        pokemon.currentSpeed = pokemon.currentSpeed;
        pokemon.currentEvasion = 1;
        pokemon.currentAccuracy = 1;

        //Assign types
        pokemon.type1 = pokemonInfoSplit[7];
        pokemon.type2 = pokemonInfoSplit[8];

        //Assign current types
        pokemon.currentType1 = pokemon.type1;
        pokemon.currentType2 = pokemon.type2;

        //Assign captureRate
        pokemon.captureRate = Integer.parseInt(pokemonInfoSplit[9]);
        
        //Assign experience growth
        pokemon.experienceGrowth = Integer.parseInt(pokemonInfoSplit[10]);
        
        
        //Assign Gender
        pokemon.percentMale = Double.parseDouble(pokemonInfoSplit[11]);
        Double randomDoubleGender = Math.random()*100;
        if(pokemon.percentMale < 0){
            pokemon.gender = "Genderless";
        } else if (randomDoubleGender < pokemon.percentMale){
            pokemon.gender = "Male";
        } else {
            pokemon.gender = "Female";
        }
        
        //Assign happiness
        pokemon.happiness = Integer.parseInt(pokemonInfoSplit[12]);

        //Assign Pokedex Number
        pokemon.pokedexNumber = Integer.parseInt(pokemonInfoSplit[13]);

        //Assign EV Drops
        pokemon.expGiven = Integer.parseInt(pokemonInfoSplit[14]);
        pokemon.hpEVsGiven = Integer.parseInt(pokemonInfoSplit[15]);
        pokemon.attackEVsGiven = Integer.parseInt(pokemonInfoSplit[16]);
        pokemon.defenseEVsGiven = Integer.parseInt(pokemonInfoSplit[17]);
        pokemon.spAttackEVsGiven = Integer.parseInt(pokemonInfoSplit[18]);
        pokemon.spDefEVsGiven = Integer.parseInt(pokemonInfoSplit[19]);
        pokemon.speedEVsGiven = Integer.parseInt(pokemonInfoSplit[20]);

        //Assign Abilities;
        if(pokemonInfoSplit.length == 21){
            pokemon.ability = pokemonInfoSplit[33].substring(2, pokemonInfoSplit[33].length()-2);
        }
        if(pokemonInfoSplit.length == 22){
            pokemon.ability1 = pokemonInfoSplit[33].substring(3, pokemonInfoSplit[33].length()-1);
            pokemon.ability2 = null;
            pokemon.hiddenAbility = pokemonInfoSplit[34].substring(2, pokemonInfoSplit[34].length()-3);

            if(Math.random()<.1){
                pokemon.ability = pokemon.hiddenAbility;
            } else {
                pokemon.ability = pokemon.ability1;
            }
        }
        if(pokemonInfoSplit.length == 33){
            pokemon.ability1 = pokemonInfoSplit[33].substring(3, pokemonInfoSplit[33].length()-1);
            pokemon.ability2 = pokemonInfoSplit[34].substring(2, pokemonInfoSplit[34].length()-1);
            pokemon.hiddenAbility = pokemonInfoSplit[35].substring(2, pokemonInfoSplit[35].length()-3);

            double randomDoubleAbility = Math.random();
            if(randomDoubleAbility < .1){
                pokemon.ability = pokemon.hiddenAbility;
            } else if(randomDoubleAbility >= .1 && randomDoubleAbility < .55){
                pokemon.ability = pokemon.ability1;
            } else {
                pokemon.ability = pokemon.ability2;
            }
        }

        //Assign shiny
        double shiny = Math.random();
        if(shiny<=0.0005){
            pokemon.shiny = true;
        } else {
            pokemon.shiny = false;
        }
        
        //Assign EV
        pokemon.evHP = 0;
        pokemon.evAttack = 0;
        pokemon.evDefense = 0;
        pokemon.evSpAttack = 0;
        pokemon.evSpDef = 0;
        pokemon.evSpeed = 0;

        //Assign IV
        ArrayList<Integer> list = new ArrayList<Integer>();
        Random random = new Random();
        for (int i = 0; i <= 5; i++) {
            list.add(i);
        }
        //Assign 31 iv to 3 removed values
        for (int i = 0; i < 3; i++) {
            int index = random.nextInt(list.size());
            int value = list.remove(index);
            if(value == 0){
                pokemon.ivHP = 31;
            } else if (value == 1){
                pokemon.ivAttack = 31;
            } else if (value == 2){
                pokemon.ivDefense = 31;
            } else if (value == 3){
                pokemon.ivSpAttack = 31;
            } else if (value == 4){
                pokemon.ivSpDef = 31;
            } else {
                pokemon.ivSpeed = 31;
            }
        }
        //Assign random int for 
        for (Integer value : list) {
            int randomValue = (int) (Math.random() * 32);
            if (value == 0) {
              pokemon.ivHP = randomValue;
            } else if (value == 1) {
              pokemon.ivAttack = randomValue;
            } else if (value == 2) {
              pokemon.ivDefense = randomValue;
            } else if (value == 3) {
              pokemon.ivSpAttack = randomValue;
            } else if (value == 4) {
              pokemon.ivSpDef = randomValue;
            } else {
              pokemon.ivSpeed = randomValue;
            }
          }
    }
}
