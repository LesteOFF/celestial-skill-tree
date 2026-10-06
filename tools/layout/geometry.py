"""Deterministic geometry primitives for authoring the Nostalgia Skills graph.

Every sector is authored in a *local* frame:
  u = distance outward along the sector axis (from the tree centre)
  v = lateral offset; positive v turns clockwise on screen (towards the
      sector whose axis angle is 60 degrees larger, because tree space has +Y down).
The builder converts local points to tree space with a fixed rotation per
sector. Nothing here is random: the same input always yields the same output.
"""
import math

SPACING_MIN = 28.0
SPACING_MAX = 40.0


def rot(angle_deg, u, v):
    a = math.radians(angle_deg)
    c, s = math.cos(a), math.sin(a)
    return (u * c - v * s, u * s + v * c)


def unrot(angle_deg, x, y):
    a = math.radians(-angle_deg)
    c, s = math.cos(a), math.sin(a)
    return (x * c - y * s, x * s + y * c)


def catmull_rom(points, samples_per_segment=40):
    """Centripetal-free uniform Catmull-Rom through all points (endpoints included)."""
    if len(points) == 2:
        (x0, y0), (x1, y1) = points
        return [(x0 + (x1 - x0) * t / samples_per_segment, y0 + (y1 - y0) * t / samples_per_segment)
                for t in range(samples_per_segment + 1)]
    pts = [points[0]] + list(points) + [points[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for k in range(samples_per_segment):
            t = k / samples_per_segment
            t2, t3 = t * t, t * t * t
            out.append(tuple(
                0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                       + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in range(2)))
    out.append(points[-1])
    return out


def cumulative(poly):
    acc = [0.0]
    for (x0, y0), (x1, y1) in zip(poly, poly[1:]):
        acc.append(acc[-1] + math.hypot(x1 - x0, y1 - y0))
    return acc


def point_at(poly, acc, s):
    if s <= 0:
        return poly[0], 0
    for i in range(1, len(acc)):
        if acc[i] >= s:
            seg = acc[i] - acc[i - 1]
            t = 0 if seg == 0 else (s - acc[i - 1]) / seg
            (x0, y0), (x1, y1) = poly[i - 1], poly[i]
            return (x0 + (x1 - x0) * t, y0 + (y1 - y0) * t), i
    return poly[-1], len(poly) - 1


def seg_point_distance(a, b, p):
    (ax, ay), (bx, by), (px, py) = a, b, p
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    if l2 == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def segments_intersect(p1, p2, p3, p4):
    def orient(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    d1, d2 = orient(p3, p4, p1), orient(p3, p4, p2)
    d3, d4 = orient(p1, p2, p3), orient(p1, p2, p4)
    return ((d1 > 0) != (d2 > 0)) and ((d3 > 0) != (d4 > 0)) and 0 not in (d1, d2, d3, d4)
