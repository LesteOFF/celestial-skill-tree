"""Graph builder used by the sector blueprints (see blueprints.py)."""
import math

from geometry import catmull_rom, cumulative, point_at, rot, seg_point_distance, unrot

TARGET_SPACING = 34.0


def _r(v):
    return round(v * 2) / 2.0  # half-unit grid keeps exported JSON stable and readable


class Node:
    __slots__ = ("key", "sector", "type", "x", "y", "cluster", "theme", "slug", "exclusive_group", "order")

    def __init__(self, key, sector, ntype, x, y, cluster, theme, order):
        self.key = key
        self.sector = sector
        self.type = ntype
        self.x = _r(x)
        self.y = _r(y)
        self.cluster = cluster
        self.theme = theme
        self.slug = None
        self.exclusive_group = None
        self.order = order


class Edge:
    __slots__ = ("a", "b", "points", "style", "corridor")

    def __init__(self, a, b, points, style, corridor=None):
        self.a, self.b = a, b
        self.points = [(_r(x), _r(y)) for x, y in points]
        self.style = style
        self.corridor = corridor


class Graph:
    def __init__(self):
        self.nodes = {}
        self.edges = []
        self._counter = 0
        self.corridors = []

    def add_node(self, sector, ntype, x, y, cluster, theme):
        self._counter += 1
        key = f"n{self._counter:04d}"
        self.nodes[key] = Node(key, sector, ntype, x, y, cluster, theme, self._counter)
        return key

    def add_edge(self, a, b, points=(), style="path", corridor=None):
        assert a != b, "self edge"
        self.edges.append(Edge(a, b, list(points), style, corridor))

    def pos(self, key):
        n = self.nodes[key]
        return (n.x, n.y)


def _bend_points(samples, acc, s0, s1, a, b):
    """Return 0-2 intermediate points that preserve the authored curve between two node positions."""
    mid, _ = point_at(samples, acc, (s0 + s1) / 2)
    if seg_point_distance(a, b, mid) < 1.5:
        return []
    if s1 - s0 > 44:
        p1, _ = point_at(samples, acc, s0 + (s1 - s0) / 3)
        p2, _ = point_at(samples, acc, s0 + 2 * (s1 - s0) / 3)
        return [p1, p2]
    return [mid]


