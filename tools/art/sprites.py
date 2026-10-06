"""Node frames, state overlays, skill icons and the Orb of Reconfiguration item sprite.

Frames/overlays are drawn directly at 32x32. Icons are authored on a 16x16 grid with a tiny vector DSL,
auto-shaded (light from the upper left), outlined, and upscaled x2 so they share one pixel scale.
"""
import math

from PIL import Image

from common import hexc, save, upscale

OUTLINE = hexc("#120C08")


# ------------------------------------------------------------------ frames and overlays (32x32)

def _ring_frame(ramp, interior, r_out, r_in, notches=0, spikes=0, facet=0):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    c = 15.5
    for y in range(32):
        for x in range(32):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            outer = r_out
            if spikes:
                outer = r_out - 2.2 + 2.2 * max(0.0, math.cos(ang * spikes)) ** 6
            if facet:
                d = max(abs(dx), abs(dy), (abs(dx) + abs(dy)) / 1.32)   # octagonal facet metric
            if d > outer:
                continue
            if d < r_in - 0.5:
                px[x, y] = interior
                continue
            light = -(dx + dy) / (abs(dx) + abs(dy) + 1e-6)            # upper-left lit
            idx = 1
            if light > 0.45:
                idx = 0
            elif light < -0.45:
                idx = 2
            if notches and int((math.degrees(ang) + 360) // (360 / notches)) % 2 == 0 and d > r_out - 2.5:
                idx = 2
            if d > outer - 1.0 or d < r_in + 0.5:
                idx = 3
            px[x, y] = ramp[idx]
    return img


def frames():
    out = []
    bronze = [hexc("#C89A4A"), hexc("#88642C"), hexc("#5A3F1A"), hexc("#2A1C0C")]
    out.append(save(_ring_frame(bronze, hexc("#120E0B", 238), 15.6, 11.8), "gui/skill_tree/frames/bronze_circle.png"))
    violet = [hexc("#D7B8F2"), hexc("#A977DC"), hexc("#6B4596"), hexc("#241236")]
    out.append(save(_ring_frame(violet, hexc("#140F1C", 240), 15.4, 11.6, facet=1), "gui/skill_tree/frames/violet_facet.png"))
    gold = [hexc("#FFF0A8"), hexc("#F3D15C"), hexc("#B98A22"), hexc("#3A2808")]
    out.append(save(_ring_frame(gold, hexc("#16110A", 240), 15.8, 11.0, spikes=8), "gui/skill_tree/frames/gold_ornate.png"))
    stone = [hexc("#B5C2BE"), hexc("#7C8D88"), hexc("#55635F"), hexc("#1E2624")]
    med = _ring_frame(stone, hexc("#0E1212", 242), 15.6, 10.6, notches=12)
    p = med.load()
    for y in range(32):                                    # thin bronze inner ring
        for x in range(32):
            if abs(math.hypot(x - 15.5, y - 15.5) - 10.2) < 0.55:
                p[x, y] = hexc("#88642C")
    out.append(save(med, "gui/skill_tree/frames/stone_medallion.png"))

    def ring(name, r0, r1, col, alpha_fn, dash=0):
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        q = img.load()
        for y in range(32):
            for x in range(32):
                d = math.hypot(x - 15.5, y - 15.5)
                if r0 <= d <= r1:
                    ang = (math.degrees(math.atan2(y - 15.5, x - 15.5)) + 360) % 360
                    if dash and int(ang // (360 / dash)) % 2:
                        continue
                    a = alpha_fn(d)
                    if a > 0:
                        q[x, y] = (col[0], col[1], col[2], a)
        out.append(save(img, f"gui/skill_tree/frames/{name}.png"))
        return img

    ring("ring_allocated", 9.5, 16.0, hexc("#F3D15C"), lambda d: int((1 - (d - 9.5) / 6.5) * 4) * 40)
    ring("ring_available", 14.2, 15.6, hexc("#F6E6A6"), lambda d: 200)
    ring("ring_pending", 13.6, 15.6, hexc("#7FD6E8"), lambda d: 255, dash=16)
    ring("ring_search", 14.0, 15.6, hexc("#6FE3C4"), lambda d: 230, dash=24)

    x_img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    xp = x_img.load()
    for y in range(32):
        for x in range(32):
            a = abs((x - 15.5) - (y - 15.5)) / math.sqrt(2)
            b = abs((x - 15.5) + (y - 15.5)) / math.sqrt(2)
            if max(abs(x - 15.5), abs(y - 15.5)) > 12:
                continue
            if min(a, b) < 1.6:
                xp[x, y] = hexc("#E04A3C")
            elif min(a, b) < 2.8:
                xp[x, y] = OUTLINE
    out.append(save(x_img, "gui/skill_tree/frames/mark_conflict.png"))

    lock = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    lp = lock.load()
    for y in range(32):
        for x in range(32):
            body = 8 <= x <= 23 and 14 <= y <= 27
            shackle = abs(math.hypot(x - 15.5, y - 14) - 6) < 1.6 and y <= 14
            if body:
                lp[x, y] = hexc("#B0A69A") if y < 18 else hexc("#857B70")
                if x in (8, 23) or y in (14, 27):
                    lp[x, y] = OUTLINE
            elif shackle:
                lp[x, y] = hexc("#C8C0B4")
    lp[15, 20] = lp[16, 20] = lp[15, 21] = lp[16, 21] = lp[15, 22] = lp[16, 22] = OUTLINE
    out.append(save(lock, "gui/skill_tree/frames/mark_locked.png"))

    focus = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    fp = focus.load()
    for i in range(9):
        for (x, y) in ((i, 0), (0, i), (31 - i, 0), (31, i), (i, 31), (0, 31 - i), (31 - i, 31), (31, 31 - i)):
            fp[x, y] = (255, 255, 255, 255)
        for (x, y) in ((i, 1), (1, i), (31 - i, 1), (30, i), (i, 30), (1, 31 - i), (31 - i, 30), (30, 31 - i)):
            if i < 8:
                fp[x, y] = (255, 255, 255, 200)
    out.append(save(focus, "gui/skill_tree/frames/focus.png"))
    return out


# ------------------------------------------------------------------ icon DSL (16x16 grid)

PALETTES = {
    "red": ("#FF8A7A", "#D8453A", "#8A1E18"), "steel": ("#E2E8EC", "#A9B3BA", "#646E76"),
    "bronze": ("#E6B866", "#B88838", "#6E4A18"), "gold": ("#FFF0A8", "#F3D15C", "#A8781C"),
    "violet": ("#DCC0F6", "#A977DC", "#5E3A8A"), "teal": ("#A8F0EC", "#4FC4C0", "#1F6E70"),
    "green": ("#B8F0A0", "#5CC048", "#2A6E22"), "wood": ("#D8A868", "#9A6A34", "#5A3A16"),
    "sky": ("#CDEBFF", "#7FB8E8", "#3A6A9A"), "bone": ("#F4ECDC", "#CFC2A8", "#8A7E66"),
    "fire": ("#FFE08A", "#F28A2E", "#A8341A"), "stone": ("#C8D0CC", "#8C9A96", "#505C58"),
}


class Canvas:
    def __init__(self):
        self.px = [[None] * 16 for _ in range(16)]

    def _put(self, x, y, key):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = key

    def line(self, x0, y0, x1, y1, key, w=1.0):
        for y in range(16):
            for x in range(16):
                cx, cy = x + 0.5, y + 0.5
                dx, dy = x1 - x0, y1 - y0
                l2 = dx * dx + dy * dy or 1e-9
                t = max(0.0, min(1.0, ((cx - x0) * dx + (cy - y0) * dy) / l2))
                if math.hypot(cx - (x0 + t * dx), cy - (y0 + t * dy)) <= w / 2 + 0.2:
                    self._put(x, y, key)

    def circle(self, cx, cy, r, key, ring=0.0, a0=None, a1=None):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                inside = abs(d - r) <= ring / 2 + 0.25 if ring else d <= r + 0.15
                if inside and a0 is not None:
                    ang = (math.degrees(math.atan2(y + 0.5 - cy, x + 0.5 - cx)) + 360) % 360
                    inside = (a0 <= ang <= a1) if a0 <= a1 else (ang >= a0 or ang <= a1)
                if inside:
                    self._put(x, y, key)

    def poly(self, pts, key):
        for y in range(16):
            for x in range(16):
                cx, cy = x + 0.5, y + 0.5
                inside = False
                j = len(pts) - 1
                for i in range(len(pts)):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if (yi > cy) != (yj > cy) and cx < (xj - xi) * (cy - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                if inside:
                    self._put(x, y, key)

    def rect(self, x0, y0, x1, y1, key):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self._put(x, y, key)

    def dot(self, x, y, key):
        self._put(x, y, key)

    def render(self, palettes):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        p = img.load()
        for y in range(16):
            for x in range(16):
                k = self.px[y][x]
                if k is None:
                    continue
                light, mid, dark = (hexc(c) for c in PALETTES[palettes[k]])
                up_left_empty = (x == 0 or self.px[y][x - 1] != k) or (y == 0 or self.px[y - 1][x] != k)
                down_right_empty = (x == 15 or self.px[y][x + 1] != k) or (y == 15 or self.px[y + 1][x] != k)
                p[x, y] = light if up_left_empty and not down_right_empty else dark if down_right_empty and not up_left_empty else mid
        for y in range(16):                                   # 1px dark outline around every shape
            for x in range(16):
                if self.px[y][x] is None and any(
                        0 <= x + dx < 16 and 0 <= y + dy < 16 and self.px[y + dy][x + dx] is not None
                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    p[x, y] = OUTLINE
        return upscale(img, 2)


def sword(c, x0, y0, x1, y1, blade="a", hilt="b", w=1.6):
    c.line(x0, y0, x1, y1, blade, w)
    mx, my = x0 + (x1 - x0) * 0.22, y0 + (y1 - y0) * 0.22
    nx, ny = (y1 - y0), -(x1 - x0)
    n = math.hypot(nx, ny) or 1
    c.line(mx - nx / n * 2.6, my - ny / n * 2.6, mx + nx / n * 2.6, my + ny / n * 2.6, hilt, 1.4)
    c.line(x0, y0, mx, my, hilt, 1.6)


def heart(c, key):
    c.circle(5.2, 6.0, 3.0, key)
    c.circle(10.8, 6.0, 3.0, key)
    c.poly([(2.3, 7.0), (13.7, 7.0), (8.0, 13.6)], key)


def shield(c, key, boss=None):
    c.poly([(3, 3), (13, 3), (13, 8), (8, 14), (3, 8)], key)
    if boss:
        c.circle(8, 7, 1.8, boss)


def flask(c, glass, liquid):
    c.rect(6, 2, 9, 5, glass)
    c.circle(8, 10, 4.6, glass)
    c.circle(8, 10.5, 3.2, liquid)
    c.rect(6, 1, 9, 1, "b")


def arrow(c, x0, y0, x1, y1, shaft="a", head="b"):
    c.line(x0, y0, x1, y1, shaft, 1.2)
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy)
    ux, uy = dx / n, dy / n
    c.poly([(x1 + ux, y1 + uy), (x1 - 3 * ux + 2 * uy, y1 - 3 * uy - 2 * ux), (x1 - 3 * ux - 2 * uy, y1 - 3 * uy + 2 * ux)], head)
    c.line(x0, y0, x0 + 2 * ux + 1.5 * uy, y0 + 2 * uy - 1.5 * ux, head, 1)
    c.line(x0, y0, x0 + 2 * ux - 1.5 * uy, y0 + 2 * uy + 1.5 * ux, head, 1)


def skull(c, key, eye="b"):
    c.circle(8, 7, 5, key)
    c.rect(5, 10, 10, 13, key)
    c.rect(5, 6, 6, 8, eye)
    c.rect(9, 6, 10, 8, eye)
    c.dot(7, 10, eye)
    c.dot(8, 10, eye)


def pickaxe(c, head="a", handle="b"):
    c.line(4, 13, 11, 4, handle, 1.5)
    c.circle(10, 6, 6.2, head, ring=1.8, a0=200, a1=340)


def gear(c, key):
    c.circle(8, 8, 4.5, key)
    for i in range(8):
        a = math.radians(i * 45)
        c.line(8 + 4 * math.cos(a), 8 + 4 * math.sin(a), 8 + 6.5 * math.cos(a), 8 + 6.5 * math.sin(a), key, 2)
    c.circle(8, 8, 1.6, "b")


ICONS = {
    "attack_speed": ({"a": "steel", "b": "sky"}, lambda c: (c.line(3, 13, 12, 3, "a", 1.6), c.line(6, 14, 14, 6, "a", 1.2),
                                                         c.line(1, 9, 5, 9, "b", 1), c.line(2, 12, 5, 12, "b", 1))),
    "mobility": ({"a": "sky", "b": "steel"}, lambda c: (c.poly([(2, 3), (8, 8), (2, 13), (4, 8)], "a"),
                                                      c.poly([(8, 3), (14, 8), (8, 13), (10, 8)], "b"))),
    "light_weapon": ({"a": "steel", "b": "bronze"}, lambda c: sword(c, 3, 13, 14, 2, w=1.1)),
    "critical": ({"a": "gold", "b": "red"}, lambda c: (c.poly([(8, 1), (9.6, 6.4), (15, 8), (9.6, 9.6), (8, 15), (6.4, 9.6), (1, 8), (6.4, 6.4)], "a"),
                                                     c.circle(8, 8, 1.6, "b"))),
    "dodge": ({"a": "sky", "b": "steel"}, lambda c: (c.circle(8, 8, 5.5, "a", ring=1.5, a0=40, a1=300),
                                                   c.poly([(12, 2), (14.5, 6.5), (10, 6)], "a"), c.circle(8, 8, 1.5, "b"))),
    "health": ({"a": "red", "b": "red"}, lambda c: heart(c, "a")),
    "armor": ({"a": "steel", "b": "stone"}, lambda c: (c.poly([(3, 3), (6, 2), (8, 4), (10, 2), (13, 3), (13, 8), (11, 9), (11, 14), (5, 14), (5, 9), (3, 8)], "a"),
                                                     c.line(8, 5, 8, 13, "b", 1))),
    "knockback": ({"a": "steel", "b": "stone"}, lambda c: (c.line(8, 2, 8, 13, "a", 1.6), c.circle(8, 2.8, 1.6, "b", ring=1.0),
                                                         c.circle(8, 9, 5, "a", ring=1.4, a0=20, a1=160), c.line(5, 5, 11, 5, "a", 1.2))),
    "defense": ({"a": "steel", "b": "bronze"}, lambda c: shield(c, "a", "b")),
    "potion": ({"a": "teal", "b": "steel"}, lambda c: flask(c, "b", "a")),
    "magic": ({"a": "violet", "b": "teal"}, lambda c: (c.circle(8, 8, 6, "a", ring=1.2),
                                                     c.poly([(8, 3), (9.2, 6.8), (13, 8), (9.2, 9.2), (8, 13), (6.8, 9.2), (3, 8), (6.8, 6.8)], "b"))),
    "healing": ({"a": "green", "b": "green"}, lambda c: (c.rect(6, 2, 9, 13, "a"), c.rect(2, 6, 13, 9, "a"))),
    "luck": ({"a": "green", "b": "wood"}, lambda c: (c.circle(5.5, 5.5, 2.7, "a"), c.circle(10.5, 5.5, 2.7, "a"),
                                                   c.circle(5.5, 10.5, 2.7, "a"), c.circle(10.5, 10.5, 2.7, "a"), c.line(9, 9, 13, 15, "b", 1))),
    "projectile": ({"a": "wood", "b": "steel"}, lambda c: arrow(c, 2, 14, 13, 3)),
    "range": ({"a": "red", "b": "steel"}, lambda c: (c.circle(8, 8, 5.5, "b", ring=1.2), c.line(8, 1, 8, 5, "a", 1), c.line(8, 11, 8, 15, "a", 1),
                                                   c.line(1, 8, 5, 8, "a", 1), c.line(11, 8, 15, 8, "a", 1), c.dot(7, 7, "a"))),
    "crossbow": ({"a": "wood", "b": "steel"}, lambda c: (c.line(8, 3, 8, 14, "a", 1.8), c.circle(8, 9, 6, "b", ring=1.3, a0=200, a1=340),
                                                       c.line(2.5, 6.5, 13.5, 6.5, "b", 0.8))),
    "bow": ({"a": "wood", "b": "bone"}, lambda c: (c.circle(4, 8, 8, "a", ring=1.6, a0=300, a1=60), c.line(8, 1.5, 8, 14.5, "b", 0.8))),
    "mining": ({"a": "steel", "b": "wood"}, lambda c: pickaxe(c)),
    "tools": ({"a": "steel", "b": "wood"}, lambda c: (c.line(4, 13, 10, 7, "b", 1.6), c.poly([(7, 4), (11, 1), (15, 5), (11, 9)], "a"))),
    "exploration": ({"a": "bronze", "b": "red"}, lambda c: (c.circle(8, 8, 6.2, "a", ring=1.3), c.poly([(8, 3), (9.5, 8), (6.5, 8)], "b"),
                                                          c.poly([(8, 13), (9.5, 8), (6.5, 8)], "a"))),
    "damage": ({"a": "steel", "b": "bronze"}, lambda c: sword(c, 8, 14.5, 8, 1.5, w=2.2)),
    "heavy_weapon": ({"a": "steel", "b": "wood"}, lambda c: (c.line(8, 2, 8, 15, "b", 1.5), c.poly([(8, 3), (3, 1), (2, 7), (8, 6)], "a"),
                                                           c.poly([(8, 3), (13, 1), (14, 7), (8, 6)], "a"))),
    "lifesteal": ({"a": "red", "b": "red"}, lambda c: (c.circle(8, 10, 4, "a"), c.poly([(8, 1), (11.5, 8.5), (4.5, 8.5)], "a"))),
    "risk": ({"a": "bone", "b": "red"}, lambda c: skull(c, "a")),
    "melee": ({"a": "steel", "b": "bronze"}, lambda c: (sword(c, 3, 13, 13, 3, w=1.3), sword(c, 13, 13, 3, 3, w=1.3))),
    "shield": ({"a": "bronze", "b": "steel"}, lambda c: (c.circle(8, 8, 6, "a"), c.circle(8, 8, 2.2, "b"), c.circle(8, 8, 6, "b", ring=0.9))),
    "fire": ({"a": "fire", "b": "gold"}, lambda c: (c.poly([(8, 1), (12, 7), (12.5, 11), (8, 15), (3.5, 11), (4, 7), (6, 9)], "a"),
                                                  c.poly([(8, 7), (10, 11), (8, 14), (6, 11)], "b"))),
}

KEYSTONE_ICONS = {
    "swift_hands": ({"a": "gold", "b": "steel"}, lambda c: (sword(c, 2, 12, 9, 2, "b", "a", 1.1), sword(c, 7, 14, 14, 4, "b", "a", 1.1))),
    "thousand_cuts": ({"a": "gold", "b": "red"}, lambda c: [c.line(2 + i * 3, 13, 5 + i * 3, 3, "a" if i % 2 else "b", 1) for i in range(4)]),
    "wind_dancer": ({"a": "sky", "b": "gold"}, lambda c: (c.circle(8, 8, 5.5, "a", ring=1.3, a0=0, a1=270), c.circle(8, 8, 2.8, "b", ring=1.2, a0=90, a1=360),
                                                        c.line(1, 13, 7, 13, "a", 1))),
    "bastion": ({"a": "stone", "b": "gold"}, lambda c: (c.rect(4, 4, 11, 14, "a"), c.rect(3, 2, 4, 4, "a"), c.rect(7, 2, 8, 4, "a"), c.rect(11, 2, 12, 4, "a"),
                                                      c.rect(7, 10, 8, 14, "b"))),
    "living_aegis": ({"a": "gold", "b": "red"}, lambda c: (shield(c, "a"), c.circle(8, 7, 2.2, "b"))),
    "rooted_stance": ({"a": "wood", "b": "green"}, lambda c: (c.line(8, 2, 8, 10, "a", 2), c.line(8, 10, 3, 14, "a", 1.2), c.line(8, 10, 13, 14, "a", 1.2),
                                                            c.line(8, 10, 8, 15, "a", 1.2), c.circle(8, 4, 3, "b"))),
    "volatile_concentrate": ({"a": "violet", "b": "fire"}, lambda c: (flask(c, "a", "b"), c.line(13, 2, 15, 0, "b", 1), c.line(2, 3, 0, 1, "b", 1))),
    "elixir_of_ages": ({"a": "gold", "b": "teal"}, lambda c: (c.poly([(3, 2), (13, 2), (8, 8)], "a"), c.poly([(3, 14), (13, 14), (8, 8)], "a"),
                                                            c.poly([(5, 13), (11, 13), (8, 10)], "b"))),
    "arcane_ward": ({"a": "violet", "b": "gold"}, lambda c: (shield(c, "a"), c.circle(8, 8, 2.5, "b", ring=1.0))),
    "patient_aim": ({"a": "gold", "b": "sky"}, lambda c: (c.circle(8, 8, 6, "a", ring=1.1), c.circle(8, 8, 2.5, "b"), c.dot(8, 8, "a"))),
    "volley_doctrine": ({"a": "wood", "b": "gold"}, lambda c: (arrow(c, 1, 12, 9, 4, "a", "b"), arrow(c, 4, 15, 12, 7, "a", "b"), arrow(c, 6, 10, 14, 2, "a", "b"))),
    "heavy_bolts": ({"a": "steel", "b": "gold"}, lambda c: (c.line(3, 13, 12, 4, "a", 2.6), c.poly([(14, 2), (9, 4), (12, 7)], "b"))),
    "master_excavator": ({"a": "gold", "b": "wood"}, lambda c: (pickaxe(c, "a", "b"), c.circle(13, 13, 1.8, "a"))),
    "tinkers_bargain": ({"a": "bronze", "b": "gold"}, lambda c: gear(c, "a")),
    "wayfinder": ({"a": "gold", "b": "sky"}, lambda c: (c.poly([(8, 0.5), (9.5, 6.5), (15.5, 8), (9.5, 9.5), (8, 15.5), (6.5, 9.5), (0.5, 8), (6.5, 6.5)], "a"),
                                                      c.circle(8, 8, 1.5, "b"))),
    "reckless_fury": ({"a": "red", "b": "fire"}, lambda c: (skull(c, "a", "b"), c.line(3, 4, 1, 1, "b", 1.2), c.line(13, 4, 15, 1, "b", 1.2))),
    "juggernaut": ({"a": "stone", "b": "gold"}, lambda c: (c.circle(8, 9, 5.5, "a"), c.poly([(3, 6), (1, 1), (6, 4)], "b"), c.poly([(13, 6), (15, 1), (10, 4)], "b"))),
    "death_wish": ({"a": "bone", "b": "violet"}, lambda c: (skull(c, "a", "b"), c.circle(8, 7, 6.5, "b", ring=0.8, a0=180, a1=360))),
}

HUB_ICONS = {
    "duelist": ({"a": "steel", "b": "bronze"}, lambda c: (sword(c, 3, 13, 13, 3, w=1.1), sword(c, 13, 13, 3, 3, w=1.1))),
    "guardian": ({"a": "stone", "b": "bronze"}, lambda c: shield(c, "a", "b")),
    "arcanist": ({"a": "teal", "b": "violet"}, lambda c: (flask(c, "b", "a"),)),
    "marksman": ({"a": "wood", "b": "steel"}, lambda c: (c.circle(4, 8, 8, "a", ring=1.4, a0=300, a1=60), arrow(c, 2, 8, 14, 8, "b", "b"))),
    "artificer": ({"a": "bronze", "b": "steel"}, lambda c: (gear(c, "a"),)),
    "berserker": ({"a": "red", "b": "steel"}, lambda c: (c.line(8, 2, 8, 15, "a", 1.5), c.poly([(8, 3), (2, 1), (2, 8), (8, 6)], "b"))),
}


def icons():
    out = []
    for table, prefix in ((ICONS, ""), (KEYSTONE_ICONS, "keystone_"), (HUB_ICONS, "hub_")):
        for name, (pal, draw) in table.items():
            c = Canvas()
            draw(c)
            out.append(save(c.render(pal), f"skill_icons/{prefix}{name}.png"))
    return out


def orb_item():
    c = Canvas()
    c.circle(8, 8.5, 6, "a")
    c.circle(8, 8.5, 6, "b", ring=1.0, a0=150, a1=210)
    c.circle(8, 8.5, 3, "c", ring=1.0, a0=200, a1=470 % 360)
    c.dot(6, 5, "c")
    c.rect(5, 1, 10, 2, "b")
    img = c.render({"a": "violet", "b": "gold", "c": "teal"})
    img = img.resize((16, 16), Image.NEAREST)            # items are 16x16
    return [save(img, "item/orb_of_reconfiguration.png")]


def generate():
    return frames() + icons() + orb_item()
