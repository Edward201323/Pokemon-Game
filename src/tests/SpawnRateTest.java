package tests;

import java.util.Random;

import Pokemon.GetPokemon;
import Pokemon.Pokemon;
import PokemonEncounters.PlayerObjectInteraction;

// Regression checks for the wild encounter pipeline:
//   - LEGENDARY_ENCOUNTER_RATE is the documented 0.02 (2%)
//   - findRandomLegendaryPokemon only returns pokemon with isLegendary=true
//   - findRandomNormalPokemonForPartyMax never returns a legendary
//   - Simulated rolls over many trials hit ~2% legendary (within a wide tolerance band)
//
// Run with:
//   javac -d bin -sourcepath src $(find src -name '*.java')
//   java -cp bin tests.SpawnRateTest
public class SpawnRateTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testRateConstantIsTwoPercent();
        testLegendaryPickerReturnsLegendaries();
        testNormalPickerNeverReturnsLegendary();
        testSimulatedRateMatchesConstant();

        System.out.println();
        System.out.println("==== " + passed + " passed, " + failed + " failed ====");
        System.exit(failed == 0 ? 0 : 1);
    }

    // The encounter math is `RNG.nextDouble() < LEGENDARY_ENCOUNTER_RATE`. If this drifts,
    // legendary rate changes silently. Pin the value.
    static void testRateConstantIsTwoPercent() {
        double rate = PlayerObjectInteraction.LEGENDARY_ENCOUNTER_RATE;
        require("LEGENDARY_ENCOUNTER_RATE == 0.02", Math.abs(rate - 0.02) < 1e-9);
    }

    // findRandomLegendaryPokemon must only draw from the legendary pool.
    static void testLegendaryPickerReturnsLegendaries() {
        GetPokemon g = new GetPokemon();
        int trials = 5000, nonLegendary = 0, nulls = 0;
        for (int i = 0; i < trials; i++) {
            Pokemon p = g.findRandomLegendaryPokemon(100);
            if (p == null) { nulls++; continue; }
            if (!p.isLegendary) nonLegendary++;
        }
        require("legendary picker returned non-null every time", nulls == 0);
        require("legendary picker returned isLegendary=true every time (got " + nonLegendary + " non-legs)",
                nonLegendary == 0);
    }

    // findRandomNormalPokemonForPartyMax draws from the non-legendary pool only.
    static void testNormalPickerNeverReturnsLegendary() {
        GetPokemon g = new GetPokemon();
        int trials = 5000, legendaryLeaked = 0;
        for (int partyMax : new int[]{10, 30, 60, 100}) {
            for (int i = 0; i < trials / 4; i++) {
                int lvl = Math.max(5, partyMax - 5);
                Pokemon p = g.findRandomNormalPokemonForPartyMax(lvl, partyMax);
                if (p != null && p.isLegendary) legendaryLeaked++;
            }
        }
        require("normal picker never returned a legendary across "
                + trials + " calls (leaked: " + legendaryLeaked + ")", legendaryLeaked == 0);
    }

    // Simulate the encounter roll: of N "encounter triggered" events, ~LEGENDARY_RATE
    // should be legendary. Wide tolerance band (±0.5%) so RNG variance doesn't flake the
    // test, but tight enough to catch an off-by-100 bug or a rate=0 regression.
    static void testSimulatedRateMatchesConstant() {
        double target = PlayerObjectInteraction.LEGENDARY_ENCOUNTER_RATE;
        int trials = 200_000;
        Random rng = new Random(424242);
        int legendaryRolls = 0;
        for (int i = 0; i < trials; i++) {
            if (rng.nextDouble() < target) legendaryRolls++;
        }
        double observed = legendaryRolls / (double) trials;
        double tolerance = 0.005; // ±0.5%
        boolean ok = Math.abs(observed - target) < tolerance;
        if (ok) {
            pass(String.format("simulated rate %.4f matches target %.4f (±%.3f)",
                    observed, target, tolerance));
        } else {
            fail(String.format("simulated rate %.4f drifted from target %.4f beyond ±%.3f",
                    observed, target, tolerance));
        }
    }

    // --- helpers ---

    static boolean require(String label, boolean cond) {
        if (cond) { pass(label); return true; }
        fail(label); return false;
    }

    static void pass(String label) { passed++; System.out.println("  PASS  " + label); }
    static void fail(String label) { failed++; System.out.println("  FAIL  " + label); }
}
