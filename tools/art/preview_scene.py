"""Offline composite of the backdrop using the same anchors/scales as BackgroundRenderer.java.

Usage: python3 tools/art/preview_scene.py OUT.png [WIDTH HEIGHT] [--graph]
With --graph the authored skill graph (all nodes locked) is drawn on top at fit zoom, to check readability.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

from common import REPO, TEX

BG = os.path.join(TEX, "gui", "skill_tree", "background")
OVERSCAN = 0.06


def layer(name):
    return Image.open(os.path.join(BG, name + ".png")).convert("RGBA")


def cover(canvas, img):
    w, h = canvas.size
    scale = max(w / 2048, h / 1152) * (1 + OVERSCAN)
    im = img.resize((int(2048 * scale), int(1152 * scale)), Image.NEAREST)
    canvas.alpha_composite(im, ((w - im.width) // 2, (h - im.height) // 2))


def sprite(canvas, img, box, cx, cy, dw, dh):
    region = img.crop(box).resize((max(1, int(dw)), max(1, int(dh))), Image.NEAREST)
    x, y = int(cx - dw / 2), int(cy - dh / 2)
    canvas.alpha_composite(region, (max(0, x), max(0, y)), (max(0, -x), max(0, -y)))


def compose(w, h):
    canvas = Image.new("RGBA", (w, h), (5, 12, 16, 255))
    cover(canvas, layer("space_base"))
    cover(canvas, layer("stars_far"))
    cover(canvas, layer("nebula_far"))
    s = h / 1152
    sprite(canvas, layer("astrolabe_ghost"), (0, 0, 1024, 1024), w * 0.50, h * 0.52, 760 * s, 760 * s)
    sprite(canvas, layer("moon_crescent"), (0, 0, 512, 512), w * 0.10, h * 0.91, 440 * s, 440 * s)
    ruins = layer("observatory_ruins")
    f = 190 * s
    sprite(canvas, ruins, (0, 0, 256, 256), w * 0.06, h * 0.12, f, f)
    sprite(canvas, ruins, (256, 0, 512, 256), w * 0.93, h * 0.10, f, f)
    sprite(canvas, ruins, (0, 256, 256, 512), w * 0.95, h * 0.62, f * 0.8, f * 0.8)
    sprite(canvas, ruins, (256, 256, 512, 512), w * 0.87, h * 0.80, f * 0.7, f * 0.7)
    plat = layer("observatory_platform")
    ph = 118 * s
    pw = 1024 * ph / 160
    x = -pw
    while x < w + pw:
        sprite(canvas, plat, (0, 0, 1024, 160), x + pw / 2, h - ph / 2, pw, ph)
        x += pw
    return canvas


def draw_graph(canvas):
    man = json.load(open(os.path.join(REPO, "docs", "layout", "layout_manifest.json")))
    w, h = canvas.size
    b = man["bounds"]
    top, bottom = 56, h - 34
    zoom = min(w / (b["max_x"] - b["min_x"] + 80), (bottom - top) / (b["max_y"] - b["min_y"] + 80))
    cx, cy = (b["min_x"] + b["max_x"]) / 2, (b["min_y"] + b["max_y"]) / 2
    T = lambda x, y: (w / 2 + (x - cx) * zoom, (top + bottom) / 2 + (y - cy) * zoom)
    d = ImageDraw.Draw(canvas)
    pos = {n["id"]: (n["x"], n["y"], n["type"]) for n in man["nodes"]}
    for e in man["edges"]:
        pts = [pos[e["a"]][:2]] + [(p["x"], p["y"]) for p in e["points"]] + [pos[e["b"]][:2]]
        d.line([T(*p) for p in pts], fill=(0x35, 0x29, 0x1D, 255), width=max(1, int(1.5 * math.sqrt(zoom))))
    size = {"minor": 13, "notable": 17, "keystone": 24, "root": 30}
    col = {"minor": (0x88, 0x64, 0x2C), "notable": (0xA9, 0x77, 0xDC), "keystone": (0xF3, 0xD1, 0x5C), "root": (0x7C, 0x8D, 0x88)}
    for x, y, t in pos.values():
        sx, sy = T(x, y)
        r = size[t] * zoom / 2
        d.ellipse([sx - r, sy - r, sx + r, sy + r], outline=col[t], fill=(20, 16, 12, 255))


if __name__ == "__main__":
    out = sys.argv[1]
    w, h = (int(sys.argv[2]), int(sys.argv[3])) if len(sys.argv) > 3 and sys.argv[2].isdigit() else (1280, 720)
    img = compose(w, h)
    if "--graph" in sys.argv:
        draw_graph(img)
    img.convert("RGB").save(out)