class Sector:
    def __init__(self, graph, name, angle_deg, root_xy):
        self.g = graph
        self.name = name
        self.angle = angle_deg
        self.root = graph.add_node(name, "root", root_xy[0], root_xy[1], f"{name}:hub", "root")
        self.named = {"root": self.root}

    # local (u, v) -> tree space
    def p(self, u, v):
        return rot(self.angle, u, v)

    def node(self, uv, theme, cluster, ntype="minor"):
        x, y = self.p(*uv)
        return self.g.add_node(self.name, ntype, x, y, f"{self.name}:{cluster}", theme)

    def link(self, a, b, via=(), style="path"):
        self.g.add_edge(a, b, [self.p(*q) for q in via], style)

    def chain(self, start, waypoints, n, theme, cluster, end=None, smooth=True, style="path", name=None):
        """Place n new nodes evenly along a curve from `start` through local `waypoints`
        (and on to existing node `end` when given). Returns the new node keys."""
        pts = [self.g.pos(start)] + [self.p(*w) for w in waypoints]
        if end is not None:
            pts.append(self.g.pos(end))
        if not smooth:
            return self._polyline_chain(start, pts, n, theme, cluster, end, style, name)
        if smooth:
            samples = catmull_rom(pts)
        else:
            samples = []
            for a, b in zip(pts, pts[1:]):
                for k in range(20):
                    samples.append((a[0] + (b[0] - a[0]) * k / 20, a[1] + (b[1] - a[1]) * k / 20))
            samples.append(pts[-1])
        acc = cumulative(samples)
        total = acc[-1]
        divisions = n + 1 if end is not None else n
        step = total / divisions
        keys, prev, prev_s = [], start, 0.0
        for i in range(1, n + 1):
            s = step * i
            if not smooth:
                # snap to waypoint corners so jagged chains keep their sharp bends
                s = self._snap_to_corner(pts, acc, samples, s, step)
            (x, y), _ = point_at(samples, acc, s)
            k = self.g.add_node(self.name, "minor", x, y, f"{self.name}:{cluster}", theme)
            self.g.add_edge(prev, k, _bend_points(samples, acc, prev_s, s, self.g.pos(prev), (x, y)) if smooth else
                            self._corner_points(pts, samples, acc, prev_s, s), style)
            keys.append(k)
            prev, prev_s = k, s
        if end is not None:
            self.g.add_edge(prev, end, _bend_points(samples, acc, prev_s, total, self.g.pos(prev), self.g.pos(end))
                            if smooth else self._corner_points(pts, samples, acc, prev_s, total), style)
        if name:
            self.named[name] = keys
        return keys

    def _polyline_chain(self, start, pts, n, theme, cluster, end, style, name):
        """Straight segments with a node on every corner; nodes spread evenly inside each segment."""
        lens = [math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pts, pts[1:])]
        divisions = n + (1 if end is not None else 0)
        assert divisions >= len(lens), "polyline chain needs at least one node per segment"
        ideal = [l * divisions / sum(lens) for l in lens]
        alloc = [max(1, int(x)) for x in ideal]
        while sum(alloc) < divisions:
            i = max(range(len(lens)), key=lambda j: ideal[j] - alloc[j])
            alloc[i] += 1
        while sum(alloc) > divisions:
            i = max((j for j in range(len(lens)) if alloc[j] > 1), key=lambda j: alloc[j] - ideal[j])
            alloc[i] -= 1
        keys, prev = [], start
        for si, (a, b) in enumerate(zip(pts, pts[1:])):
            for j in range(1, alloc[si] + 1):
                last_point = si == len(lens) - 1 and j == alloc[si]
                if last_point and end is not None:
                    self.g.add_edge(prev, end, [], style)
                    continue
                t = j / alloc[si]
                k = self.g.add_node(self.name, "minor", a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t,
                                    f"{self.name}:{cluster}", theme)
                self.g.add_edge(prev, k, [], style)
                keys.append(k)
                prev = k
        if name:
            self.named[name] = keys
        return keys

    @staticmethod
    def _corner_s(pts, samples, acc):
        out = []
        idx = 0
        for p in pts[1:-1]:
            best = min(range(idx, len(samples)), key=lambda i: (samples[i][0] - p[0]) ** 2 + (samples[i][1] - p[1]) ** 2)
            out.append(acc[best])
            idx = best
        return out

    def _snap_to_corner(self, pts, acc, samples, s, step):
        for c in self._corner_s(pts, samples, acc):
            if abs(c - s) < step * 0.35:
                return c
        return s

    def _corner_points(self, pts, samples, acc, s0, s1):
        out = []
        for c in self._corner_s(pts, samples, acc):
            if s0 + 0.5 < c < s1 - 0.5:
                out.append(point_at(samples, acc, c)[0])
        return out

    def ring(self, attach, center, n, theme, cluster, radius=None, attach_index_angle=None, close=True):
        """n nodes on a circle around local `center`; node 0 faces `attach`. Returns keys in ring order."""
        radius = radius or TARGET_SPACING / (2 * math.sin(math.pi / n))
        cx, cy = self.p(*center)
        ax, ay = self.g.pos(attach)
        # slide the authored centre along the attach direction so the entry edge has standard length
        dist = math.hypot(cx - ax, cy - ay)
        want = radius + TARGET_SPACING
        cx, cy = ax + (cx - ax) * want / dist, ay + (cy - ay) * want / dist
        base = math.atan2(ay - cy, ax - cx) if attach_index_angle is None else math.radians(attach_index_angle)
        keys = []
        for i in range(n):
            a = base + 2 * math.pi * i / n
            keys.append(self.g.add_node(self.name, "minor", cx + radius * math.cos(a), cy + radius * math.sin(a),
                                        f"{self.name}:{cluster}", theme))
        self.g.add_edge(attach, keys[0], [], "path")
        last = n if close else n - 1
        for i in range(last):
            a0 = base + 2 * math.pi * i / n
            a1 = base + 2 * math.pi * (i + 1) / n
            am = (a0 + a1) / 2
            self.g.add_edge(keys[i], keys[(i + 1) % n], [(cx + radius * math.cos(am), cy + radius * math.sin(am))], "ring")
        return keys

    def mark(self, key, ntype, slug, exclusive_group=None):
        n = self.g.nodes[key]
        n.type = ntype
        n.slug = slug
        n.exclusive_group = exclusive_group
        return key


