#!/usr/bin/env python3
"""Generate Sokoban levels and verify every one of them with a solver.

Levels are produced by *reverse playing*: start from a solved position
(every box on a goal) and repeatedly pull boxes backwards. The reverse
sequence is a valid solution when replayed forwards, so every generated
level is solvable by construction. A push-based BFS solver then runs on
each level as an independent check and to compute the minimum number of
pushes ("par") used to order the levels by difficulty.

Output format is standard XSB (also used by the app):

    #######      #  wall      .  goal
    # .$@ #      $  box       @  player
    #######      *  box on goal   +  player on goal
"""

import argparse
import os
import random
import time
from collections import deque

DIRS = ((0, -1), (0, 1), (-1, 0), (1, 0))


# --------------------------------------------------------------------------
# board helpers
# --------------------------------------------------------------------------
def neighbours(cell, d):
    return (cell[0] + d[0], cell[1] + d[1])


def flood_from(start, blocked):
    seen = {start}
    queue = deque([start])
    while queue:
        cell = queue.popleft()
        for d in DIRS:
            nxt = neighbours(cell, d)
            if nxt in seen or nxt in blocked:
                continue
            seen.add(nxt)
            queue.append(nxt)
    return seen


def make_room(width, height, density, rng):
    """Random room enclosed by walls. Returns (walls, floor_cells)."""
    walls = set()
    for x in range(width):
        walls.add((x, 0))
        walls.add((x, height - 1))
    for y in range(height):
        walls.add((0, y))
        walls.add((width - 1, y))

    for y in range(1, height - 1):
        for x in range(1, width - 1):
            # keep the layout tidy: fewer walls in the middle rows/columns
            edge_bias = 0.0
            if x in (1, width - 2) or y in (1, height - 2):
                edge_bias = 0.05
            if rng.random() < density + edge_bias:
                walls.add((x, y))

    floor = {(x, y) for y in range(height) for x in range(width)} - walls
    if not floor:
        return walls, floor
    anchor = min(floor)
    if flood_from(anchor, walls) != floor:
        return walls, set()
    return walls, floor


# --------------------------------------------------------------------------
# static deadlock table
# --------------------------------------------------------------------------
def dead_squares(walls, goals):
    """Cells where a box can never be pushed out of (goals excluded)."""
    xs = [c[0] for c in walls]
    ys = [c[1] for c in walls]
    dead = set()
    for y in range(min(ys), max(ys) + 1):
        for x in range(min(xs), max(xs) + 1):
            cell = (x, y)
            if cell in walls or cell in goals:
                continue
            up = (x, y - 1) in walls
            down = (x, y + 1) in walls
            left = (x - 1, y) in walls
            right = (x + 1, y) in walls
            if (up or down) and (left or right):
                dead.add(cell)

    changed = True
    while changed:
        changed = False
        for y in range(min(ys), max(ys) + 1):
            for x in range(min(xs), max(xs) + 1):
                cell = (x, y)
                if cell in walls or cell in goals or cell in dead:
                    continue
                pushable = False
                all_dead = True
                for d in DIRS:
                    if (x - d[0], y - d[1]) in walls:
                        continue
                    if (x + d[0], y + d[1]) in walls:
                        continue
                    pushable = True
                    if (x + d[0], y + d[1]) not in dead:
                        all_dead = False
                        break
                if not pushable or all_dead:
                    dead.add(cell)
                    changed = True
    return frozenset(dead)


# --------------------------------------------------------------------------
# solver
# --------------------------------------------------------------------------
def normalise(player, boxes, walls):
    """Canonical player cell + the cells the player can reach."""
    reach = flood_from(player, walls | boxes)
    return min(reach), reach


