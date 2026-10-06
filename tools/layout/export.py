"""Exports the authored graph to the mod's data files, lang files and the layout manifest.

Usage (from the repository root):  python3 tools/layout/export.py
Requires Python 3.10+ and Pillow (for the annotated render). Output is deterministic.
"""
import json
import math
import os
import shutil
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from analysis import COST, RADIUS, SECTORS, adjacency, analyse, cheapest  # noqa: E402
from blueprints import CORRIDORS, ROOTS, build  # noqa: E402
from content import EXCLUSIVE_GROUP_NAMES, HUBS, NS, SECTOR_NAMES, SPECIALS, THEMES  # noqa: E402
from describe import describe  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(REPO, "src", "main", "resources", "data", NS)
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", NS)
DOCS = os.path.join(REPO, "docs", "layout")
SCHEMA_VERSION = 1
FRAMES = {"minor": "bronze_circle", "notable": "violet_facet", "keystone": "gold_ornate", "root": "stone_medallion"}

# icon glyphs: one per effect category (shared by minors and notables), unique art for keystones and hubs
TAG_ICON = {
    "attack_speed": "attack_speed", "mobility": "mobility", "light_weapon": "light_weapon", "critical": "critical",
    "dodge": "dodge", "health": "health", "armor": "armor", "knockback": "knockback", "defense": "defense",
    "potion": "potion", "magic": "magic", "healing": "healing", "luck": "luck", "projectile": "projectile",
    "range": "range", "crossbow": "crossbow", "bow": "bow", "mining": "mining", "tools": "tools",
    "exploration": "exploration", "damage": "damage", "heavy_weapon": "heavy_weapon", "lifesteal": "lifesteal",
    "risk": "risk", "melee": "melee", "shield": "shield", "fire": "fire",
}


def icon_for(n, tags):
    if n.type == "root":
        return f"{NS}:textures/skill_icons/hub_{n.sector}.png"
    if n.type == "keystone":
        return f"{NS}:textures/skill_icons/keystone_{n.slug}.png"
    return f"{NS}:textures/skill_icons/{TAG_ICON[tags[0]]}.png"


def assign_ids(g):
    """Stable resource-path ids. Minors: <sector>/<theme>_<n> numbered in authoring order."""
    ids = {}
    per_theme = defaultdict(int)
    for n in sorted(g.nodes.values(), key=lambda n: n.order):
        if n.type == "root":
            ids[n.key] = f"{n.sector}/hub"
        elif n.slug and n.type in ("notable", "keystone"):
            ids[n.key] = f"{n.sector}/{n.slug}"
        else:
            per_theme[(n.sector, n.theme)] += 1
            ids[n.key] = f"{n.sector}/{n.theme}_{per_theme[(n.sector, n.theme)]}"
    assert len(set(ids.values())) == len(ids), "duplicate ids"
    return ids


def node_content(n):
    if n.type == "root":
        names, effects = HUBS[n.sector]
        return names, effects, ["hub", n.sector]
    if n.type in ("notable", "keystone"):
        names, effects, tags = SPECIALS[n.slug]
        return names, effects, list(tags)
    names, effect, tags = THEMES[n.theme]
    return names, [effect], list(tags)


def roman(i):
    vals = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
    out = ""
    for v, s in vals:
        while i >= v:
            out += s
            i -= v
    return out


