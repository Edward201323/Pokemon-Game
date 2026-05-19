package tests;

import java.util.ArrayList;
import java.util.List;

import Pokemon.GetPokemon;
import Pokemon.Move;
import Pokemon.Moves;
import Pokemon.Pokemon;

// Regression check for the Move Tutor's swap action:
//   selectedPokemon.moves.set(slotIndex, newMove)
// is what MoveTutor.handleSlotListInput executes. This test verifies the swap is a real
// mutation of the underlying list (not a visual-only / shadow copy), and that any other
// reference to the same Pokemon sees the new move.
//
// Run with:
//   javac -d bin -sourcepath src $(find src -name '*.java')
//   java -cp bin tests.MoveSwapTest
public class MoveSwapTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testMovesListIsMutable();
        testSetReplacesByReference();
        testReferenceAliasingPersistsSwap();
        testRepeatedSwaps();
        testLearnableExcludesAlreadyKnown();

        System.out.println();
        System.out.println("==== " + passed + " passed, " + failed + " failed ====");
        System.exit(failed == 0 ? 0 : 1);
    }

    // --- tests ---

    // Mewtwo's moves field must be a mutable List; the tutor would otherwise throw on set().
    static void testMovesListIsMutable() {
        Pokemon p = new GetPokemon().findPokemon("Mewtwo", 50);
        require("Mewtwo loaded", p != null);
        if (p == null) return;
        require("Mewtwo has moves", p.moves != null && !p.moves.isEmpty());
        try {
            Move sentinel = new Move("__TEST__", "psychic", 1, 100, true, 1);
            p.moves.set(0, sentinel);
            require("moves.set succeeded (list is mutable)", p.moves.get(0) == sentinel);
        } catch (UnsupportedOperationException e) {
            fail("moves.set threw UnsupportedOperationException: list is immutable");
        }
    }

    // The exact line the tutor runs: replace slot K with a new Move. Verify it actually
    // takes effect when we re-read p.moves.
    static void testSetReplacesByReference() {
        Pokemon p = new GetPokemon().findPokemon("Charizard", 50);
        if (!require("Charizard loaded", p != null)) return;
        if (!require("Charizard has at least 1 move", p.moves != null && p.moves.size() >= 1)) return;
        Move original = p.moves.get(0);
        Move replacement = new Move("__SWAP__", "fire", 99, 95, false, 50);
        p.moves.set(0, replacement);
        require("slot 0 is the replacement Move (==, same reference)",
                p.moves.get(0) == replacement);
        require("slot 0 is no longer the original",
                p.moves.get(0) != original);
    }

    // The MoveTutor reaches the active pokemon via gp.playerPokemon.pokemonEquipped.get(idx).
    // Verify that holding a separate reference to the same Pokemon (the way BattleSystem and
    // OpenPlayerInventory both do) sees the swap. This is the "not a visual change" check.
    static void testReferenceAliasingPersistsSwap() {
        Pokemon p = new GetPokemon().findPokemon("Blastoise", 50);
        if (!require("Blastoise loaded", p != null)) return;
        // Build a stand-in party — same reference pattern the game uses.
        List<Pokemon> party = new ArrayList<>();
        party.add(p);
        Pokemon partyRef = party.get(0); // simulates pokemonEquipped.get(idx)
        Move replacement = new Move("__SWAP2__", "water", 80, 100, true, 50);
        partyRef.moves.set(2 % partyRef.moves.size(), replacement);

        // Re-read from a different alias and verify same content.
        Pokemon alias = party.get(0);
        require("alias sees the same Move reference",
                alias.moves.get(2 % alias.moves.size()) == replacement);
        require("original variable sees the same Move reference",
                p.moves.get(2 % p.moves.size()) == replacement);
    }

    // Swap, then swap again. The second swap should overwrite the first.
    static void testRepeatedSwaps() {
        Pokemon p = new GetPokemon().findPokemon("Pikachu", 40);
        if (!require("Pikachu loaded", p != null)) return;
        if (p.moves == null || p.moves.isEmpty()) { fail("Pikachu has no moves"); return; }
        Move first  = new Move("__A__", "electric", 50, 100, true, 1);
        Move second = new Move("__B__", "electric", 90, 100, false, 30);
        p.moves.set(0, first);
        require("first swap landed", p.moves.get(0) == first);
        p.moves.set(0, second);
        require("second swap overwrites first", p.moves.get(0) == second);
        require("first is no longer in slot 0", p.moves.get(0) != first);
    }

    // The tutor's learnable list must not offer moves the pokemon already knows.
    static void testLearnableExcludesAlreadyKnown() {
        Pokemon p = new GetPokemon().findPokemon("Snorlax", 50);
        if (!require("Snorlax loaded", p != null)) return;
        if (p.moves == null || p.moves.isEmpty()) { fail("Snorlax has no moves"); return; }
        List<Move> learnable = Moves.getLearnableMoves(p);
        for (Move known : p.moves) {
            if (known == null) continue;
            for (Move offered : learnable) {
                if (offered.name.equals(known.name)) {
                    fail("getLearnableMoves offered '" + offered.name
                         + "', which is already in the pokemon's moveset");
                    return;
                }
            }
        }
        pass("getLearnableMoves excludes the " + p.moves.size() + " already-known move(s)");
    }

    // --- helpers ---

    static boolean require(String label, boolean cond) {
        if (cond) { pass(label); return true; }
        fail(label); return false;
    }

    static void pass(String label) {
        passed++;
        System.out.println("  PASS  " + label);
    }

    static void fail(String label) {
        failed++;
        System.out.println("  FAIL  " + label);
    }
}
