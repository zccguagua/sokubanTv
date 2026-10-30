#!/usr/bin/env python3
"""Draw the TV banner and the launcher icon without any image libraries.

Everything is rendered with signed distance fields and smoothed edges, so the
result stays crisp at any size. Run after changing colours in res/values.
"""

import math
import os
import struct
import zlib


# --------------------------------------------------------------------------
# tiny PNG writer (RGBA, 8 bit)
# --------------------------------------------------------------------------
def write_png(path, width, height, pixels):
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)                      # filter: none
        raw.extend(pixels[y * stride:(y + 1) * stride])

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        out += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return out

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    directory = os.path.dirname(path)
    if directory and not os.path.isdir(directory):
        os.makedirs(directory)
    with open(path, "wb") as fp:
        fp.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


# --------------------------------------------------------------------------
# painting helpers
# --------------------------------------------------------------------------
def clamp01(v):
    return 0.0 if v < 0.0 else (1.0 if v > 1.0 else v)


def smoothstep(edge, width, value):
    """1 inside the shape, 0 outside, smooth across `width`."""
    if width <= 0:
        return 1.0 if value <= edge else 0.0
    t = clamp01(0.5 - (value - edge) / width)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = clamp01(t)
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def sd_round_rect(px, py, cx, cy, hw, hh, r):
    qx = abs(px - cx) - (hw - r)
    qy = abs(py - cy) - (hh - r)
    outside = math.hypot(max(qx, 0.0), max(qy, 0.0))
    inside = min(max(qx, qy), 0.0)
    return outside + inside - r


def sd_circle(px, py, cx, cy, r):
    return math.hypot(px - cx, py - cy) - r


def sd_diamond(px, py, cx, cy, r):
    return (abs(px - cx) + abs(py - cy)) - r


class Canvas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.buf = [[(0.0, 0.0, 0.0, 0.0)] * width for _ in range(height)]

    def blend(self, x, y, color, alpha):
        if alpha <= 0.0 or x < 0 or y < 0 or x >= self.width or y >= self.height:
            return
        dst = self.buf[y][x]
        a = clamp01(alpha)
        src = (color[0] / 255.0, color[1] / 255.0, color[2] / 255.0)
        na = a + dst[3] * (1 - a)
        if na <= 0:
            return
        rgb = [(src[i] * a + dst[i] * dst[3] * (1 - a)) / na for i in range(3)]
        self.buf[y][x] = (rgb[0], rgb[1], rgb[2], na)

    def shape(self, sdf, color, width=1.0, alpha=1.0, fill=True):
        """Fill (or outline) a signed distance field, anti-aliased."""
        for y in range(self.height):
            row = self.buf[y]
            py = y + 0.5
            for x in range(self.width):
                d = sdf(x + 0.5, py)
                if fill:
                    a = smoothstep(0.0, width, d)
                else:
                    a = smoothstep(0.0, width, abs(d) - width * 0.5)
                if a > 0:
                    row[x] = blend_pixel(row[x], color, a * alpha)

    def pixels(self):
        out = bytearray()
        for y in range(self.height):
            for x in range(self.width):
                r, g, b, a = self.buf[y][x]
                out.extend((
                    int(clamp01(r) * 255 + 0.5),
                    int(clamp01(g) * 255 + 0.5),
                    int(clamp01(b) * 255 + 0.5),
                    int(clamp01(a) * 255 + 0.5),
                ))
        return bytes(out)


def blend_pixel(dst, color, alpha):
    a = clamp01(alpha)
    src = (color[0] / 255.0, color[1] / 255.0, color[2] / 255.0)
    na = a + dst[3] * (1 - a)
    if na <= 0:
        return (0.0, 0.0, 0.0, 0.0)
    rgb = [(src[i] * a + dst[i] * dst[3] * (1 - a)) / na for i in range(3)]
    return (rgb[0], rgb[1], rgb[2], na)


# --------------------------------------------------------------------------
# artwork
# --------------------------------------------------------------------------
BG_TOP = (11, 16, 32)
BG_BOTTOM = (24, 34, 62)
TEAL = (53, 208, 186)
TEAL_DARK = (18, 96, 92)
CRATE_LIGHT = (247, 178, 92)
CRATE_DARK = (176, 99, 45)
WHITE = (240, 246, 255)


def paint_background(canvas):
    w, h = canvas.width, canvas.height
    for y in range(h):
        row = canvas.buf[y]
        t = y / float(h - 1)
        base = mix(BG_TOP, BG_BOTTOM, t)
        for x in range(w):
            row[x] = (base[0] / 255.0, base[1] / 255.0, base[2] / 255.0, 1.0)


def paint_glow(canvas, cx, cy, radius, color, strength):
    w, h = canvas.width, canvas.height
    for y in range(h):
        row = canvas.buf[y]
        py = y + 0.5
        for x in range(w):
            dist = math.hypot(x + 0.5 - cx, py - cy)
            if dist > radius:
                continue
            falloff = (1.0 - dist / radius) ** 2.6
            row[x] = blend_pixel(row[x], color, falloff * strength)