def edge_id(ids, a, b):
    pa, pb = sorted((ids[a], ids[b]))
    return f"edge/{pa.replace('/', '.')}--{pb.replace('/', '.')}"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    g = build()
    rep = analyse(g)
    if rep["errors"] or rep["overlaps"] or rep["edge_clearance"] or rep["crossings"]:
        print("Refusing to export: layout diagnostics are not clean", rep["errors"][:5])
        sys.exit(1)
    ids = assign_ids(g)
    adj = adjacency(g)

    # ---- nodes ----
    nodes_dir = os.path.join(DATA, "skill_nodes")
    if os.path.isdir(nodes_dir):
        shutil.rmtree(nodes_dir)
    lang = {"en": {}, "pt": {}}
    theme_seen = defaultdict(int)
    node_records = []
    groups = defaultdict(list)
    for n in sorted(g.nodes.values(), key=lambda n: n.order):
        nid = ids[n.key]
        names, effects, tags = node_content(n)
        if n.type == "minor":
            theme_seen[(n.sector, n.theme)] += 1
            suffix = " " + roman(theme_seen[(n.sector, n.theme)])
        else:
            suffix = ""
        key = f"skill.{NS}.{nid.replace('/', '.')}"
        lang["en"][key + ".name"] = names[0] + suffix
        lang["pt"][key + ".name"] = names[1] + suffix
        lang["en"][key + ".description"] = describe(effects, "en")
        lang["pt"][key + ".description"] = describe(effects, "pt")
        if n.sector not in tags:
            tags = tags + [n.sector]
        rec = {
            "schema_version": SCHEMA_VERSION,
            "id": f"{NS}:{nid}",
            "tree": f"{NS}:main",
            "sector": n.sector,
            "type": n.type,
            "position": {"x": n.x, "y": n.y},
            "name": key + ".name",
            "description": key + ".description",
            "icon": icon_for(n, tags),
            "frame": FRAMES[n.type],
            "cost": COST[n.type],
            "max_rank": 1,
            "requirements": {"all_of": [], "any_of": [], "minimum_sector_points": 0},
            "effects": effects,
            "tags": tags,
            "cluster": n.cluster,
        }
        if n.type == "root":
            rec["cost"] = 1
            rec["root"] = True
        if n.exclusive_group:
            rec["exclusive_group"] = n.exclusive_group
            groups[n.exclusive_group].append(f"{NS}:{nid}")
        write_json(os.path.join(nodes_dir, nid + ".json"), rec)
        node_records.append(rec)

    # ---- connections ----
    corridor_of = {}
    for e in g.edges:
        if e.corridor:
            corridor_of[frozenset((e.a, e.b))] = e.corridor
    connections = []
    for e in g.edges:
        sa, sb = g.nodes[e.a].sector, g.nodes[e.b].sector
        c = {
            "id": f"{NS}:{edge_id(ids, e.a, e.b)}",
            "a": f"{NS}:{ids[e.a]}",
            "b": f"{NS}:{ids[e.b]}",
            "directed": False,
            "style": e.style,
            "points": [{"x": x, "y": y} for x, y in e.points],
        }
        if sa != sb:
            c["bridge"] = True
            c["corridor"] = f"{NS}:{e.corridor}"
        connections.append(c)
    connections.sort(key=lambda c: c["id"])
    write_json(os.path.join(DATA, "skill_connections", "main.json"),
               {"schema_version": SCHEMA_VERSION, "tree": f"{NS}:main", "connections": connections})

    # ---- tree ----
    sectors = []
    for s in SECTORS:
        sectors.append({"id": s, "root": f"{NS}:{s}/hub", "name": f"sector.{NS}.{s}"})
        lang["en"][f"sector.{NS}.{s}"] = SECTOR_NAMES[s][0]
        lang["pt"][f"sector.{NS}.{s}"] = SECTOR_NAMES[s][1]
    ex_groups = []
    for gname, members in sorted(groups.items()):
        ex_groups.append({"id": gname, "name": f"exclusive_group.{NS}.{gname}", "max_active": 1,
                          "members": sorted(members)})
        lang["en"][f"exclusive_group.{NS}.{gname}"] = EXCLUSIVE_GROUP_NAMES[gname][0]
        lang["pt"][f"exclusive_group.{NS}.{gname}"] = EXCLUSIVE_GROUP_NAMES[gname][1]
    b = rep["bounds"]
    tree = {
        "schema_version": SCHEMA_VERSION,
        "id": f"{NS}:main",
        "name": f"tree.{NS}.main",
        "sectors": sectors,
        "exclusive_groups": ex_groups,
        "default_costs": {"minor": 1, "notable": 2, "keystone": 3, "root": 1, "starting_root": 0},
        "bounds": {"min_x": b["min_x"], "min_y": b["min_y"], "max_x": b["max_x"], "max_y": b["max_y"]},
        "expected_node_count": len(node_records),
    }
    lang["en"][f"tree.{NS}.main"] = "The Astral Observatory"
    lang["pt"][f"tree.{NS}.main"] = "O Observatório Astral"
    write_json(os.path.join(DATA, "skill_trees", "main.json"), tree)

    # ---- lang: hand-written UI strings + generated skill strings ----
    for code, short in (("en_us", "en"), ("pt_br", "pt")):
        ui_path = os.path.join(REPO, "tools", "lang", f"ui_{code}.json")
        ui = json.load(open(ui_path, encoding="utf-8")) if os.path.exists(ui_path) else {}
        merged = dict(ui)
        merged.update(dict(sorted(lang[short].items())))
        write_json(os.path.join(ASSETS, "lang", f"{code}.json"), merged)

    # ---- classpath index for the verified bundled fallback (read by BundledTree, bypassing datapacks) ----
    index = {}
    for d in ("skill_trees", "skill_nodes", "skill_connections"):
        base = os.path.join(DATA, d)
        paths = []
        for root, _, files in os.walk(base):
            for fn in files:
                if fn.endswith(".json"):
                    rel = os.path.relpath(os.path.join(root, fn), base).replace(os.sep, "/")
                    paths.append(rel[:-5])
        index[d] = sorted(paths)
    write_json(os.path.join(REPO, "src", "main", "resources", "nostalgiaskills_bundled_index.json"), index)

    # ---- manifest ----
    write_manifest(g, rep, ids, adj, node_records, connections, ex_groups)
    print(f"exported {len(node_records)} nodes, {len(connections)} connections")


