package com.sokbantv.game;

import java.util.ArrayDeque;
import java.util.Deque;

/** Mutable game state: player, boxes, counters, undo history. */
public final class GameEngine {

    private static final int MAX_HISTORY = 4096;

    private final Level level;

    private int playerX;
    private int playerY;
    private final int[] boxX;
    private final int[] boxY;

    private int moves;
    private int pushes;

    /** Each entry: playerX, playerY, moves, pushes, then box coordinates. */
    private final Deque<int[]> history = new ArrayDeque<>();

    public GameEngine(Level level) {
        this.level = level;
        this.playerX = level.playerX;
        this.playerY = level.playerY;
        this.boxX = level.boxX.clone();
        this.boxY = level.boxY.clone();
    }

    public Level level() {
        return level;
    }

    public int playerX() {
        return playerX;
    }

    public int playerY() {
        return playerY;
    }

    public int boxCount() {
        return boxX.length;
    }

    public int boxX(int index) {
        return boxX[index];
    }

    public int boxY(int index) {
        return boxY[index];
    }

    public int moves() {
        return moves;
    }

    public int pushes() {
        return pushes;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public int boxIndexAt(int x, int y) {
        for (int i = 0; i < boxX.length; i++) {
            if (boxX[i] == x && boxY[i] == y) {
                return i;
            }
        }
        return -1;
    }

    public int boxCountOnGoals() {
        int count = 0;
        for (int i = 0; i < boxX.length; i++) {
            if (level.isGoal(boxX[i], boxY[i])) {
                count++;
            }
        }
        return count;
    }

    /**
     * Tries to walk one cell. Returns true when something actually moved, so
     * the caller can animate / play a sound.
     */
    public boolean move(int dx, int dy) {
        int nextX = playerX + dx;
        int nextY = playerY + dy;
        if (level.isWall(nextX, nextY)) {
            return false;
        }

        int box = boxIndexAt(nextX, nextY);
        if (box >= 0) {
            int targetX = nextX + dx;
            int targetY = nextY + dy;
            if (level.isWall(targetX, targetY) || boxIndexAt(targetX, targetY) >= 0) {
                return false;
            }
            pushHistory();
            boxX[box] = targetX;
            boxY[box] = targetY;
            playerX = nextX;
            playerY = nextY;
            moves++;
            pushes++;
            return true;
        }

        pushHistory();
        playerX = nextX;
        playerY = nextY;
        moves++;
        return true;
    }

    public boolean undo() {
        int[] snapshot = history.pollFirst();
        if (snapshot == null) {
            return false;
        }
        playerX = snapshot[0];
        playerY = snapshot[1];
        moves = snapshot[2];
        pushes = snapshot[3];
        for (int i = 0; i < boxX.length; i++) {
            boxX[i] = snapshot[4 + i * 2];
            boxY[i] = snapshot[5 + i * 2];
        }
        return true;
    }

    public boolean isSolved() {
        for (int i = 0; i < boxX.length; i++) {
            if (!level.isGoal(boxX[i], boxY[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cheap deadlock check: a box that is not on a goal and is wedged into a
     * wall corner can never be pushed again, so the level is already lost.
     */
    public boolean isBoxStuck(int index) {
        int x = boxX[index];
        int y = boxY[index];
        if (level.isGoal(x, y)) {
            return false;
        }
        boolean up = level.isWall(x, y - 1);
        boolean down = level.isWall(x, y + 1);
        boolean left = level.isWall(x - 1, y);
        boolean right = level.isWall(x + 1, y);
        if ((up || down) && (left || right)) {
            return true;
        }
        // box against a wall row and blocked by other boxes on both sides
        if (up || down) {
            return (isBoxOrWall(x - 1, y) && isBoxOrWall(x + 1, y));
        }
        if (left || right) {
            return (isBoxOrWall(x, y - 1) && isBoxOrWall(x, y + 1));
        }
        return false;
    }

    private boolean isBoxOrWall(int x, int y) {
        return level.isWall(x, y) || boxIndexAt(x, y) >= 0;
    }

    public boolean anyBoxStuck() {
        for (int i = 0; i < boxX.length; i++) {
            if (isBoxStuck(i)) {
                return true;
            }
        }
        return false;
    }

    private void pushHistory() {
        int[] snapshot = new int[4 + boxX.length * 2];
        snapshot[0] = playerX;
        snapshot[1] = playerY;
        snapshot[2] = moves;
        snapshot[3] = pushes;
        for (int i = 0; i < boxX.length; i++) {
            snapshot[4 + i * 2] = boxX[i];
            snapshot[5 + i * 2] = boxY[i];
        }
        history.push(snapshot);
        if (history.size() > MAX_HISTORY) {
            history.pollLast();
        }
    }
}
