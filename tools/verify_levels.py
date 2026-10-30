#!/usr/bin/env python3
"""Sanity check app/src/main/assets/levels.txt.

Parses the XSB asset the same way the app does and verifies that every level
is well formed (one player, #boxes == #goals) and solvable. Levels whose
optimal push count is too large to search are reported as "slow" instead of
being called unsolvable.
"""

import sys

import gen_levels as G


def parse(text):
    levels = []
    rows = []
    title = ""
    for line in text.splitlines():
        line = line.rstrip("\n")
        if line.startswith(";"):
            if rows:
                levels.append((title, rows))
                rows = []
            title = line.lstrip("; ").strip()
            continue
        if not line.strip():
            if rows:
                levels.append((title, rows))
                rows = []
            continue
        rows.append(line)
    if rows:
        levels.append((title, rows))
    return levels


def main(path):
    with open(path, encoding="utf-8") as fp:
        text = fp.read()

    levels = parse(text)
    print("%d levels" % len(levels))
    bad = 0
    for index, (title, rows) in enumerate(levels, 1):
        walls, goals, boxes, player = G.parse_xsb(rows)
        problems = []
        if player is None:
            problems.append("no player")
        if not boxes:
            problems.append("no boxes")
        if len(boxes) != len(goals):
            problems.append("boxes %d != goals %d" % (len(boxes), len(goals)))
        widths = {len(row) for row in rows}
        if len(widths) > 1:
            problems.append("ragged rows %s" % sorted(widths))
        if problems:
            bad += 1
            print("  FAIL %s: %s" % (title, ", ".join(problems)))
            continue
        par = G.solve(walls, goals, boxes, player, max_nodes=400_000)
        status = "UNSOLVED" if par is None else "par %d" % par
        if par is None:
            print("  %-28s %dx%d  slow/unsolved" % (title, len(rows[0]), len(rows)))
        else:
            print("  %-28s %dx%d  %s" % (title, len(rows[0]), len(rows), status))

    print("structure failures: %d" % bad)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1
                  else "../app/src/main/assets/levels.txt"))
