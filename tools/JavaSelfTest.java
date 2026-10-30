import com.sokbantv.game.GameEngine;
import com.sokbantv.game.Level;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the real Level parser and GameEngine over assets/levels.txt.
 *
 * For every level it checks the structure, searches for a solution, replays
 * that solution through GameEngine and finally undoes everything again. This
 * catches parser mistakes and engine regressions without a device.
 */
public final class JavaSelfTest {

    private static final int[][] DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private static int failures;

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "app/src/main/assets/levels.txt";
        String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        List<Level> levels = Level.parseAll(text);
        System.out.println("parsed levels: " + levels.size());
        if (levels.isEmpty()) {
            System.out.println("FAIL: no levels parsed");
            System.exit(1);
        }

        for (Level level : levels) {
            check(level);
        }

        System.out.println(failures == 0
                ? "ALL LEVELS OK (" + levels.size() + ")"
                : failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(Level level) {
        int label = level.index + 1;
        int goals = 0;
        for (int y = 0; y < level.height; y++) {
            for (int x = 0; x < level.width; x++) {
                if (level.isGoal(x, y)) {
                    goals++;
                }
            }
        }
        if (goals != level.boxCount()) {
            fail(label, "goals " + goals + " != boxes " + level.boxCount());
            return;
        }
        if (level.isWall(level.playerX, level.playerY)) {
            fail(label, "player starts inside a wall");
            return;
        }

        Board board = new Board(level);
        List<int[]> path = solve(board);
        if (path == null) {
            fail(label, "unsolvable");
            return;
        }

        GameEngine engine = new GameEngine(level);
        for (int[] move : path) {
            if (!engine.move(move[0], move[1])) {
                fail(label, "replay rejected a move");
                return;
            }
        }
        if (!engine.isSolved()) {
            fail(label, "engine did not report solved after replay");
            return;
        }

        int moves = engine.moves();
        int pushes = engine.pushes();
        while (engine.canUndo()) {
            engine.undo();
        }
        boolean backToStart = engine.moves() == 0 && engine.pushes() == 0
                && engine.playerX() == level.playerX
                && engine.playerY() == level.playerY;
        for (int i = 0; i < level.boxCount() && backToStart; i++) {
            backToStart = engine.boxX(i) == level.boxX[i] && engine.boxY(i) == level.boxY[i];
        }
        if (!backToStart) {
            fail(label, "undo did not restore the start position");
            return;
        }
        if (level.par > 0 && pushes != level.par) {
            fail(label, "pushes " + pushes + " != par " + level.par);
            return;
        }

        System.out.printf("  level %-3d %dx%-2d boxes %d  %3d moves / %3d pushes (par %d)%n",
                label, level.width, level.height, level.boxCount(),
                moves, pushes, level.par);
    }

    private static void fail(int level, String message) {
        failures++;
        System.out.println("  level " + level + " FAIL: " + message);
    }

    // ------------------------------------------------------------------
    // push based BFS; each edge is one push, so the result is optimal
    // ------------------------------------------------------------------
    private static List<int[]> solve(Board board) {
        int[] startBoxes = board.startBoxes.clone();
        int startPlayer = board.startPlayer;

        Map<String, Node> seen = new HashMap<>();
        Deque<Node> queue = new ArrayDeque<>();
        Node start = new Node(startBoxes, startPlayer, null, null, 0);
        seen.put(key(start, board), start);
        queue.add(start);

        int nodes = 0;
        while (!queue.isEmpty() && nodes < 400_000) {
            Node node = queue.poll();
            nodes++;

            if (board.solved(node.boxes)) {
                return buildPath(node);
            }

            boolean[] blocked = new boolean[board.size];
            for (int box : node.boxes) {
                blocked[box] = true;
            }

            int[] prev = new int[board.size];
            Arrays.fill(prev, -1);
            Deque<Integer> walk = new ArrayDeque<>();
            prev[node.player] = node.player;
            walk.add(node.player);
            List<Integer> reachable = new ArrayList<>();
            while (!walk.isEmpty()) {
                int cell = walk.poll();
                reachable.add(cell);
                int x = cell % board.width;
                int y = cell / board.width;
                for (int[] d : DIRS) {
                    int nx = x + d[0];
                    int ny = y + d[1];
                    if (nx < 0 || ny < 0 || nx >= board.width || ny >= board.height) {
                        continue;
                    }
                    int next = ny * board.width + nx;
                    if (prev[next] >= 0 || board.walls[next] || blocked[next]) {
                        continue;
                    }
                    prev[next] = cell;
                    walk.add(next);
                }
            }

            for (int cell : reachable) {
                int x = cell % board.width;
                int y = cell / board.width;
                for (int[] d : DIRS) {
                    int bx = x + d[0];
                    int by = y + d[1];
                    if (bx < 0 || by < 0 || bx >= board.width || by >= board.height) {
                        continue;
                    }
                    int boxCell = by * board.width + bx;
                    if (!blocked[boxCell]) {
                        continue;
                    }
                    int tx = bx + d[0];
                    int ty = by + d[1];
                    if (tx < 0 || ty < 0 || tx >= board.width || ty >= board.height) {
                        continue;
                    }
                    int target = ty * board.width + tx;
                    if (board.walls[target] || blocked[target]) {
                        continue;
                    }

                    int[] boxes = node.boxes.clone();
                    for (int i = 0; i < boxes.length; i++) {
                        if (boxes[i] == boxCell) {
                            boxes[i] = target;
                            break;
                        }
                    }
                    Arrays.sort(boxes);

                    // steps: walk from the player to the pushing cell, then push
                    List<int[]> segment = new ArrayList<>();
                    appendWalk(segment, prev, board.width, node.player, cell, board.size);
                    segment.add(d);

                    Node next = new Node(boxes, boxCell, node, segment, node.pushes + 1);
                    String key = key(next, board);
                    if (!seen.containsKey(key)) {
                        seen.put(key, next);
                        queue.add(next);
                    }
                }
            }
        }
        return null;
    }

    /** Appends the single-cell steps that walk from {@code from} to {@code to}. */
    private static void appendWalk(List<int[]> out, int[] prev, int width,
                                   int from, int to, int size) {
        List<Integer> cells = new ArrayList<>();
        int cell = to;
        while (cell != from && cell >= 0 && cell < size) {
            cells.add(cell);
            cell = prev[cell];
        }
        for (int i = cells.size() - 1; i >= 0; i--) {
            int current = cells.get(i);
            int previous = i == cells.size() - 1 ? from : cells.get(i + 1);
            int dx = current % width - previous % width;
            int dy = current / width - previous / width;
            out.add(new int[]{dx, dy});
        }
    }

    private static String key(Node node, Board board) {
        // Two states that differ only by where the player stands inside the
        // same connected region are equivalent, so the region's smallest cell
        // is used for the key. The node keeps the real cell for path building.
        boolean[] blocked = new boolean[board.size];
        for (int box : node.boxes) {
            blocked[box] = true;
        }
        boolean[] visited = new boolean[board.size];
        Deque<Integer> queue = new ArrayDeque<>();
        visited[node.player] = true;
        queue.add(node.player);
        int anchor = node.player;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            anchor = Math.min(anchor, cell);
            int x = cell % board.width;
            int y = cell / board.width;
            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= board.width || ny >= board.height) {
                    continue;
                }
                int next = ny * board.width + nx;
                if (visited[next] || board.walls[next] || blocked[next]) {
                    continue;
                }
                visited[next] = true;
                queue.add(next);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(anchor).append(':');
        for (int box : node.boxes) {
            sb.append(box).append(',');
        }
        return sb.toString();
    }

