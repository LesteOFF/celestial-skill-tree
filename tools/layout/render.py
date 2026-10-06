"""Annotated debug render of the authored graph (requires Pillow)."""
from PIL import Image, ImageDraw

from analysis import RADIUS, polyline

SECTOR_COLOR = {
    "duelist": (90, 200, 230), "guardian": (150, 170, 160), "arcanist": (180, 120, 230),
    "marksman": (120, 210, 120), "artificer": (230, 180, 80), "berserker": (230, 90, 70),
}


def render(g, rep, path, scale=1.0, labels=None, margin=60):
    b = rep["bounds"]
    w = int((b["max_x"] - b["min_x"]) * scale + 2 * margin)
    h = int((b["max_y"] - b["min_y"]) * scale + 2 * margin)
    img = Image.new("RGB", (w, h), (8, 14, 18))
    d = ImageDraw.Draw(img)

    def T(p):
        return ((p[0] - b["min_x"]) * scale + margin, (p[1] - b["min_y"]) * scale + margin)

    # hexagon of roots and centre marker
    d.ellipse([T((-8, -8)), T((8, 8))], outline=(60, 60, 60))
    bad_edges = {frozenset((a, b_)) for a, b_, *_ in rep["edge_clearance"]}
    cross_edges = set()
    for a, b_, c, e in rep["crossings"]:
        cross_edges.add(frozenset((a, b_)))
        cross_edges.add(frozenset((c, e)))
    for e in g.edges:
        k = frozenset((e.a, e.b))
        col = (90, 70, 50)
        if e.corridor:
            col = (200, 200, 90)
        if k in cross_edges:
            col = (255, 0, 255)
        if k in bad_edges:
            col = (255, 40, 40)
        d.line([T(p) for p in polyline(g, e)], fill=col, width=max(1, int(2 * scale)))
    bad_nodes = set()
    for a, b_, *_ in rep["overlaps"]:
        bad_nodes |= {a, b_}
    for n in g.nodes.values():
        r = RADIUS[n.type] * scale
        x, y = T((n.x, n.y))
        col = SECTOR_COLOR[n.sector]
        outline = (255, 0, 0) if n.key in bad_nodes else (20, 20, 20)
        if n.type == "notable":
            d.rectangle([x - r, y - r, x + r, y + r], fill=(169, 119, 220), outline=col, width=2)
        elif n.type == "keystone":
            d.regular_polygon((x, y, r), 8, fill=(243, 209, 92), outline=col)
        elif n.type == "root":
            d.ellipse([x - r, y - r, x + r, y + r], fill=(124, 141, 136), outline=col, width=3)
        else:
            d.ellipse([x - r, y - r, x + r, y + r], fill=col, outline=outline, width=2 if n.key in bad_nodes else 1)
        if labels:
            text = labels(n)
            if text:
                d.text((x + r + 2, y - 5), text, fill=(230, 225, 215))
    img.save(path)