def spur(sec, attach, waypoints, n_minors, theme, cluster, slug, ntype="keystone", exclusive_group=None, smooth=True):
    """A short optional branch: n minors followed by a terminal notable/keystone."""
    keys = sec.chain(attach, waypoints, n_minors + 1, theme, cluster, smooth=smooth)
    sec.mark(keys[-1], ntype, slug, exclusive_group)
    return keys


def local_of(sec, key):
    from geometry import unrot
    return unrot(sec.angle, *sec.g.pos(key))


def toward(sec, attach, target, dist=TARGET_SPACING + 2):
    """Local point at `dist` from `attach` in the direction of local `target`."""
    ax, ay = local_of(sec, attach)
    dx, dy = target[0] - ax, target[1] - ay
    l = math.hypot(dx, dy)
    return (ax + dx * dist / l, ay + dy * dist / l)


def spur_dir(sec, attach, heading, n_minors, theme, cluster, slug, ntype="keystone", exclusive_group=None,
             turn=0.0):
    """Optional branch leaving `attach` along local heading (degrees), gently turning by `turn` degrees per step."""
    u, v = local_of(sec, attach)
    pts = []
    h = heading
    for i in range(n_minors + 1):
        step = TARGET_SPACING + (6 if (i == n_minors and ntype == "keystone") else 0)
        u += step * math.cos(math.radians(h))
        v += step * math.sin(math.radians(h))
        pts.append((u, v))
        h += turn
    keys = []
    prev = attach
    for i, q in enumerate(pts):
        k = sec.node(q, theme, cluster)
        sec.link(prev, k)
        keys.append(k)
        prev = k
    sec.mark(keys[-1], ntype, slug, exclusive_group)
    return keys


def star(sec, attach, center, leaf_dirs, theme, cluster, slug, leaf_len=34.0):
    """A notable junction with minor leaves pointing along local angles (degrees)."""
    ax, ay = local_of(sec, attach)
    steps = max(1, round(math.hypot(center[0] - ax, center[1] - ay) / (TARGET_SPACING + 2)))
    if steps == 1:
        center = toward(sec, attach, center)
    stem = sec.chain(attach, [center], steps, theme, cluster)
    hub = stem[-1]
    sec.mark(hub, "notable", slug)
    leaves = []
    for d in leaf_dirs:
        a = math.radians(d)
        leaves.append(sec.chain(hub, [(center[0] + leaf_len * math.cos(a), center[1] + leaf_len * math.sin(a))], 1,
                                theme, cluster)[0])
    return hub, leaves


def diamond(sec, attach, entry, left, right, tip, theme, cluster, slug):
    """attach -> entry -> (left | right) -> tip(notable): two parallel two-step routes."""
    e = sec.chain(attach, [entry], 1, theme, cluster)[0]
    l = sec.chain(e, [left], 1, theme, cluster)[0]
    r = sec.chain(e, [right], 1, theme, cluster)[0]
    t = sec.node(tip, theme, cluster)
    sec.link(l, t)
    sec.link(r, t)
    sec.mark(t, "notable", slug)
    return e, l, r, t


def nearest(sec, keys, target):
    tx, ty = sec.g.pos(target)
    return min(keys, key=lambda k: (sec.g.pos(k)[0] - tx) ** 2 + (sec.g.pos(k)[1] - ty) ** 2)