    private static List<int[]> buildPath(Node node) {
        List<List<int[]>> segments = new ArrayList<>();
        while (node.segment != null) {
            segments.add(node.segment);
            node = node.parent;
        }
        List<int[]> path = new ArrayList<>();
        for (int i = segments.size() - 1; i >= 0; i--) {
            path.addAll(segments.get(i));
        }
        return path;
    }

    private static final class Node {
        final int[] boxes;
        final int player;
        final Node parent;
        final List<int[]> segment;
        final int pushes;

        Node(int[] boxes, int player, Node parent, List<int[]> segment, int pushes) {
            this.boxes = boxes;
            this.player = player;
            this.parent = parent;
            this.segment = segment;
            this.pushes = pushes;
        }
    }

    private static final class Board {
        final int width;
        final int height;
        final int size;
        final boolean[] walls;
        final boolean[] goals;
        final int startPlayer;
        final int[] startBoxes;

        Board(Level level) {
            width = level.width;
            height = level.height;
            size = width * height;
            walls = new boolean[size];
            goals = new boolean[size];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int cell = y * width + x;
                    walls[cell] = level.isWall(x, y);
                    goals[cell] = level.isGoal(x, y);
                }
            }
            startPlayer = level.playerY * width + level.playerX;
            startBoxes = new int[level.boxCount()];
            for (int i = 0; i < level.boxCount(); i++) {
                startBoxes[i] = level.boxY[i] * width + level.boxX[i];
            }
            Arrays.sort(startBoxes);
        }

        boolean solved(int[] boxes) {
            for (int box : boxes) {
                if (!goals[box]) {
                    return false;
                }
            }
            return true;
        }
    }
}
