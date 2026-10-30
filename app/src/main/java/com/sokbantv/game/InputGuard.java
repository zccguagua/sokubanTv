package com.sokbantv.game;

import java.util.HashMap;
import java.util.Map;

/**
 * Filters key events so that a flaky TV remote cannot move the player twice for
 * one button press.
 *
 * Some remotes send two {@code ACTION_DOWN} events for a single press, and
 * Android only de-duplicates them when they share the exact same timestamp.
 * The rule here is time based and per key: the same key is accepted at most
 * once per window, so phantom duplicates (a few milliseconds apart) collapse
 * into one move while deliberate taps and auto-repeat still work.
 *
 * {@code nowMs} is passed in instead of read from the clock so the logic can be
 * unit tested without Android.
 */
public final class InputGuard {

    /** Window for good remotes: blocks phantom duplicates, keeps play snappy. */
    public static final long STANDARD_WINDOW_MS = 70;
    /** Window for very jittery remotes: at most one move per 300 ms. */
    public static final long STRICT_WINDOW_MS = 300;

    private final long windowMs;
    private final Map<Integer, Long> lastAccepted = new HashMap<>();

    public InputGuard(long windowMs) {
        this.windowMs = windowMs;
    }

    public long windowMs() {
        return windowMs;
    }

    /**
     * @param keyCode the key that went down
     * @param nowMs   current uptime in milliseconds
     * @return true when the caller should act on this event
     */
    public boolean accept(int keyCode, long nowMs) {
        Long last = lastAccepted.get(keyCode);
        if (last != null) {
            long elapsed = nowMs - last;
            // A negative value means the clock or the event time jumped
            // backwards; treat it as a duplicate rather than firing twice.
            if (elapsed < windowMs) {
                return false;
            }
        }
        lastAccepted.put(keyCode, nowMs);
        return true;
    }

    /** Forgets all history, e.g. when a dialog closes and play resumes. */
    public void reset() {
        lastAccepted.clear();
    }
}
