import com.sokbantv.game.InputGuard;

/**
 * Checks the remote de-duplication rules used by GameActivity.
 * Run with: java -cp out InputGuardTest
 */
public final class InputGuardTest {

    private static final int UP = 19;
    private static final int LEFT = 21;

    private static int failures;

    public static void main(String[] args) {
        standardWindow();
        strictWindow();
        perKeyAndReset();

        System.out.println(failures == 0
                ? "INPUT GUARD OK"
                : failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** A good remote: phantom duplicates collapse, real taps survive. */
    private static void standardWindow() {
        InputGuard guard = new InputGuard(InputGuard.STANDARD_WINDOW_MS);

        // one press arrives twice within a couple of milliseconds
        check("phantom pair", guard.accept(UP, 1000), true);
        check("phantom pair", guard.accept(UP, 1003), false);
        check("phantom pair (same stamp)", guard.accept(UP, 1003), false);

        // deliberate taps 150 ms apart both move
        check("two taps", guard.accept(UP, 1150), true);
        check("two taps", guard.accept(UP, 1300), true);

        // holding the key: android repeats every ~50 ms
        int moves = 0;
        for (long t = 1400; t <= 2000; t += 50) {
            if (guard.accept(LEFT, t)) {
                moves++;
            }
        }
        // 600 ms of repeats at a 70 ms floor cannot exceed ~9 moves
        check("hold repeats (" + moves + " in 600ms)", moves <= 9 && moves >= 7, true);
    }

    /** A jittery remote: at most one move per 300 ms. */
    private static void strictWindow() {
        InputGuard guard = new InputGuard(InputGuard.STRICT_WINDOW_MS);

        check("strict 1st", guard.accept(UP, 5000), true);
        check("strict 150ms later", guard.accept(UP, 5150), false);
        check("strict 299ms later", guard.accept(UP, 5299), false);
        check("strict 300ms later", guard.accept(UP, 5300), true);

        int moves = 0;
        for (long t = 5400; t <= 6300; t += 50) {
            if (guard.accept(UP, t)) {
                moves++;
            }
        }
        check("strict hold (" + moves + " in 900ms)", moves <= 4, true);
    }

    private static void perKeyAndReset() {
        InputGuard guard = new InputGuard(InputGuard.STANDARD_WINDOW_MS);

        check("left first", guard.accept(LEFT, 7000), true);
        // a different key is not blocked by the previous one
        check("up after left", guard.accept(UP, 7005), true);
        check("left once more, too soon", guard.accept(LEFT, 7010), false);

        guard.reset();
        check("after reset", guard.accept(LEFT, 7015), true);
    }

    private static void check(String name, boolean actual, boolean expected) {
        if (actual != expected) {
            failures++;
            System.out.println("  FAIL " + name + ": expected " + expected + " but was " + actual);
        } else {
            System.out.println("  ok   " + name + " -> " + actual);
        }
    }
}
