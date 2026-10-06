"""Quick authoring preview: build, analyse, render to the path given as argv[1]."""
import json, sys
from blueprints import build
from analysis import analyse
from render import render
g = build()
rep = analyse(g)
for k in ("counts", "degree_distribution", "adjacent_root_costs", "edge_length", "bounds"):
    print(k, rep[k])
for k in ("overlaps", "edge_clearance", "crossings", "high_degree", "long_edges", "short_edges", "outside_wedge"):
    print(k, len(rep[k]), rep[k][:12])
print("errors", rep["errors"][:10])
render(g, rep, sys.argv[1], scale=float(sys.argv[2]) if len(sys.argv) > 2 else 1.0,
       labels=lambda n: n.slug or "")