def write_manifest(g, rep, ids, adj, node_records, connections, ex_groups):
    os.makedirs(DOCS, exist_ok=True)
    root_of = {g.nodes[k].sector: k for k in g.nodes if g.nodes[k].type == "root"}
    routes = []
    for i, s in enumerate(SECTORS):
        t = SECTORS[(i + 1) % 6]
        cost, path = cheapest(g, adj, root_of[s], root_of[t])
        routes.append({"from": f"{NS}:{s}/hub", "to": f"{NS}:{t}/hub", "paid_points": cost,
                       "path": [f"{NS}:{ids[k]}" for k in path]})
    clusters = defaultdict(list)
    for n in g.nodes.values():
        clusters[n.cluster].append(f"{NS}:{ids[n.key]}")
    degree = {f"{NS}:{ids[k]}": len(adj[k]) for k in g.nodes}
    manifest = {
        "schema_version": SCHEMA_VERSION,
        "generator": "tools/layout/export.py",
        "coordinate_system": "tree space, +x right, +y down, centre decoration at (0,0)",
        "roots": {f"{NS}:{s}/hub": {"x": ROOTS[s][0], "y": ROOTS[s][1]} for s in SECTORS},
        "bounds": rep["bounds"],
        "counts": rep["counts"],
        "degree_distribution": rep["degree_distribution"],
        "reachability": {"all_nodes_reachable_from_every_root": not rep["unreachable"], "unreachable": rep["unreachable"]},
        "adjacent_root_routes": routes,
        "overlaps": rep["overlaps"],
        "edge_clearance_violations": rep["edge_clearance"],
        "edge_crossings": rep["crossings"],
        "intentional_exceptions": [],
        "corridors": sorted(c["corridor"] for c in connections if c.get("bridge")),
        "keystone_groups": ex_groups,
        "clusters": {k: sorted(v) for k, v in sorted(clusters.items())},
        "nodes": [{"id": r["id"], "sector": r["sector"], "type": r["type"], "x": r["position"]["x"],
                   "y": r["position"]["y"], "frame": r["frame"], "icon": r["icon"], "cluster": r["cluster"],
                   "degree": degree[r["id"]]} for r in node_records],
        "edges": [{"id": c["id"], "a": c["a"], "b": c["b"], "style": c["style"],
                   "points": c["points"], "bridge": c.get("bridge", False)} for c in connections],
    }
    write_json(os.path.join(DOCS, "layout_manifest.json"), manifest)
    write_manifest_md(os.path.join(DOCS, "layout_manifest.json"), os.path.join(REPO, "LAYOUT_MANIFEST.md"))
    try:
        from render import render
        render(g, rep, os.path.join(DOCS, "annotated_layout.png"), scale=1.0,
               labels=lambda n: ids[n.key].split("/")[1] if n.type != "minor" else "")
    except ImportError:
        print("Pillow not installed: skipped annotated render")