def solve(walls, goals, boxes, player, max_nodes=1_500_000):
    """Minimum pushes needed, or None if unsolvable / search gave up."""
    goals = frozenset(goals)
    boxes = frozenset(boxes)
    if boxes <= goals:
        return 0
    dead = dead_squares(walls, goals)

    start_player, _ = normalise(player, boxes, walls)
    start = (boxes, start_player)
    dist = {start: 0}
    queue = deque([start])
    nodes = 0

    while queue:
        boxes, player = queue.popleft()
        cost = dist[(boxes, player)]
        nodes += 1
        if nodes > max_nodes:
            return None
        _, reach = normalise(player, boxes, walls)
        for cell in reach:
            for d in DIRS:
                box = neighbours(cell, d)
                dest = neighbours(box, d)
                if box not in boxes or dest in walls or dest in boxes:
                    continue
                if dest in dead:
                    continue
                nboxes = frozenset((boxes - {box}) | {dest})
                if nboxes <= goals:
                    return cost + 1
                nplayer, _ = normalise(box, nboxes, walls)
                state = (nboxes, nplayer)
                if state not in dist:
                    dist[state] = cost + 1
                    queue.append(state)
    return None


# --------------------------------------------------------------------------
# reverse play generation
# --------------------------------------------------------------------------
def bfs_path(start, target, blocked):
    """Shortest walk from start to target, avoiding walls and boxes."""
    if start == target:
        return [start]
    prev: dict[tuple[int, int], tuple[int, int]] = {}
    seen = {start}
    queue = deque([start])
    while queue:
        cell = queue.popleft()
        for d in DIRS:
            nxt = neighbours(cell, d)
            if nxt in seen or nxt in blocked:
                continue
            seen.add(nxt)
            prev[nxt] = cell
            if nxt == target:
                path = [nxt]
                while path[-1] != start:
                    path.append(prev[path[-1]])
                return path[::-1]
            queue.append(nxt)
    return None


def reverse_pull(walls, goals, boxes, player, rounds, rng, max_pull=5):
    """Reverse-play from a solved position.

    Each round picks a box, walks the player behind it and drags it backwards a
    few steps. Undoing those pulls gives a real forward solution, so every
    level built this way is solvable by construction (see replay()). Boxes are
    never dragged back onto a goal, keeping the start position unsolved.
    """
    boxes = set(boxes)
    moves = []
    pulled = 0

    for _ in range(rounds):
        if not boxes:
            break
        box = rng.choice(sorted(boxes))
        options = pull_directions(box, walls, boxes)
        if not options:
            continue

        d = rng.choice(options)
        stand = (box[0] - d[0], box[1] - d[1])
        path = bfs_path(player, stand, walls | boxes)
        if path is None:
            continue
        for cell in path[1:]:
            moves.append(("walk", (cell[0] - player[0], cell[1] - player[1])))
            player = cell

        for _ in range(rng.randint(1, max_pull)):
            if player in goals:            # would park the box on a goal
                break
            back = (player[0] - d[0], player[1] - d[1])
            if back in walls or back in boxes:
                break
            boxes.remove(box)
            boxes.add(player)
            moves.append(("pull", d))
            player = back
            pulled += 1
            box = neighbours(player, d)

    return frozenset(boxes), player, moves, pulled


def replay(walls, goals, boxes, player, moves):
    """Replay a reverse move list forwards. Returns pushes, or None if the
    replay is invalid (which would mean the reverse play had a bug)."""
    boxes = set(boxes)
    pushes = 0
    for kind, d in reversed(moves):
        if kind == "walk":
            # a reverse walk of +d is undone by walking -d
            nxt = neighbours(player, (-d[0], -d[1]))
            if nxt in walls or nxt in boxes:
                return None
            player = nxt
        else:
            box = neighbours(player, d)
            dest = neighbours(box, d)
            if box not in boxes or dest in walls or dest in boxes:
                return None
            boxes.remove(box)
            boxes.add(dest)
            player = box
            pushes += 1
    return pushes if boxes == set(goals) else None


def pull_directions(box, walls, boxes):
    """Directions a box can be dragged backwards (room in front and behind)."""
    options = []
    for d in DIRS:
        stand = (box[0] - d[0], box[1] - d[1])
        beyond = (box[0] - 2 * d[0], box[1] - 2 * d[1])
        if stand in walls or stand in boxes:
            continue
        if beyond in walls or beyond in boxes:
            continue
        options.append(d)
    return options


