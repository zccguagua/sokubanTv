package com.sokbantv.game;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One immutable Sokoban level parsed from the XSB text in assets/levels.txt.
 *
 * <pre>
 *   #  wall      .  goal      $  box
 *   @  player    *  box on goal      +  player on goal
 * </pre>
 */
public final class Level {

    /** Cells the player can walk on. */
    public static final byte FLOOR = 0;
    public static final byte WALL = 1;
    public static final byte GOAL = 2;
    /** Floor cell outside the room; treated as wall and not drawn. */
    public static final byte VOID = 3;

    private static final Pattern PAR_PATTERN =
            Pattern.compile("par\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public final int index;
    public final int width;
    public final int height;
    /** Minimum number of pushes for an optimal solution, or 0 if unknown. */
    public final int par;

    private final byte[] tiles;

    /** Starting box positions. */
    public final int[] boxX;
    public final int[] boxY;
    public final int playerX;
    public final int playerY;

    private Level(int index, int width, int height, int par, byte[] tiles,
                  int[] boxX, int[] boxY, int playerX, int playerY) {
        this.index = index;
        this.width = width;
        this.height = height;
        this.par = par;
        this.tiles = tiles;
        this.boxX = boxX;
        this.boxY = boxY;
        this.playerX = playerX;
        this.playerY = playerY;
    }

    public byte tile(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return WALL;
        }
        return tiles[y * width + x];
    }

    public boolean isWall(int x, int y) {
        byte tile = tile(x, y);
        return tile != FLOOR && tile != GOAL;
    }

    public boolean isGoal(int x, int y) {
        return tile(x, y) == GOAL;
    }

    public int boxCount() {
        return boxX.length;
    }

    // ----------------------------------------------------------------------
    // parsing
    // ----------------------------------------------------------------------
    public static List<Level> parseAll(String text) {
        List<Level> levels = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        String note = "";

        for (String raw : text.split("\r?\n")) {
            if (raw.startsWith(";")) {
                if (!rows.isEmpty()) {
                    addLevel(levels, rows, note);
                    rows.clear();
                }
                note = raw.substring(1).trim();
                continue;
            }
            if (raw.trim().isEmpty()) {
                if (!rows.isEmpty()) {
                    addLevel(levels, rows, note);
                    rows.clear();
                }
                continue;
            }
            rows.add(raw);
        }
        if (!rows.isEmpty()) {
            addLevel(levels, rows, note);
        }
        return levels;
    }

    private static void addLevel(List<Level> out, List<String> rows, String note) {
        int height = rows.size();
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        if (width == 0) {
            return;
        }

        byte[] tiles = new byte[width * height];
        List<int[]> boxes = new ArrayList<>();
        int playerX = -1;
        int playerY = -1;

        for (int y = 0; y < height; y++) {
            String row = rows.get(y);
            for (int x = 0; x < width; x++) {
                char c = x < row.length() ? row.charAt(x) : ' ';
                byte tile;
                switch (c) {
                    case '#':
                        tile = WALL;
                        break;
                    case '.':
                        tile = GOAL;
                        break;
                    case '*':
                        tile = GOAL;
                        boxes.add(new int[]{x, y});
                        break;
                    case '+':
                        tile = GOAL;
                        playerX = x;
                        playerY = y;
                        break;
                    case '$':
                        tile = FLOOR;
                        boxes.add(new int[]{x, y});
                        break;
                    case '@':
                        tile = FLOOR;
                        playerX = x;
                        playerY = y;
                        break;
                    default:
                        tile = FLOOR;
                        break;
                }
                tiles[y * width + x] = tile;
            }
        }

        if (playerX < 0 || boxes.isEmpty()) {
            return;                            // malformed, skip it
        }

        markOutside(tiles, width, height);

        int par = 0;
        Matcher matcher = PAR_PATTERN.matcher(note);
        if (matcher.find()) {
            par = Integer.parseInt(matcher.group(1));
        }

        int count = boxes.size();
        int[] boxX = new int[count];
        int[] boxY = new int[count];
        for (int i = 0; i < count; i++) {
            boxX[i] = boxes.get(i)[0];
            boxY[i] = boxes.get(i)[1];
        }
        out.add(new Level(out.size(), width, height, par, tiles,
                boxX, boxY, playerX, playerY));
    }

    /**
     * Marks floor cells that lie outside the room as VOID, so that irregular
     * hand written levels are not surrounded by drawn floor.
     */
    private static void markOutside(byte[] tiles, int width, int height) {
        boolean[] seen = new boolean[width * height];
        ArrayList<Integer> queue = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            push(tiles, seen, queue, x, 0, width, height);
            push(tiles, seen, queue, x, height - 1, width, height);
        }
        for (int y = 0; y < height; y++) {
            push(tiles, seen, queue, 0, y, width, height);
            push(tiles, seen, queue, width - 1, y, width, height);
        }
        for (int i = 0; i < queue.size(); i++) {
            int cell = queue.get(i);
            int x = cell % width;
            int y = cell / width;
            push(tiles, seen, queue, x + 1, y, width, height);
            push(tiles, seen, queue, x - 1, y, width, height);
            push(tiles, seen, queue, x, y + 1, width, height);
            push(tiles, seen, queue, x, y - 1, width, height);
        }
        for (int i = 0; i < seen.length; i++) {
            if (seen[i]) {
                tiles[i] = VOID;
            }
        }
    }

    private static void push(byte[] tiles, boolean[] seen, List<Integer> queue,
                             int x, int y, int width, int height) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int cell = y * width + x;
        if (seen[cell] || tiles[cell] == WALL) {
            return;
        }
        seen[cell] = true;
        queue.add(cell);
    }
}
