"""The Astral Observatory backdrop layers (see ART_DIRECTION.md for composition and palette)."""
import math
import random

from PIL import Image

from common import ValueNoise, hexc, lerp, lerp_c, quantize, ramp, save, smoothstep, upscale

W, H = 512, 288          # authoring resolution for full-frame layers (x4 -> 2048x1152)
BG_DIR = "gui/skill_tree/background/"

SPACE_RAMP = [hexc(c) for c in ("#030709", "#040A0D", "#050C10", "#061015", "#08151B", "#0A1A21", "#0C1F27",
                                  "#0E252E", "#112B35", "#13323D", "#153844")]
PLATFORM_STONE = [hexc(c) for c in ("#0C0F12", "#12161A", "#191D21", "#20252A", "#282E33", "#31383E")]
TEAL_RAMP = [hexc("#0E2A30", 0), hexc("#12404A", 46), hexc("#1B5A60", 74), hexc("#2A7272", 96)]
VIOLET_RAMP = [hexc("#21183A", 0), hexc("#2E2150", 40), hexc("#3E2A5C", 62), hexc("#55397A", 84)]
STONE = [hexc(c) for c in ("#14181C", "#1E2327", "#2A3036", "#373E45", "#454D55", "#56606A")]
BRONZE_INLAY = hexc("#6E5226")
AMBER = hexc("#A0702A")
RIM_CYAN = hexc("#5FAFBA")


def central_quiet(nx, ny):
    """1 inside the calm central 60% x 65% reading area, falling to 0 outside."""
    ax = 1 - smoothstep(0.24, 0.34, abs(nx - 0.5))
    ay = 1 - smoothstep(0.26, 0.36, abs(ny - 0.5))
    return ax * ay


def space_base():
    noise = ValueNoise(1101, period=32)
    img = Image.new("RGBA", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            nx, ny = x / W, y / H
            d = math.hypot((nx - 0.5) * 2, (ny - 0.5) * 2) / math.sqrt(2)
            n = noise.fbm(x / 40.0, y / 40.0, 4)
            b = 0.08 + 0.55 * smoothstep(0.3, 1.0, d) + 0.35 * (n - 0.5) * smoothstep(0.15, 0.9, d)
            b += 0.18 * smoothstep(0.55, 1.0, 1 - math.hypot(nx - 1.0, ny - 0.0))   # cool glow, upper right
            b *= 1 - 0.35 * smoothstep(0.9, 1.2, d)                                  # soft vignette in corners
            px[x, y] = ramp(SPACE_RAMP, quantize(b, len(SPACE_RAMP), x, y))
    return upscale(img, 4)


def stars_far():
    sw, sh = 1024, 576            # stars are authored at half resolution (2px blocks) so they stay tiny
    rnd = random.Random(2202)
    img = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    px = img.load()
    tints = [hexc("#CFE8F0"), hexc("#A9DCDC"), hexc("#BFD3EA"), hexc("#F0E2B0")]
    placed = 0
    while placed < 1900:
        x, y = rnd.randrange(sw), rnd.randrange(sh)
        nx, ny = x / sw, y / sh
        quiet = central_quiet(nx, ny)
        if rnd.random() < quiet * 0.6:       # fewer stars behind the central graph area
            continue
        r = rnd.random()
        tint = tints[3] if rnd.random() < 0.06 else tints[rnd.randrange(3)]
        alpha = int(40 + 90 * r ** 3)
        bright = r > 0.985 and quiet < 0.2
        if quiet > 0.2:
            alpha = min(alpha, 100)
        px[x, y] = (tint[0], tint[1], tint[2], 210 if bright else alpha)
        if bright:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < sw and 0 <= y + dy < sh:
                    px[x + dx, y + dy] = (tint[0], tint[1], tint[2], 70)
        placed += 1
    return upscale(img, 2)


def _bezier(p0, p1, p2, n=72):
    pts = []
    for i in range(n + 1):
        t = i / n
        pts.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1], t))
    return pts


def nebula_far():
    noise = ValueNoise(3303, period=64)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    # main teal ribbon: from the upper-right perimeter (origin near 0.82, 0.15) toward the lower middle distance
    main = [(x * W, y * H, t) for x, y, t in _bezier((1.02, -0.05), (0.80, 0.18), (0.50, 1.05))]
    side = [(x * W, y * H, t) for x, y, t in _bezier((1.05, 0.30), (0.88, 0.55), (0.70, 1.08))]
    for y in range(H):
        for x in range(W):
            nx, ny = x / W, y / H
            fade = 1 - 0.88 * central_quiet(nx, ny)
            best = None
            for ribbon, width, rmp, k in ((main, 0.11, TEAL_RAMP, 1.0), (side, 0.06, VIOLET_RAMP, 0.8)):
                d, t = min((math.hypot(x - cx, y - cy), ct) for cx, cy, ct in ribbon[::3])
                w = width * W * (0.6 + 0.6 * math.sin(t * math.pi))
                if d > w:
                    continue
                wisp = noise.fbm(x / 22.0 + t * 3, y / 22.0, 4)
                v = (1 - d / w) ** 1.6 * (0.45 + 0.9 * wisp) * k * fade
                if best is None or v > best[0]:
                    best = (v, rmp)
            if best:
                px[x, y] = ramp(best[1], quantize(best[0], len(best[1]), x, y))
    return upscale(img, 4)