def pick_goals(floor, walls, count, rng):
    """Goal cells need elbow room so boxes can be dragged off them again."""
    candidates = []
    for cell in sorted(floor):
        for d in DIRS:
            near = (cell[0] + d[0], cell[1] + d[1])
            far = (cell[0] + 2 * d[0], cell[1] + 2 * d[1])
            if near in floor and far in floor:
                candidates.append(cell)
                break
    if len(candidates) < count:
        return None
    rng.shuffle(candidates)
    goals = []
    for cell in candidates:
        if all(max(abs(cell[0] - g[0]), abs(cell[1] - g[1])) >= 2 for g in goals):
            goals.append(cell)
            if len(goals) == count:
                return frozenset(goals)
    return None


def build_level(width, height, box_count, rng, solve_nodes=250_000):
    """Build one solvable level. With solve_nodes=0 the (expensive) optimal
    solve is skipped and ``par`` is the length of the constructed solution."""
    density = rng.choice([0.10, 0.14, 0.18])
    walls, floor = make_room(width, height, density, rng)
    if len(floor) < box_count * 2 + 5:
        return None
    if len(floor) > width * height * 0.75:
        return None

    goals = pick_goals(floor, walls, box_count, rng)
    if goals is None:
        return None
    # every box must be draggable off its goal before we start
    for goal in goals:
        if not pull_directions(goal, walls, goals):
            return None

    rest = sorted(floor - goals)
    player = rng.choice(rest)

    # the solved position: one box on every goal
    boxes, player, moves, pulled = reverse_pull(
        walls, goals, goals, player, rounds=box_count * 8, rng=rng, max_pull=6)
    if pulled < max(4, box_count * 2):
        return None
    if boxes & goals:                      # a box already parked on a goal
        return None
    if not boxes:
        return None

    # hard proof: replay the reverse-played moves forwards
    pushes = replay(walls, goals, boxes, player, moves)
    if pushes is None:
        return None

    par = solve(walls, goals, boxes, player, max_nodes=solve_nodes) if solve_nodes else None
    exact = par is not None
    if not exact:
        par = pushes                       # provably solvable, par is a bound
    return {
        "walls": frozenset(walls),
        "goals": goals,
        "boxes": boxes,
        "player": player,
        "par": par,
        "exact": exact,
        "size": (width, height),
    }


def render(level):
    width, height = level["size"]
    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            cell = (x, y)
            if cell in level["walls"]:
                row.append("#")
            elif cell == level["player"]:
                row.append("+" if cell in level["goals"] else "@")
            elif cell in level["boxes"]:
                row.append("*" if cell in level["goals"] else "$")
            elif cell in level["goals"]:
                row.append(".")
            else:
                row.append(" ")
        rows.append("".join(row))
    return rows


def shape_key(level):
    """Mirror/rotation independent key used to drop near duplicates."""
    rows = render(level)
    variants = {
        "\n".join(rows),
        "\n".join(row[::-1] for row in rows),
        "\n".join(rows[::-1]),
        "\n".join(row[::-1] for row in rows[::-1]),
    }
    return min(variants).replace("@", " ").replace("+", ".")


# --------------------------------------------------------------------------
# hand written warm-up levels
# --------------------------------------------------------------------------
HAND_MADE = [
    [
        "#######",
        "#     #",
        "# .$@ #",
        "#     #",
        "#######",
    ],
    [
        "########",
        "#      #",
        "# .$.$ #",
        "#   @  #",
        "########",
    ],
]


