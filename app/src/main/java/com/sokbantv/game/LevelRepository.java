package com.sokbantv.game;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Loads the bundled levels once and keeps them in memory. */
public final class LevelRepository {

    private static final String ASSET = "levels.txt";

    private static LevelRepository instance;

    private final List<Level> levels;

    private LevelRepository(List<Level> levels) {
        this.levels = levels;
    }

    public static synchronized LevelRepository get(Context context) {
        if (instance == null) {
            instance = new LevelRepository(load(context.getApplicationContext()));
        }
        return instance;
    }

    public List<Level> levels() {
        return Collections.unmodifiableList(levels);
    }

    public int count() {
        return levels.size();
    }

    public Level level(int index) {
        if (levels.isEmpty()) {
            return null;
        }
        return levels.get(Math.max(0, Math.min(index, levels.size() - 1)));
    }

    private static List<Level> load(Context context) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(ASSET), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + ASSET, e);
        }
        return Level.parseAll(text.toString());
    }
}
