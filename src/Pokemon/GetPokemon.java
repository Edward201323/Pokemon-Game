package Pokemon;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GetPokemon {
    private static final String CSV_PATH = "./src/res/PokemonData/PokemonStats.csv";
    private static final Random RNG = new Random();
    private static final Map<String, String[]> CACHE = loadAll();

    private static Map<String, String[]> loadAll() {
        Map<String, String[]> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_PATH))) {
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = parseRow(line);
                if (row.length > 0 && !row[0].isEmpty()) {
                    map.put(row[0], row);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    // CSV rows have 21 plain fields plus a trailing abilities list, e.g.
    //   ...,3,0,0,"['Blaze', 'Solar Power']"
    // The single-ability rows are unquoted: ...,0,0,['Levitate']
    // Return: [field0, ..., field20, ability1, (ability2, (ability3))]
    private static String[] parseRow(String line) {
        int bracket = line.indexOf('[');
        if (bracket < 0) {
            return line.split(",", -1);
        }
        int closeBracket = line.lastIndexOf(']');
        String head = line.substring(0, bracket);
        if (head.endsWith("\"")) head = head.substring(0, head.length() - 1);
        if (head.endsWith(",")) head = head.substring(0, head.length() - 1);
        String inside = line.substring(bracket + 1, closeBracket);

        String[] base = head.split(",", -1);
        String[] abilities = inside.split(",");
        String[] out = new String[base.length + abilities.length];
        System.arraycopy(base, 0, out, 0, base.length);
        for (int i = 0; i < abilities.length; i++) {
            out[base.length + i] = abilities[i].trim().replace("'", "");
        }
        return out;
    }

    public Pokemon findPokemon(String pokemonName, int level) {
        String[] row = CACHE.get(pokemonName);
        if (row == null) {
            System.err.println("Unknown pokemon: " + pokemonName);
            return null;
        }
        Pokemon pokemon = new Pokemon();
        assignPokemonInfo(pokemon, row, level);
        return pokemon;
    }

    private void assignPokemonInfo(Pokemon pokemon, String[] row, int level) {
        pokemon.name = row[0];
        pokemon.level = level;

        String nameInFile = Character.toLowerCase(pokemon.name.charAt(0)) + pokemon.name.substring(1);
        pokemon.NameInFile = nameInFile + ".png";
        pokemon.NameInFileGif = nameInFile + ".gif";

        pokemon.hp = Integer.parseInt(row[1]);
        pokemon.attack = Integer.parseInt(row[2]);
        pokemon.defense = Integer.parseInt(row[3]);
        pokemon.spAttack = Integer.parseInt(row[4]);
        pokemon.spDef = Integer.parseInt(row[5]);
        pokemon.speed = Integer.parseInt(row[6]);

        pokemon.currentHP = pokemon.hp;
        pokemon.currentAttack = pokemon.attack;
        pokemon.currentDefense = pokemon.defense;
        pokemon.currentSpAttack = pokemon.spAttack;
        pokemon.currentSpDef = pokemon.spDef;
        pokemon.currentSpeed = pokemon.speed;
        pokemon.currentEvasion = 1;
        pokemon.currentAccuracy = 1;

        pokemon.type1 = row[7];
        pokemon.type2 = row[8];
        pokemon.currentType1 = pokemon.type1;
        pokemon.currentType2 = pokemon.type2;

        pokemon.captureRate = Integer.parseInt(row[9]);
        pokemon.experienceGrowth = Integer.parseInt(row[10]);

        pokemon.percentMale = Double.parseDouble(row[11]);
        if (pokemon.percentMale < 0) {
            pokemon.gender = "Genderless";
        } else if (RNG.nextDouble() * 100 < pokemon.percentMale) {
            pokemon.gender = "Male";
        } else {
            pokemon.gender = "Female";
        }

        pokemon.happiness = Integer.parseInt(row[12]);
        pokemon.pokedexNumber = Integer.parseInt(row[13]);

        pokemon.expGiven = Integer.parseInt(row[14]);
        pokemon.hpEVsGiven = Integer.parseInt(row[15]);
        pokemon.attackEVsGiven = Integer.parseInt(row[16]);
        pokemon.defenseEVsGiven = Integer.parseInt(row[17]);
        pokemon.spAttackEVsGiven = Integer.parseInt(row[18]);
        pokemon.spDefEVsGiven = Integer.parseInt(row[19]);
        pokemon.speedEVsGiven = Integer.parseInt(row[20]);

        assignAbility(pokemon, row);

        pokemon.shiny = RNG.nextDouble() <= 0.0005;

        assignIVs(pokemon);
        // EVs initialize to 0 by default
    }

    private void assignAbility(Pokemon pokemon, String[] row) {
        int abilityCount = row.length - 21;
        if (abilityCount <= 1) {
            pokemon.ability1 = abilityCount == 1 ? row[21] : null;
            pokemon.ability = pokemon.ability1;
            return;
        }
        if (abilityCount == 2) {
            // Two listed: first is normal, second is hidden (10% chance)
            pokemon.ability1 = row[21];
            pokemon.hiddenAbility = row[22];
            pokemon.ability = RNG.nextDouble() < 0.1 ? pokemon.hiddenAbility : pokemon.ability1;
            return;
        }
        // Three or more: two normals (45% each) + hidden (10%)
        pokemon.ability1 = row[21];
        pokemon.ability2 = row[22];
        pokemon.hiddenAbility = row[23];
        double r = RNG.nextDouble();
        if (r < 0.1) {
            pokemon.ability = pokemon.hiddenAbility;
        } else if (r < 0.55) {
            pokemon.ability = pokemon.ability1;
        } else {
            pokemon.ability = pokemon.ability2;
        }
    }

    // Three IVs are guaranteed 31, remaining three are random 0-31.
    private void assignIVs(Pokemon pokemon) {
        int[] ivs = new int[6];
        // Random 0-31 for all
        for (int i = 0; i < 6; i++) ivs[i] = RNG.nextInt(32);
        // Pick 3 distinct indices to set to 31
        int[] pool = {0, 1, 2, 3, 4, 5};
        for (int i = 5; i > 2; i--) {
            int j = RNG.nextInt(i + 1);
            int tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp;
        }
        for (int i = 3; i < 6; i++) ivs[pool[i]] = 31;

        pokemon.ivHP = ivs[0];
        pokemon.ivAttack = ivs[1];
        pokemon.ivDefense = ivs[2];
        pokemon.ivSpAttack = ivs[3];
        pokemon.ivSpDef = ivs[4];
        pokemon.ivSpeed = ivs[5];
    }
}
