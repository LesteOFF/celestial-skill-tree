"""Computes the example builds documented in TREE_DESIGN.md from the exported data.

Each build starts from a free hub and adds the cheapest route (by point cost) to each target in order, exactly like
the in-game suggested-route feature. Totals include traversal minors and paid secondary hubs (1 point each).
Usage: python3 tools/layout/builds.py   (rewrites the generated section of TREE_DESIGN.md and docs/builds.json)
"""
import heapq
import json
import os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(REPO, "src", "main", "resources", "data", "nostalgiaskills")
NS = "nostalgiaskills:"
BUDGET = 120

BUILDS = [
    ("Fencer (Duelist)", "duelist", ["swift_hands", "flowing_strikes", "en_garde", "measured_cut", "blade_dance",
                                     "parry_and_return", "fleet_footwork", "quickstep", "lunge_mastery", "opportunist"]),
    ("Bastion Warden (Guardian)", "guardian", ["bastion", "gatekeeper", "inner_sanctum", "iron_will", "unbroken",
                                               "shield_wall", "watchtower", "sentinel", "warden", "stalwart"]),
    ("Volatile Alchemist (Arcanist)", "arcanist", ["steady_hand", "catalyst", "transmutation", "quintessence",
                                                   "philosophers_eye", "volatile_concentrate", "herbalism", "panacea"]),
    ("Patient Hunter (Marksman)", "marksman", ["eagle_eye", "long_shot", "nocking_point", "rangefinder", "upper_limb",
                                               "taut_string", "patient_aim", "full_quiver"]),
    ("Deep Delver (Artificer)", "artificer", ["deep_breath", "junction_lamp", "rich_seams", "foremans_post",
                                              "lantern_post", "tool_care", "master_excavator", "shoring"]),
    ("Reckless Fury (Berserker)", "berserker", ["battle_cry", "skull_splitter", "berserk", "rising_rage",
                                                "bloodletting", "blood_pact", "reckless_fury", "frenzy"]),
    ("Raider (Berserker -> Duelist hybrid)", "berserker", ["battle_cry", "scarred_hide", "warlords_march",
                                                           "duelist/hub", "duelist/sidestep", "duelist/riposte_rhythm",
                                                           "duelist/disarming_touch"]),
    ("Ranger (Marksman -> Artificer hybrid)", "marksman", ["taut_string", "windreader", "artificer/trailblazer",
                                                           "artificer/prospectors_camp", "artificer/wayfinder",
                                                           "crossbow_drill"]),
]


def load():
    nodes = {}
    for root, _, files in os.walk(os.path.join(DATA, "skill_nodes")):
        for fn in files:
            n = json.load(open(os.path.join(root, fn), encoding="utf-8"))
            nodes[n["id"]] = n
    conns = json.load(open(os.path.join(DATA, "skill_connections", "main.json"), encoding="utf-8"))["connections"]
    adj = defaultdict(list)
    for c in conns:
        adj[c["a"]].append(c["b"])
        adj[c["b"]].append(c["a"])
    tree = json.load(open(os.path.join(DATA, "skill_trees", "main.json"), encoding="utf-8"))
    group_of = {m: g["id"] for g in tree["exclusive_groups"] for m in g["members"]}
    return nodes, adj, group_of


def route(nodes, adj, group_of, held, target):
    blocked = set()
    for nid, gid in group_of.items():
        if nid not in held and any(group_of.get(h) == gid for h in held):
            blocked.add(nid)
    dist = {h: 0 for h in held}
    prev = {}
    pq = [(0, h) for h in sorted(held)]
    heapq.heapify(pq)
    while pq:
        d, k = heapq.heappop(pq)
        if k == target:
            break
        if d > dist.get(k, 1e9):
            continue
        for m in adj[k]:
            if m in blocked:
                continue
            nd = d + (0 if m in held else nodes[m]["cost"])
            if nd < dist.get(m, 1e9):
                dist[m] = nd
                prev[m] = k
                heapq.heappush(pq, (nd, m))
    path = []
    k = target
    while k not in held:
        path.append(k)
        k = prev[k]
    return list(reversed(path))


