"""Quick authoring preview: build, analyse, render. Usage: preview.py out.png [scale] [sector]"""
import sys
from PIL import Image
from blueprints import build
from analysis import analyse
from render import render
g = build()
rep = analyse(g)
sector = sys.argv[3] if len(sys.argv) > 3 else None
def mine(k):
    return sector is None or any(g.nodes[x].sector == sector for x in (k if isinstance(k, (list, tuple)) else [k]))
for k in ("counts", "degree_distribution", "adjacent_root_costs", "edge_length", "bounds"):
    v = rep[k]
    if k == "counts" and sector:
        v = v.get(sector)
    print(k, v)
for k in ("overlaps", "edge_clearance", "crossings", "high_degree", "long_edges", "short_edges", "outside_wedge"):
    items = [it for it in rep[k] if mine([x for x in it if isinstance(x, str) and x.startswith("n0")])]
    print(k, len(items), items[:12])
print("errors", [e for e in rep["errors"] if not sector or sector in e])
scale = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0
render(g, rep, sys.argv[1], scale=scale, labels=lambda n: n.slug or "")
if sector:
    b = rep["bounds"]
    xs = [n.x for n in g.nodes.values() if n.sector == sector]
    ys = [n.y for n in g.nodes.values() if n.sector == sector]
    T = lambda x, y: ((x - b["min_x"]) * scale + 60, (y - b["min_y"]) * scale + 60)
    x0, y0 = T(min(xs) - 40, min(ys) - 40)
    x1, y1 = T(max(xs) + 90, max(ys) + 40)
    Image.open(sys.argv[1]).crop((int(x0), int(y0), int(x1), int(y1))).save(sys.argv[1])