def parse_xsb(lines):
    walls, goals, boxes, player = set(), set(), set(), None
    for y, row in enumerate(lines):
        for x, ch in enumerate(row):
            cell = (x, y)
            if ch == "#":
                walls.add(cell)
            elif ch == ".":
                goals.add(cell)
            elif ch == "$":
                boxes.add(cell)
            elif ch == "*":
                boxes.add(cell)
                goals.add(cell)
            elif ch == "@":
                player = cell
            elif ch == "+":
                player = cell
                goals.add(cell)
    return walls, goals, boxes, player


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="app/src/main/assets/levels.txt")
    ap.add_argument("--seed", type=int, default=20261030)
    ap.add_argument("--budget", type=int, default=240,
                    help="seconds allowed for the optimal-solve ranking pass")
    args = ap.parse_args()

    rng = random.Random(args.seed)

    # difficulty buckets: (label, min par, max par, how many levels)
    buckets = [
        ("入门 Warm-up", 2, 5, 6),
        ("简单 Easy", 6, 8, 7),
        ("普通 Normal", 9, 11, 8),
        ("进阶 Advanced", 12, 14, 7),
        ("困难 Hard", 15, 20, 6),
        ("挑战 Expert", 21, 99, 4),
    ]

    shapes = [
        (5, 5, 2), (5, 5, 3), (6, 5, 2), (6, 6, 2), (6, 6, 3),
        (7, 6, 3), (7, 7, 3), (7, 7, 4), (8, 7, 4), (8, 8, 4),
        (8, 8, 5), (9, 8, 5), (9, 9, 6), (10, 9, 6),
    ]

    # -- warm-up levels ----------------------------------------------------
    levels = []
    for rows in HAND_MADE:
        walls, goals, boxes, player = parse_xsb(rows)
        par = solve(walls, goals, boxes, player)
        assert par is not None, ("hand made level unsolvable", rows)
        levels.append({
            "walls": frozenset(walls),
            "goals": frozenset(goals),
            "boxes": frozenset(boxes),
            "player": player,
            "par": par,
            "size": (len(rows[0]), len(rows)),
        })
    seen = {shape_key(level) for level in levels}

    # -- generated levels --------------------------------------------------
    # Generation itself is cheap (solvability is proven by replaying the
    # reverse-played moves). The optimal solve used for difficulty ranking is
    # done once per candidate in a second pass.
    pool = []
    tries = 0
    target_pool = 200
    while len(pool) < target_pool and tries < 20000:
        tries += 1
        width, height, box_count = rng.choice(shapes)
        level = build_level(width, height, box_count, rng, solve_nodes=0)
        if level is None:
            continue
        key = shape_key(level)
        if key in seen:
            continue
        # very short levels are rarely interesting once past the tutorial
        if level["par"] < 4:
            continue
        seen.add(key)
        pool.append(level)
    print("attempts %d -> %d unique levels" % (tries, len(pool)))

    # -- exact difficulty --------------------------------------------------
    # Ranking every candidate with a full optimal solve is far too slow for
    # the big boards, so the second pass gets a time budget and a node cap.
    ranked = []
    # small boards solve instantly; do them first so the budget is well spent
    pool.sort(key=lambda lv: (lv["size"][0] * lv["size"][1], lv["par"]))
    deadline = time.time() + args.budget
    for level in pool:
        if time.time() > deadline:
            break
        if level["par"] > 60:              # pathological construction, skip
            continue
        par = solve(level["walls"], level["goals"], level["boxes"],
                    level["player"], max_nodes=200_000)
        if par is None:                    # too slow to search: skip
            continue
        level["par"] = par
        level["exact"] = True
        ranked.append(level)
    ranked.sort(key=lambda lv: (lv["par"], lv["size"]))
    print("solved %d/%d exactly (%ds budget)"
          % (len(ranked), len(pool), args.budget))
    hist = {}
    for lv in ranked:
        hist[lv["par"]] = hist.get(lv["par"], 0) + 1
    print("par histogram: %s" % ", ".join(
        "%d:%d" % (par, hist[par]) for par in sorted(hist)))

    # -- pick levels per bucket -------------------------------------------
    chosen = list(levels)
    for label, low, high, count in buckets:
        cand = [lv for lv in ranked if low <= lv["par"] <= high]
        if len(cand) > count:
            step = len(cand) / float(count)
            cand = [cand[int(i * step)] for i in range(count)]
        chosen.extend(cand)

    # -- write -------------------------------------------------------------
    out_dir = os.path.dirname(args.out) or "."
    if out_dir and not os.path.isdir(out_dir):
        os.makedirs(out_dir)

    names = ["第 %d 关" % (i + 1) for i in range(len(chosen))]
    blocks = []
    for index, level in enumerate(chosen):
        rows = render(level)
        width, height = level["size"]
        title = names[index]
        blocks.append(
            "; %d. %s | %dx%d | par %d"
            % (index + 1, title, width, height, level["par"])
        )
        blocks.extend(rows)
        blocks.append("")

    with open(args.out, "w", encoding="utf-8", newline="\n") as fp:
        fp.write("\n".join(blocks))

    print("wrote %d levels to %s" % (len(chosen), args.out))
    hard = [lv["par"] for lv in chosen]
    print("par range: %d .. %d" % (min(hard), max(hard)))


if __name__ == "__main__":
    main()
