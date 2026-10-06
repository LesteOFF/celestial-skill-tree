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
    try:
        from render import render
        render(g, rep, os.path.join(DOCS, "annotated_layout.png"), scale=1.0,
               labels=lambda n: ids[n.key].split("/")[1] if n.type != "minor" else "")
    except ImportError:
        print("Pillow not installed: skipped annotated render")


if __name__ == "__main__":
    main()