def paint_crate(canvas, cx, cy, size, alpha=1.0):
    """Isometric-ish crate: body, border, diagonal braces."""
    half = size / 2.0
    r = size * 0.16
    canvas.shape(
        lambda x, y: sd_round_rect(x, y, cx, cy, half, half, r),
        CRATE_DARK, width=size * 0.02, alpha=alpha)
    inner = half - size * 0.055
    canvas.shape(
        lambda x, y: sd_round_rect(x, y, cx, cy, inner, inner, r * 0.8),
        CRATE_LIGHT, width=size * 0.02, alpha=alpha)

    # braces: two diagonals, drawn as thin rotated capsules
    brace_w = size * 0.055
    diag = inner * 1.18
    for sign in (1, -1):
        nx, ny = 1.0 / math.sqrt(2), sign / math.sqrt(2)

        def brace(x, y, nx=nx, ny=ny):
            dx = x - cx
            dy = y - cy
            along = dx * nx + dy * ny
            across = -dx * ny + dy * nx
            return max(abs(along) - diag / 2.0, abs(across) - brace_w / 2.0)

        canvas.shape(brace, CRATE_DARK, width=size * 0.012, alpha=alpha * 0.85)

    # top-left highlight
    canvas.shape(
        lambda x, y: sd_round_rect(x, y, cx - size * 0.06, cy - size * 0.08,
                                   inner * 0.42, inner * 0.2, size * 0.06),
        (255, 226, 178), width=size * 0.06, alpha=alpha * 0.35)


def paint_player(canvas, cx, cy, size, look_x, look_y):
    canvas.shape(lambda x, y: sd_circle(x, y, cx, cy, size * 0.5),
                 (26, 34, 52), width=size * 0.02)
    canvas.shape(lambda x, y: sd_circle(x, y, cx, cy, size * 0.42),
                 TEAL, width=size * 0.02)
    canvas.shape(lambda x, y: sd_circle(x, y, cx - size * 0.10, cy - size * 0.12,
                                       size * 0.16), (200, 255, 248),
                 width=size * 0.06, alpha=0.45)
    eye_r = size * 0.085
    for side in (-1, 1):
        ex = cx + side * size * 0.15 + look_x * size * 0.03
        ey = cy - size * 0.04 + look_y * size * 0.03
        canvas.shape(lambda x, y, ex=ex, ey=ey: sd_circle(x, y, ex, ey, eye_r),
                     (12, 28, 38), width=1.0)


def paint_goal(canvas, cx, cy, size):
    canvas.shape(lambda x, y: sd_diamond(x, y, cx, cy, size * 0.5),
                 TEAL, width=size * 0.05, fill=False, alpha=0.9)
    canvas.shape(lambda x, y: sd_diamond(x, y, cx, cy, size * 0.18),
                 TEAL, width=size * 0.04, alpha=0.85)


def banner(path):
    w, h = 320, 180
    canvas = Canvas(w, h)
    paint_background(canvas)

    # faint grid
    for x in range(0, w, 32):
        canvas.shape(lambda px, py, x=x: abs(px - (x + 0.5)) - 0.6,
                     (60, 82, 124), width=1.0, alpha=0.25)
    for y in range(0, h, 32):
        canvas.shape(lambda px, py, y=y: abs(py - (y + 0.5)) - 0.6,
                     (60, 82, 124), width=1.0, alpha=0.25)

    paint_glow(canvas, 176, 92, 96, TEAL, 0.22)
    paint_goal(canvas, 176, 92, 52)

    paint_crate(canvas, 108, 112, 62)
    paint_crate(canvas, 232, 128, 40, alpha=0.95)
    paint_player(canvas, 150, 62, 34, 1, 0)

    write_png(path, w, h, canvas.pixels())


def icon(path):
    w = h = 192
    canvas = Canvas(w, h)
    for y in range(h):
        row = canvas.buf[y]
        t = y / float(h - 1)
        base = mix(BG_TOP, BG_BOTTOM, t)
        for x in range(w):
            row[x] = (base[0] / 255.0, base[1] / 255.0, base[2] / 255.0, 0.0)

    canvas.shape(lambda x, y: sd_round_rect(x, y, w / 2.0, h / 2.0, w * 0.5, h * 0.5, w * 0.22),
                 BG_BOTTOM, width=1.0)
    paint_glow(canvas, 106, 96, 70, TEAL, 0.18)
    paint_goal(canvas, 96, 96, 44)
    paint_crate(canvas, 96, 104, 88)
    paint_player(canvas, 138, 58, 30, -1, 1)

    write_png(path, w, h, canvas.pixels())


if __name__ == "__main__":
    res = os.path.join("app", "src", "main", "res")
    banner(os.path.join(res, "drawable", "tv_banner.png"))
    icon(os.path.join(res, "mipmap-xxxhdpi", "ic_launcher.png"))
