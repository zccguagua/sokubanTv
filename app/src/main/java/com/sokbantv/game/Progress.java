package com.sokbantv.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Per level best results, remembered across launches. */
public final class Progress {

    private static final String PREFS = "sokoban_progress";
    private static final String KEY_BEST_MOVES = "best_moves_";
    private static final String KEY_BEST_PUSHES = "best_pushes_";
    private static final String KEY_LAST_LEVEL = "last_level";

    private final SharedPreferences prefs;

    public Progress(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Best step count for a level, or 0 when never finished. */
    public int bestMoves(int levelIndex) {
        return prefs.getInt(KEY_BEST_MOVES + levelIndex, 0);
    }

    public int bestPushes(int levelIndex) {
        return prefs.getInt(KEY_BEST_PUSHES + levelIndex, 0);
    }

    public boolean isCompleted(int levelIndex) {
        return bestMoves(levelIndex) > 0;
    }

    /** Stores a win, keeping only the better result. */
    public void recordWin(int levelIndex, int moves, int pushes) {
        int best = bestMoves(levelIndex);
        SharedPreferences.Editor editor = prefs.edit();
        if (best == 0 || moves < best) {
            editor.putInt(KEY_BEST_MOVES + levelIndex, moves);
            editor.putInt(KEY_BEST_PUSHES + levelIndex, pushes);
        }
        editor.apply();
    }

    public int completedCount(int total) {
        int count = 0;
        for (int i = 0; i < total; i++) {
            if (isCompleted(i)) {
                count++;
            }
        }
        return count;
    }

    public int lastLevel() {
        return prefs.getInt(KEY_LAST_LEVEL, 0);
    }

    public void setLastLevel(int index) {
        prefs.edit().putInt(KEY_LAST_LEVEL, index).apply();
    }

    public void reset() {
        prefs.edit().clear().apply();
    }
}