def moon_crescent():
    S, R = 128, 56
    cx, cy = 64, 64
    rnd = random.Random(4404)
    noise = ValueNoise(4405, period=32)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    shadow = (cx - 19, cy + 15, 54)                       # crescent lit toward the upper right
    craters = [(cx + rnd.randint(-30, 40), cy + rnd.randint(-40, 30), rnd.randint(3, 8)) for _ in range(9)]
    lx, ly, lz = 0.62, -0.58, 0.53
    ramp_moon = [hexc(c) for c in ("#151A1F", "#20262D", "#2E363F", "#404A54", "#58646F", "#76838E", "#93A0AA")]

    def crack_y(x):                                         # the broken ridge: a jagged fracture line
        return cy - 4 + 0.55 * (x - cx) + 5 * math.sin(x * 0.45) + (3 if x % 7 < 3 else -2)

    for y in range(S):
        for x in range(S):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy > R * R:
                continue
            in_shadow = (x - shadow[0]) ** 2 + (y - shadow[1]) ** 2 < shadow[2] ** 2
            if abs(y - crack_y(x)) < 1.6 and x > cx - 10:
                continue                                    # fracture gap
            nz = math.sqrt(max(0.0, 1 - (dx * dx + dy * dy) / (R * R)))
            nxv, nyv = dx / R, dy / R
            lit = max(0.0, nxv * lx + nyv * ly + nz * lz)
            lit *= 0.85 + 0.3 * (noise.fbm(x / 9.0, y / 9.0, 3) - 0.5)
            for (kx, ky, kr) in craters:
                dd = math.hypot(x - kx, y - ky)
                if dd < kr:
                    lit *= 0.7 if (x - kx) + (y - ky) < 0 else 1.15
            if in_shadow:
                px[x, y] = (12, 16, 20, 235)                 # earthshine: the dark side still reads as a body
                continue
            px[x, y] = ramp(ramp_moon, quantize(lit, len(ramp_moon), x, y))
    # thin cool rim light on the lit limb
    for y in range(S):
        for x in range(S):
            if px[x, y][3] == 0 or px[x, y][:3] == (12, 16, 20):
                continue
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if R - 1.2 <= dist <= R and dx * lx + dy * ly > 0.35 * R:
                px[x, y] = RIM_CYAN
    # detached chunks floating off the fracture
    for (ox, oy, w, h) in ((cx + 42, cy - 2, 5, 3), (cx + 50, cy + 6, 3, 2), (cx + 36, cy - 10, 2, 2)):
        for y in range(oy, oy + h):
            for x in range(ox, ox + w):
                if 0 <= x < S and 0 <= y < S:
                    px[x, y] = ramp_moon[4] if y == oy else ramp_moon[2]
    return upscale(img, 4)


def astrolabe_ghost():
    S = 256
    c = S / 2
    rnd = random.Random(5505)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    col = (0x88, 0x64, 0x2C)

    def plot(x, y, a):
        xi, yi = int(round(x)), int(round(y))
        if 0 <= xi < S and 0 <= yi < S and px[xi, yi][3] < a:
            px[xi, yi] = (col[0], col[1], col[2], a)

    for r, a in ((122, 34), (110, 26), (78, 30), (44, 22)):
        gaps = [(rnd.uniform(0, 360), rnd.uniform(14, 40)) for _ in range(3)]
        steps = int(2 * math.pi * r * 1.5)
        for i in range(steps):
            ang = 360 * i / steps
            if any((ang - g0) % 360 < gl for g0, gl in gaps):
                continue                                      # broken arcs
            plot(c + r * math.cos(math.radians(ang)), c + r * math.sin(math.radians(ang)), a)
    for deg in range(0, 360, 5):                              # degree ticks on the outer ring
        L = 6 if deg % 30 == 0 else 3
        for k in range(L):
            rr = 122 - k
            plot(c + rr * math.cos(math.radians(deg)), c + rr * math.sin(math.radians(deg)), 30)
    for deg in range(0, 360, 30):                             # partial spokes
        if rnd.random() < 0.35:
            continue
        for rr in range(48, 108):
            plot(c + rr * math.cos(math.radians(deg)), c + rr * math.sin(math.radians(deg)), 18)
    # a rete pointer (curved star pointer), never a filled disc so it cannot read as a node
    for t in range(0, 100):
        ang = math.radians(200 + t * 0.9)
        rr = 50 + t * 0.55
        plot(c + rr * math.cos(ang), c + rr * math.sin(ang), 24)
    return upscale(img, 4)


