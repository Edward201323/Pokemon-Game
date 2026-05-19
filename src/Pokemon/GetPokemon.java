package Pokemon;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GetPokemon {
    private static final String CSV_PATH = "./src/res/PokemonData/PokemonStats.csv";
    private static final Random RNG = new Random();
    private static final Map<String, String[]> CACHE = loadAll();
    // Split pools indexed by is_legendary so encounter callers can roll against
    // each one without re-scanning the full pokedex on every encounter.
    private static final String[] LEGENDARY_NAMES = collectByLegendary(true);
    private static final String[] NORMAL_NAMES    = collectByLegendary(false);

    // Non-legendary species bucketed by where they sit in their evolution chain. Lets the
    // encounter system bias by the player's max party level: low-level players meet base
    // forms, mid-level players meet middle forms, high-level players meet final forms.
    // Single-stage species (no evolution at all, e.g., Tauros) bucket as FINAL so they
    // still appear, just not at low levels.
    private static final String[] STAGE_BASE_NAMES  = collectByStage(0);
    private static final String[] STAGE_MID_NAMES   = collectByStage(1);
    private static final String[] STAGE_FINAL_NAMES = collectByStage(2);

    // Per-species minimum spawn level: the evolve_level of whatever evolves *into* this
    // species. A Charizard's pre-evo Charmeleon evolves at Lv 36, so MIN_LEVEL["Charizard"]
    // = 36 and we won't pick Charizard for a Lv-30 wild encounter. Species nobody evolves
    // into default to 1 (base forms, single-stage species).
    private static final Map<String, Integer> MIN_SPAWN_LEVEL = computeMinSpawnLevels();

    private static Map<String, Integer> computeMinSpawnLevels() {
        Map<String, Integer> out = new HashMap<>();
        for (String[] row : CACHE.values()) {
            if (row.length <= 23) continue;
            String into = row[22].trim();
            if (into.isEmpty()) continue;
            int level;
            try { level = Integer.parseInt(row[23].trim()); }
            catch (NumberFormatException e) { continue; }
            // If multiple species evolve into the same target (rare, e.g., Eevee chains),
            // take the LOWEST evolve level so the target is still encounterable as early
            // as any pre-evo could justify.
            Integer cur = out.get(into);
            if (cur == null || level < cur) out.put(into, level);
        }
        return out;
    }

    private static int minSpawnLevel(String name) {
        Integer v = MIN_SPAWN_LEVEL.get(name);
        return v == null ? 1 : v;
    }

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

    // Pick a random pokemon from the full Pokedex CSV. Uniform across every species.
    public Pokemon findRandomPokemon(int level) {
        String[] names = CACHE.keySet().toArray(new String[0]);
        if (names.length == 0) return null;
        return findPokemon(names[RNG.nextInt(names.length)], level);
    }

    // Pick a random non-legendary/non-mythical species. Used for the normal 99% of encounters.
    public Pokemon findRandomNormalPokemon(int level) {
        if (NORMAL_NAMES.length == 0) return findRandomPokemon(level);
        return findPokemon(NORMAL_NAMES[RNG.nextInt(NORMAL_NAMES.length)], level);
    }

    // Pick a random legendary/mythical species. Used for the rare 1% encounter roll.
    public Pokemon findRandomLegendaryPokemon(int level) {
        if (LEGENDARY_NAMES.length == 0) return findRandomPokemon(level);
        return findPokemon(LEGENDARY_NAMES[RNG.nextInt(LEGENDARY_NAMES.length)], level);
    }

    // Level-tiered wild encounter pick. The player's max party level decides which
    // evolution-stage bucket we draw from (1..20 = base, 21..40 = mid, 41+ = final), and
    // we then filter out species whose MIN_SPAWN_LEVEL exceeds encounterLevel — so e.g.
    // a Lv 30 wild can't be a Charizard (Charmeleon evolves at 36). Falls back through
    // wider pools if the strict filter empties out.
    public Pokemon findRandomNormalPokemonForPartyMax(int encounterLevel, int partyMax) {
        String[] bucket = bucketForPartyMax(partyMax);
        String pick = pickFromBucketWithMinLevel(bucket, encounterLevel);
        if (pick != null) return findPokemon(pick, encounterLevel);
        // Bucket exhausted at this level — try every non-legendary species that's legal
        // for this level (any stage) before degrading to "any species, no min filter".
        pick = pickFromBucketWithMinLevel(NORMAL_NAMES, encounterLevel);
        if (pick != null) return findPokemon(pick, encounterLevel);
        return findRandomNormalPokemon(encounterLevel);
    }

    private static String pickFromBucketWithMinLevel(String[] pool, int encounterLevel) {
        if (pool == null || pool.length == 0) return null;
        List<String> eligible = new ArrayList<>();
        for (String n : pool) {
            if (minSpawnLevel(n) <= encounterLevel) eligible.add(n);
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(RNG.nextInt(eligible.size()));
    }

    private static String[] bucketForPartyMax(int partyMax) {
        if (partyMax <= 20) return STAGE_BASE_NAMES;
        if (partyMax <= 40) return STAGE_MID_NAMES;
        return STAGE_FINAL_NAMES;
    }

    // Walk the cached rows once at class-init and bucket names by is_legendary (col 21).
    private static String[] collectByLegendary(boolean legendary) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : CACHE.entrySet()) {
            String[] row = entry.getValue();
            if (row.length <= 21) continue;
            boolean isLeg = "YES".equalsIgnoreCase(row[21]);
            if (isLeg == legendary) names.add(entry.getKey());
        }
        return names.toArray(new String[0]);
    }

    // Bucket non-legendary species by their position in an evolution chain.
    //   stage 0 = base of a multi-stage chain (Charmander, Bulbasaur)
    //   stage 1 = middle of a multi-stage chain (Charmeleon, Ivysaur)
    //   stage 2 = final form of a chain *or* a single-stage species (Charizard, Tauros)
    // A species is "the target of" something else iff some other row's evolves_into points
    // at it. Walking forward via evolves_into tells us how many evolutions are ahead.
    private static String[] collectByStage(int targetStage) {
        java.util.Set<String> isEvolutionTarget = new java.util.HashSet<>();
        for (String[] row : CACHE.values()) {
            String into = row.length > 22 ? row[22].trim() : "";
            if (!into.isEmpty()) isEvolutionTarget.add(into);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : CACHE.entrySet()) {
            String[] row = entry.getValue();
            if (row.length <= 21) continue;
            if ("YES".equalsIgnoreCase(row[21])) continue; // legendaries excluded
            String name = entry.getKey();
            String into = row.length > 22 ? row[22].trim() : "";
            boolean evolvesForward = !into.isEmpty();
            boolean isTarget = isEvolutionTarget.contains(name);
            int stage;
            if (evolvesForward && !isTarget)       stage = 0; // base of multi-stage chain
            else if (evolvesForward &&  isTarget)  stage = 1; // middle of chain
            else                                   stage = 2; // final form OR single-stage
            if (stage == targetStage) out.add(name);
        }
        return out.toArray(new String[0]);
    }

    private void assignPokemonInfo(Pokemon pokemon, String[] row, int level) {
        pokemon.name = row[0];
        pokemon.level = level;

        String nameInFile = Character.toLowerCase(pokemon.name.charAt(0)) + pokemon.name.substring(1);
        pokemon.NameInFile = nameInFile + ".png";
        pokemon.NameInFileGif = nameInFile + ".gif";

        int baseHP   = Integer.parseInt(row[1]);
        int baseAtk  = Integer.parseInt(row[2]);
        int baseDef  = Integer.parseInt(row[3]);
        int baseSpA  = Integer.parseInt(row[4]);
        int baseSpD  = Integer.parseInt(row[5]);
        int baseSpd  = Integer.parseInt(row[6]);

        // Stash base stats on the Pokemon so it can recalc on level-up later.
        pokemon.baseHP        = baseHP;
        pokemon.baseAttack    = baseAtk;
        pokemon.baseDefense   = baseDef;
        pokemon.baseSpAttack  = baseSpA;
        pokemon.baseSpDef     = baseSpD;
        pokemon.baseSpeed     = baseSpd;

        // IVs must be assigned before scaling so they fold into the formula.
        assignIVs(pokemon);

        // Standard Pokemon stat formula (no nature, EVs default 0):
        //   HP    = floor( (2*base + IV + EV/4) * level / 100 ) + level + 10
        //   other = floor( (2*base + IV + EV/4) * level / 100 ) + 5
        pokemon.hp        = scaledHP(baseHP,  pokemon.ivHP,       pokemon.evHP,       level);
        pokemon.attack    = scaledStat(baseAtk, pokemon.ivAttack,   pokemon.evAttack,   level);
        pokemon.defense   = scaledStat(baseDef, pokemon.ivDefense,  pokemon.evDefense,  level);
        pokemon.spAttack  = scaledStat(baseSpA, pokemon.ivSpAttack, pokemon.evSpAttack, level);
        pokemon.spDef     = scaledStat(baseSpD, pokemon.ivSpDef,    pokemon.evSpDef,    level);
        pokemon.speed     = scaledStat(baseSpd, pokemon.ivSpeed,    pokemon.evSpeed,    level);

        pokemon.maxHP = pokemon.hp;
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

        pokemon.moves = Moves.getMoves(pokemon.type1, pokemon.type2, level);

        pokemon.captureRate = Integer.parseInt(row[9]);
        pokemon.experienceGrowth = Integer.parseInt(row[10]);
        // Seed totalExp to the curve value for the starting level so the pokemon doesn't
        // immediately level up or owe XP. Subsequent gains accumulate from here.
        pokemon.totalExp = ExpCurves.expAtLevel(level, pokemon.experienceGrowth);

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

        pokemon.isLegendary = "YES".equalsIgnoreCase(row[21]);

        // Evolution target (level-up only). Blank/0 means it doesn't evolve.
        pokemon.evolvesInto = row.length > 22 ? row[22].trim() : "";
        try {
            pokemon.evolveLevel = row.length > 23 ? Integer.parseInt(row[23].trim()) : 0;
        } catch (NumberFormatException e) {
            pokemon.evolveLevel = 0;
        }

        assignAbility(pokemon, row);

        pokemon.shiny = RNG.nextDouble() <= 0.0005;
        // IVs were already assigned above before the stat scaling step.
    }

    private static int scaledHP(int base, int iv, int ev, int level) {
        return ((2 * base + iv + ev / 4) * level / 100) + level + 10;
    }

    private static int scaledStat(int base, int iv, int ev, int level) {
        return ((2 * base + iv + ev / 4) * level / 100) + 5;
    }

    private void assignAbility(Pokemon pokemon, String[] row) {
        int abilityCount = row.length - 24;
        if (abilityCount <= 1) {
            pokemon.ability1 = abilityCount == 1 ? row[24] : null;
            pokemon.ability = pokemon.ability1;
            return;
        }
        if (abilityCount == 2) {
            // Two listed: first is normal, second is hidden (10% chance)
            pokemon.ability1 = row[24];
            pokemon.hiddenAbility = row[25];
            pokemon.ability = RNG.nextDouble() < 0.1 ? pokemon.hiddenAbility : pokemon.ability1;
            return;
        }
        // Three or more: two normals (45% each) + hidden (10%)
        pokemon.ability1 = row[24];
        pokemon.ability2 = row[25];
        pokemon.hiddenAbility = row[26];
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