def summarize(nodes, ids):
    sums = defaultdict(float)
    other = []
    for i in ids:
        for e in nodes[i]["effects"]:
            if e["type"] == "attribute":
                sums[(e["attribute"], e["operation"])] += e["amount"]
            else:
                other.append(i)
                break
    return sums, sorted(set(other))


def fmt_attr(attr, op, v):
    short = attr.split(":")[1].replace("generic.", "").replace("player.", "")
    if op == "add_multiplied_base":
        return f"{short} {v * 100:+.0f}% (base)"
    if attr.startswith(NS) and short in ("crit_chance", "dodge_chance", "tool_preservation"):
        return f"{short} {v * 100:+.1f} pp"
    if attr.startswith(NS):
        return f"{short} {v * 100:+.1f}%"
    return f"{short} {v:+g}"


def main():
    nodes, adj, group_of = load()
    out = []
    md = ["<!-- BEGIN GENERATED BUILDS (tools/layout/builds.py) -->", ""]
    for name, start, targets in BUILDS:
        start_id = NS + start + "/hub"
        held = {start_id}
        order = [start_id]
        for t in targets:
            tid = NS + (t if "/" in t else f"{start}/{t}")
            if tid in held:
                continue
            p = route(nodes, adj, group_of, held, tid)
            held.update(p)
            order.extend(p)
        cost = sum(nodes[i]["cost"] for i in order[1:])
        roots = [i for i in order[1:] if nodes[i]["type"] == "root"]
        kinds = defaultdict(int)
        for i in order:
            kinds[nodes[i]["type"]] += 1
        sums, cond = summarize(nodes, order)
        keystones = [i for i in order if nodes[i]["type"] == "keystone"]
        assert cost <= BUDGET, f"{name} costs {cost} > {BUDGET}"
        out.append({"name": name, "start": start_id, "nodes": order, "paid_points": cost,
                    "secondary_roots": roots, "counts": dict(kinds), "keystones": keystones})
        md.append(f"### {name}")
        md.append("")
        md.append(f"- Start: `{start_id}` (free). Paid points: **{cost} / {BUDGET}** "
                  f"({kinds['minor']} minors, {kinds['notable']} notables, {kinds['keystone']} keystones"
                  + (f", {len(roots)} secondary hub(s) at 1 point: " + ", ".join(f"`{r}`" for r in roots) if roots else "")
                  + ").")
        md.append(f"- Targets in order: " + ", ".join(f"`{t}`" for t in targets))
        md.append("- Permanent bonuses: " + "; ".join(fmt_attr(a, o, v) for (a, o), v in sorted(sums.items())))
        md.append("- Conditional / triggered effects from: " + (", ".join(f"`{c.split(':')[1]}`" for c in cond) or "none"))
        md.append("- Keystones (with penalties): " + (", ".join(f"`{k.split(':')[1]}`" for k in keystones) or "none"))
        md.append("")
    md.append("<!-- END GENERATED BUILDS -->")
    os.makedirs(os.path.join(REPO, "docs"), exist_ok=True)
    json.dump(out, open(os.path.join(REPO, "docs", "builds.json"), "w"), indent=2)
    path = os.path.join(REPO, "TREE_DESIGN.md")
    text = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
    a, b = "<!-- BEGIN GENERATED BUILDS", "<!-- END GENERATED BUILDS -->"
    if a in text:
        text = text[:text.index(a)] + "\n".join(md) + text[text.index(b) + len(b):]
    else:
        text += "\n" + "\n".join(md) + "\n"
    open(path, "w", encoding="utf-8").write(text)
    for b_ in out:
        print(f"{b_['name']}: {b_['paid_points']} points, {len(b_['nodes'])} nodes")


if __name__ == "__main__":
    main()