def _arch(px, ox, oy, rnd, kind):
    cx, cy = ox + 32, oy + 44
    noise = ValueNoise(rnd.randint(0, 10_000), period=16)
    break_ang = rnd.uniform(25, 70)
    for y in range(oy, oy + 64):
        for x in range(ox, ox + 64):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(-dy, dx)) % 360
            inside = False
            if kind == "arch":
                inside = 17 <= d <= 27 and dy <= 2 and not (ang < break_ang + 6 * noise.at(x / 3, y / 3))
                inside |= (cx - 27 <= x <= cx - 19) and cy <= y <= cy + 14 - (x % 3)     # left pillar stub
            elif kind == "column":
                inside = abs(dx) <= 6 and -26 + 3 * math.sin(x) <= dy <= 16 and not (dy < -16 and dx > 2)
                inside |= abs(dx) <= 9 and 12 <= dy <= 16
            elif kind == "block":
                inside = abs(dx + 0.4 * dy) <= 14 and abs(dy - 0.25 * dx) <= 9 and noise.at(x / 4, y / 4) > 0.18
            else:  # lintel shard
                inside = -24 <= dx <= 22 - (abs(dy) * 2) and -6 <= dy <= 5 and noise.at(x / 4, y / 4) > 0.15
            if not inside:
                continue
            shade = 0.55 + 0.35 * (noise.fbm(x / 5.0, y / 5.0, 3) - 0.5) - 0.012 * (y - oy)
            shade += 0.15 if dx > 0 else 0.0
            col = ramp(STONE, quantize(shade, len(STONE), x, y))
            px[x, y] = col
    # carved celestial markings, bronze inlay and a faint amber reflection
    for i in range(7):
        a = math.radians(20 + i * 22)
        mx, my = int(cx + 22 * math.cos(a)), int(cy - 22 * math.sin(a))
        if px[mx, my][3] and kind == "arch":
            px[mx, my] = STONE[5]
    for x in range(ox + 6, ox + 58):
        y = cy + 15 if kind != "lintel" else cy
        if px[x, y][3] and x % 4 != 0:
            px[x, y] = BRONZE_INLAY
    for _ in range(6):
        x, y = ox + rnd.randint(36, 56), oy + rnd.randint(18, 48)
        if px[x, y][3]:
            px[x, y] = AMBER


def observatory_ruins():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(6606)
    for (ox, oy), kind in zip(((0, 0), (64, 0), (0, 64), (64, 64)), ("arch", "column", "block", "lintel")):
        _arch(px, ox, oy, rnd, kind)
    return upscale(img, 4)


def observatory_platform():
    w, h = 256, 40
    noise = ValueNoise(7707, period=32)       # period divides the width -> horizontally tileable
    rnd = random.Random(7708)
    gaps = {rnd.randrange(0, 256 // 8) * 8 for _ in range(5)}   # broken, missing top blocks
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            top = 10 + int(3 * noise.at(x / 8.0, 0.5))
            if (x // 8) * 8 in gaps:
                top += 4 + (x % 8) // 3
            if y < top:
                continue
            row = (y - top) // 8
            off = 12 if row % 2 else 0
            brick = ((x + off) // 24) % 8
            mortar = (y - top) % 8 == 0 or (x + off) % 24 == 0
            crack = noise.at(x / 3.0, y / 3.0) > 0.86
            shade = 0.6 - 0.016 * (y - top) + 0.3 * (noise.fbm(x / 6.0, y / 6.0, 3) - 0.5) + 0.04 * (brick % 3)
            if y - top <= 1:
                shade += 0.2                       # worn top edge catches the cool light
            if mortar or crack:
                shade -= 0.3
            px[x, y] = ramp(PLATFORM_STONE, quantize(shade, len(PLATFORM_STONE), x, y))
            if y == top + 12 and x % 64 in (8, 9, 24, 40, 41, 56) and not mortar:
                px[x, y] = PLATFORM_STONE[5]       # carved celestial glyph band
            if y == top + 18 and x % 4 != 1 and not crack:
                px[x, y] = BRONZE_INLAY
            if y == top + 12 and x % 64 == 24:
                px[x, y] = AMBER
    return upscale(img, 4)


def astral_motes():
    img = Image.new("RGBA", (64, 16), (0, 0, 0, 0))
    px = img.load()
    cols = [hexc("#7FD6E0"), hexc("#A98BE0"), hexc("#E8D9A8"), hexc("#8FE3C8")]
    for i, col in enumerate(cols):
        r = 3 + (i % 2)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= r:
                    a = int(110 * (1 - d / (r + 0.5)))
                    a = (a // 30) * 30              # stepped falloff
                    if a > 0:
                        px[i * 16 + x, y] = (col[0], col[1], col[2], a)
    return img


def generate():
    out = []
    for name, fn in (("space_base", space_base), ("stars_far", stars_far), ("nebula_far", nebula_far),
                     ("moon_crescent", moon_crescent), ("astrolabe_ghost", astrolabe_ghost),
                     ("observatory_ruins", observatory_ruins), ("observatory_platform", observatory_platform),
                     ("astral_motes", astral_motes)):
        out.append(save(fn(), BG_DIR + name + ".png"))
    return out
