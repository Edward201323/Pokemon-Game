package Pokemon;

// Pokemon experience curves. The CSV's `experience_growth` column stores the total EXP
// a pokemon of that species needs to reach level 100 (e.g. 1,000,000 = Medium Fast,
// 800,000 = Fast, 1,250,000 = Slow). We approximate each curve as a single n^3 scaling
// of that max — which is *exactly* correct for Fast / Medium Fast / Slow (the three
// most common curves are all `n^3` with different coefficients) and a close enough
// approximation for the others.
public final class ExpCurves {
    private ExpCurves() {}

    public static int expAtLevel(int level, int growthCurveMax) {
        if (level <= 1) return 0;
        if (level >= 100) return growthCurveMax;
        return (int) (growthCurveMax * Math.pow(level / 100.0, 3));
    }

    // Gen-2-style EXP yield: (base_yield * defeated_level) / 7, with a floor of 1.
    public static int expGained(int baseYield, int defeatedLevel) {
        return expGained(baseYield, defeatedLevel, 50);
    }

    // Same formula, but boosted by a per-receiver multiplier that ramps from 2x at low
    // levels to 5x at level 100. Because this is a wild-encounter-only game (no trainers
    // = no big EXP fights), the boost keeps higher levels from feeling like a slog.
    public static int expGained(int baseYield, int defeatedLevel, int gainerLevel) {
        int clamped = Math.max(1, Math.min(100, gainerLevel));
        double multiplier = 2.0 + (clamped / 100.0) * 3.0; // 2.03 .. 5.00
        int base = Math.max(1, (baseYield * defeatedLevel) / 7);
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    // 0..1 fraction of the way from the current level threshold to the next. Returns 1.0
    // once the pokemon is level 100 (so the bar reads "full / maxed").
    public static double levelProgress(Pokemon p) {
        if (p == null || p.level >= 100) return 1.0;
        int curMax = p.experienceGrowth;
        int floor = expAtLevel(p.level, curMax);
        int ceil  = expAtLevel(p.level + 1, curMax);
        int range = ceil - floor;
        if (range <= 0) return 1.0;
        int progress = p.totalExp - floor;
        return Math.max(0.0, Math.min(1.0, progress / (double) range));
    }
}