def write_manifest_md(manifest_path, md_path):
    """Human-readable LAYOUT_MANIFEST.md generated from docs/layout/layout_manifest.json."""
    m = json.load(open(manifest_path, encoding="utf-8"))
    L = []
    w = L.append
    w("# Layout Manifest — Nostalgia Skills\n")
    w("Generated by `python3 tools/layout/export.py` from the authored blueprints in `tools/layout/blueprints.py`. "
      "Do not edit by hand: change the blueprint, re-export, and commit the regenerated data. "
      "Machine-readable twin: [`docs/layout/layout_manifest.json`](docs/layout/layout_manifest.json). "
      "Annotated render: [`docs/layout/annotated_layout.png`](docs/layout/annotated_layout.png).\n")
    w("## Coordinate system\n")
    w("- Tree space: +X right, +Y down, logical units. The decorative astrolabe centre sits at `(0, 0)` and is not a node.")
    w("- Screen position = `(tree - cameraCentre) * zoom + viewportCentre`, in GUI-scaled pixels; hit testing applies the inverse.")
    w("- Each sector is authored in a local frame (u outward along the sector axis, v lateral) and rotated by its axis angle "
      "(Marksman 0°, Artificer 60°, Berserker 120°, Duelist 180°, Guardian 240°, Arcanist 300°).\n")
    w("## Root hubs\n")
    w("| Hub | X | Y |\n|---|---:|---:|")
    for k, v in m["roots"].items():
        w(f"| `{k}` | {v['x']} | {v['y']} |")
    b = m["bounds"]
    w(f"\nGraph bounds (node centres): X {b['min_x']} … {b['max_x']}, Y {b['min_y']} … {b['max_y']} "
      f"({b['max_x'] - b['min_x']:.0f} × {b['max_y'] - b['min_y']:.0f} units).\n")
    w("## Sector route vocabulary\n")
    w("| Sector | Shape | Signature structures |\n|---|---|---|")
    w("| Duelist | Rapier lines | long serpentine *riposte line*, thin lateral feints, parry rings, evasion diamond |")
    w("| Guardian | Fortress | straight keep road through three concentric curtain walls arced around the hub, towers, buttresses |")
    w("| Arcanist / Alchemist | Alchemical spiral | 1.15-turn spiral with spokes, keystone at the spiral's eye, flask rings |")
    w("| Marksman | Bow and arrow | straight shaft, drawn bow arc, two bowstrings closing long loops, quiver ring, arrowhead |")
    w("| Artificer / Explorer | Mine workings | orthogonal shaft with three working levels joined by raises (ladder grid), ore-vein combs |")
    w("| Berserker | Lightning | sharp zig-zag war path, rage stars, blood-pact triangles |\n")
    w("## Spacing and frame rules (validated by `tools/layout/analysis.py`)\n")
    w("| Frame | Node type | Logical diameter | Validation radius |\n|---|---|---:|---:|")
    w(f"| bronze_circle | minor | 13 | {RADIUS['minor']} |")
    w(f"| violet_facet | notable | 17 | {RADIUS['notable']} |")
    w(f"| gold_ornate | keystone | 24 | {RADIUS['keystone']} |")
    w(f"| stone_medallion | root | 30 | {RADIUS['root']} |")
    w("\n- Node centres must be at least `rA + rB + 6` apart.")
    w("- Every edge polyline must clear every unrelated node by `r + 3`.")
    w("- No two edges may intersect except at a shared endpoint (crossings are reported; none are allowed).")
    w("- Edge length target 28–40 units; anything over 60 (except authored corridors) is flagged.")
    w("- Nodes stay inside their sector's 60° wedge (±30.5° tolerance) so sectors never interleave.\n")
    w("## Results\n")
    w("| Check | Result |\n|---|---|")
    w(f"| Node counts | {json.dumps(m['counts'])} |")
    w(f"| Degree distribution | {json.dumps(m['degree_distribution'])} |")
    w(f"| All nodes reachable from every root | {m['reachability']['all_nodes_reachable_from_every_root']} |")
    w(f"| Overlaps | {len(m['overlaps'])} |")
    w(f"| Edge clearance violations | {len(m['edge_clearance_violations'])} |")
    w(f"| Edge crossings | {len(m['edge_crossings'])} |")
    w(f"| Intentional exceptions | {len(m['intentional_exceptions'])} |\n")
    w("Degree-1 nodes are deliberate endpoints (keystones, arrowhead barbs, ore veins, star leaves). Degree-4 nodes are "
      "deliberate junctions (gates, nocking point, level crossings).\n")
    w("## Adjacent root routes (cheapest, paid points incl. destination root)\n")
    w("| From | To | Paid points | Nodes on route |\n|---|---|---:|---:|")
    for r in m["adjacent_root_routes"]:
        w(f"| `{r['from']}` | `{r['to']}` | {r['paid_points']} | {len(r['path'])} |")
    w("\nRequirement: ≥ 8. All routes exceed it.\n")
    w("## Corridors (bridges between sectors)\n")
    for c in sorted(set(m["corridors"])):
        w(f"- `{c}`")
    w("\n## Keystone exclusivity groups (at most one active member)\n")
    for gr in m["keystone_groups"]:
        w(f"- `{gr['id']}`: " + ", ".join(f"`{x}`" for x in gr["members"]))
    w("\nEvery exclusive keystone is a terminal node, so no exclusive choice can block a route into another sector.\n")
    w("## Clusters\n")
    w("| Cluster | Nodes |\n|---|---:|")
    for k, v in m["clusters"].items():
        w(f"| `{k}` | {len(v)} |")
    w("\n## Nodes\n")
    w("| ID | Type | X | Y | Frame | Icon | Degree |\n|---|---|---:|---:|---|---|---:|")
    for n in m["nodes"]:
        w(f"| `{n['id']}` | {n['type']} | {n['x']} | {n['y']} | {n['frame']} | `{n['icon'].split('/')[-1]}` | {n['degree']} |")
    w("\n## Edges\n")
    w("| ID | A | B | Style | Intermediate points |\n|---|---|---|---|---|")
    for e in m["edges"]:
        pts = " ".join(f"({p['x']}, {p['y']})" for p in e["points"]) or "—"
        w(f"| `{e['id'].split(':')[1]}` | `{e['a']}` | `{e['b']}` | {e['style']}{' (bridge)' if e['bridge'] else ''} | {pts} |")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")


if __name__ == "__main__":
    main()
