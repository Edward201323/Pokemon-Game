package Pokemon;

public class Pokemon {
    public String name;

    public int currentHP, currentAttack, currentDefense, currentSpAttack, currentSpDef, currentSpeed, currentEvasion, currentAccuracy;

    public int hp, attack, defense, spAttack, spDef, speed;

    public int maxHP;

    public java.util.List<Move> moves;

    public String type1, type2;

    public String currentType1, currentType2;

    public int ivHP, ivAttack, ivDefense, ivSpAttack, ivSpDef, ivSpeed;

    public int evHP, evAttack, evDefense, evSpAttack, evSpDef, evSpeed;

    public int captureRate;

    public boolean isLegendary;

    public double percentMale;
    public String gender;
    
    public int experienceGrowth;

    public int happiness;

    public int pokedexNumber;

    public String ability1, ability2, hiddenAbility;
    public String ability;

    public int expGiven;

    public int hpEVsGiven, attackEVsGiven, defenseEVsGiven, spAttackEVsGiven, spDefEVsGiven, speedEVsGiven;
    
    public int level;
    // Running total of accumulated experience points; compared against the species'
    // experience curve to decide when to level up.
    public int totalExp;

    // Base stats from the CSV (cols 1-6). Kept around so we can re-derive scaled stats
    // (hp/attack/etc.) on level-up via recalcStats().
    public int baseHP, baseAttack, baseDefense, baseSpAttack, baseSpDef, baseSpeed;

    public boolean shiny;

    public String NameInFile;

    public String NameInFileGif;

    // Re-derive level-scaled stats from base + IV + EV + level using the same formulas
    // GetPokemon uses on construction. Called after a level-up so the pokemon actually
    // gets stronger. currentHP grows by the same delta as maxHP so leveling at 1 HP
    // doesn't leave the pokemon fainted.
    public void recalcStats() {
        int oldMaxHP = maxHP;
        hp        = ((2 * baseHP        + ivHP       + evHP       / 4) * level / 100) + level + 10;
        attack    = ((2 * baseAttack    + ivAttack   + evAttack   / 4) * level / 100) + 5;
        defense   = ((2 * baseDefense   + ivDefense  + evDefense  / 4) * level / 100) + 5;
        spAttack  = ((2 * baseSpAttack  + ivSpAttack + evSpAttack / 4) * level / 100) + 5;
        spDef     = ((2 * baseSpDef     + ivSpDef    + evSpDef    / 4) * level / 100) + 5;
        speed     = ((2 * baseSpeed     + ivSpeed    + evSpeed    / 4) * level / 100) + 5;
        maxHP = hp;
        if (maxHP > oldMaxHP) currentHP += (maxHP - oldMaxHP);
        // In-battle modified stats reset to the new base values.
        currentAttack   = attack;
        currentDefense  = defense;
        currentSpAttack = spAttack;
        currentSpDef    = spDef;
        currentSpeed    = speed;
    }
}
