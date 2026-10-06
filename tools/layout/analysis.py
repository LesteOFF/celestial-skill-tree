"""Authoring diagnostics for the generated graph: spacing, edge clearance, crossings,
degrees, reachability and traversal costs. Mirrors the runtime validator's structural rules."""
import heapq
import math
from collections import defaultdict

from geometry import seg_point_distance, segments_intersect

RADIUS = {"minor": 6.5, "notable": 8.5, "keystone": 12.0, "root": 15.0}
COST = {"minor": 1, "notable": 2, "keystone": 3, "root": 1}
NODE_GAP = 6.0      # minimum clear gap between two node frames
EDGE_CLEARANCE = 3.0  # minimum clear gap between an unrelated edge and a node frame
SECTORS = ["marksman", "artificer", "berserker", "duelist", "guardian", "arcanist"]
SECTOR_ANGLE = {s: 60 * i for i, s in enumerate(SECTORS)}


def polyline(g, e):
    return [g.pos(e.a)] + e.points + [g.pos(e.b)]


def adjacency(g):
    adj = defaultdict(set)
    for e in g.edges:
        adj[e.a].add(e.b)
        adj[e.b].add(e.a)
    return adj


def cheapest(g, adj, src, dst):
    """Paid points to reach dst from an allocated src (dst cost included)."""
    dist = {src: 0}
    prev = {}
    pq = [(0, src)]
    while pq:
        d, k = heapq.heappop(pq)
        if k == dst:
            path = [k]
            while path[-1] in prev:
                path.append(prev[path[-1]])
            return d, list(reversed(path))
        if d > dist.get(k, 1e9):
            continue
        for m in adj[k]:
            nd = d + COST[g.nodes[m].type]
            if nd < dist.get(m, 1e9):
                dist[m] = nd
                prev[m] = k
                heapq.heappush(pq, (nd, m))
    return None, []


def analyse(g):
    rep = {"errors": [], "warnings": []}
    nodes = list(g.nodes.values())
    # counts
    counts = defaultdict(lambda: defaultdict(int))
    for n in nodes:
        counts[n.sector][n.type] += 1
    rep["counts"] = {s: dict(c) for s, c in counts.items()}
    for s, c in counts.items():
        want = {"root": 1, "minor": 64, "notable": 12, "keystone": 3}
        if dict(c) != want:
            rep["errors"].append(f"count {s}: {dict(c)} != {want}")
    # node spacing
    overlaps = []
    for i, a in enumerate(nodes):
        for b in nodes[i + 1:]:
            d = math.hypot(a.x - b.x, a.y - b.y)
            need = RADIUS[a.type] + RADIUS[b.type] + NODE_GAP
            if d < need:
                overlaps.append((a.key, b.key, round(d, 1), need))
    rep["overlaps"] = overlaps
    # edge clearance & crossings
    clear = []
    segs = []
    for ei, e in enumerate(g.edges):
        pl = polyline(g, e)
        for s0, s1 in zip(pl, pl[1:]):
            segs.append((ei, s0, s1))
        for n in nodes:
            if n.key in (e.a, e.b):
                continue
            d = min(seg_point_distance(s0, s1, (n.x, n.y)) for s0, s1 in zip(pl, pl[1:]))
            if d < RADIUS[n.type] + EDGE_CLEARANCE:
                clear.append((e.a, e.b, n.key, round(d, 1)))
    rep["edge_clearance"] = clear
    crossings = []
    for i in range(len(segs)):
        ei, a0, a1 = segs[i]
        for j in range(i + 1, len(segs)):
            ej, b0, b1 = segs[j]
            if ei == ej:
                continue
            ea, eb = g.edges[ei], g.edges[ej]
            if {ea.a, ea.b} & {eb.a, eb.b}:
                continue
            if segments_intersect(a0, a1, b0, b1):
                crossings.append((ea.a, ea.b, eb.a, eb.b))
    rep["crossings"] = sorted(set(crossings))
    # duplicates
    seen = set()
    for e in g.edges:
        k = frozenset((e.a, e.b))
        if k in seen:
            rep["errors"].append(f"duplicate edge {e.a}-{e.b}")
        seen.add(k)
    adj = adjacency(g)
    deg = defaultdict(int)
    for n in nodes:
        deg[len(adj[n.key])] += 1
    rep["degree_distribution"] = dict(sorted(deg.items()))
    rep["high_degree"] = [(n.key, n.type, len(adj[n.key])) for n in nodes if len(adj[n.key]) > 4]
    # reachability
    roots = [n.key for n in nodes if n.type == "root"]
    seen = {roots[0]}
    stack = [roots[0]]
    while stack:
        k = stack.pop()
        for m in adj[k]:
            if m not in seen:
                seen.add(m)
                stack.append(m)
    rep["unreachable"] = [k for k in g.nodes if k not in seen]
    if rep["unreachable"] and len(roots) == 6:
        rep["errors"].append(f"{len(rep['unreachable'])} nodes unreachable from first root")
    # root-to-root costs
    root_of = {g.nodes[r].sector: r for r in roots}
    rr = {}
    for i, s in enumerate(SECTORS):
        t = SECTORS[(i + 1) % 6]
        if s not in root_of or t not in root_of:
            continue
        c, path = cheapest(g, adj, root_of[s], root_of[t])
        rr[f"{s}->{t}"] = c
        if c is None or c < 8:
            rep["errors"].append(f"adjacent roots {s}->{t} cost {c} < 8")
    rep["adjacent_root_costs"] = rr
    # edge lengths
    lens = []
    for e in g.edges:
        pl = polyline(g, e)
        lens.append(sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pl, pl[1:])))
    rep["edge_length"] = {"min": round(min(lens), 1), "max": round(max(lens), 1),
                          "mean": round(sum(lens) / len(lens), 1)}
    rep["long_edges"] = [(e.a, e.b, round(l, 1)) for e, l in zip(g.edges, lens) if l > 60 and e.style != "corridor"]
    rep["short_edges"] = [(e.a, e.b, round(l, 1)) for e, l in zip(g.edges, lens) if l < 26]
    # sector wedge discipline
    wedge = []
    for n in nodes:
        if n.type == "root":
            continue
        ang = math.degrees(math.atan2(n.y, n.x)) % 360
        off = (ang - SECTOR_ANGLE[n.sector] + 180) % 360 - 180
        if abs(off) > 30.5:
            wedge.append((n.key, n.sector, round(off, 1)))
    rep["outside_wedge"] = wedge
    xs = [n.x for n in nodes]
    ys = [n.y for n in nodes]
    rep["bounds"] = {"min_x": min(xs), "max_x": max(xs), "min_y": min(ys), "max_y": max(ys)}
    return rep
