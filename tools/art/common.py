"""Shared helpers for the deterministic pixel-art generators (pure Python + Pillow).

All randomness comes from explicit seeds; running the generator twice produces byte-identical PNGs.
Large layers are authored at quarter resolution and upscaled with nearest-neighbour so every
"pixel" is a crisp 4x4 block (pixel-art look, no blur, no seams).
"""
import math
import os
import random

from PIL import Image

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(REPO, "src", "main", "resources", "assets", "nostalgiaskills", "textures")

BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def lerp(a, b, t):
    return a + (b - a) * t


def lerp_c(c0, c1, t):
    return tuple(int(round(lerp(c0[i], c1[i], t))) for i in range(len(c0)))


def smoothstep(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


class ValueNoise:
    """Periodic value noise (tileable with period px), deterministic per seed."""

    def __init__(self, seed, period=64):
        rnd = random.Random(seed)
        self.p = period
        self.g = [[rnd.random() for _ in range(period)] for _ in range(period)]

    def at(self, x, y):
        p = self.p
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        g = self.g
        a = g[y0 % p][x0 % p]
        b = g[y0 % p][(x0 + 1) % p]
        c = g[(y0 + 1) % p][x0 % p]
        d = g[(y0 + 1) % p][(x0 + 1) % p]
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy)

    def fbm(self, x, y, octaves=4):
        v, amp, tot, f = 0.0, 1.0, 0.0, 1.0
        for _ in range(octaves):
            v += amp * self.at(x * f, y * f)
            tot += amp
            amp *= 0.5
            f *= 2.0
        return v / tot


def quantize(value, steps, x, y):
    """Ordered-dither a 0..1 value into `steps` discrete levels (stepped shading with restrained dithering)."""
    v = max(0.0, min(1.0, value)) * (steps - 1)
    base = math.floor(v)
    frac = v - base
    threshold = (BAYER4[y % 4][x % 4] + 0.5) / 16.0
    return min(steps - 1, base + (1 if frac > threshold else 0))


def ramp(colors, idx):
    return colors[max(0, min(len(colors) - 1, idx))]


def upscale(img, factor):
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    return path


def disc_mask(size, cx, cy, r):
    return [[(x - cx) ** 2 + (y - cy) ** 2 <= r * r for x in range(size)] for y in range(size)]
