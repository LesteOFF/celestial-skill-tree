"""Hand-authored sector blueprints. Each sector uses its own route vocabulary so the six
sectors are recognisable by shape alone (they are NOT rotated copies of one template).

Local frame per sector: u = outward along the sector axis, v = lateral (+v is clockwise on
screen, i.e. towards the neighbour whose axis angle is 60 degrees larger).
"""
import math

from builder import Graph, Sector, diamond, local_of, nearest, spur, spur_dir, star

ROOTS = {  # authored tree-space coordinates (spec section 4.2)
    "duelist": (-180, 0), "guardian": (-90, -156), "arcanist": (90, -156),
    "marksman": (180, 0), "artificer": (90, 156), "berserker": (-90, 156),
}
ANGLES = {"marksman": 0, "artificer": 60, "berserker": 120, "duelist": 180, "guardian": 240, "arcanist": 300}
ORDER = ["marksman", "artificer", "berserker", "duelist", "guardian", "arcanist"]

# Corridor radii per shared boundary (sector, clockwise neighbour). Inner corridors join the lateral
# arteries; outer corridors are the longer hybrid routes. Radii differ so no two boundaries look alike.
INNER_CORRIDOR = {"marksman": 430, "artificer": 390, "berserker": 445, "duelist": 450, "guardian": 400,
                  "arcanist": 470}
OUTER_CORRIDOR = {"marksman": 700, "berserker": 690, "guardian": 680}
CORRIDOR_HALF_ANGLE = 27.4


def cp(sector, side, which="inner"):
    """Local endpoint of a corridor on `side` (+1 clockwise neighbour, -1 anticlockwise) of `sector`."""
    owner = sector if side > 0 else ORDER[(ORDER.index(sector) - 1) % 6]
    radius = (INNER_CORRIDOR if which == "inner" else OUTER_CORRIDOR)[owner]
    a = math.radians(CORRIDOR_HALF_ANGLE) * side
    return (radius * math.cos(a), radius * math.sin(a))


def duelist(g):
    """Rapier lines: a long serpentine riposte line, thin lateral feints, small parry rings."""
    s = Sector(g, "duelist", ANGLES["duelist"], ROOTS["duelist"])
    a = s.chain(s.root, [(260, -14), (340, 14), (420, -14), (500, 14), (580, -10), (650, 6), (700, 0)], 16,
                "quick_hands", "riposte_line", name="A")
    b = s.chain(s.root, [(228, 46), (290, 104), (352, 156), cp("duelist", +1)], 9, "light_step", "feint_arc", name="B")
    s.named["inner+"] = b[-1]
    c = s.chain(s.root, [(222, -56), (290, -104), (348, -150), cp("duelist", -1)], 8, "fencers_poise", "low_guard",
                name="C")
    s.named["inner-"] = c[-1]
    s.mark(a[3], "notable", "flowing_strikes")
    # quickstep branch between the feint arc and the riposte line
    q = s.chain(b[2], [(300, 64), (345, 52)], 2, "light_step", "quickstep")
    s.mark(s.chain(q[-1], [(385, 44)], 1, "light_step", "quickstep")[0], "notable", "quickstep")
    # parry circles (rings) either side of the riposte line
    r1 = s.ring(a[7], (468, 96), 5, "keen_edge", "parry_circle")
    s.mark(r1[2], "notable", "parry_and_return")
    r2 = s.ring(a[9], (535, -86), 6, "cutting_remark", "counter_circle")
    s.mark(r2[3], "notable", "opportunist")
    # feint arc continues into a fork of light-blade routes, closing a loop through the blade dance ring
    f = s.chain(b[-1], [(450, 214), (500, 222)], 2, "light_step", "fleet_fork")
    s.mark(s.chain(f[-1], [(545, 258), (565, 290)], 2, "light_step", "fleet_fork")[-1], "notable", "fleet_footwork")
    r3 = s.ring(f[-1], (590, 180), 6, "quick_hands", "blade_dance")
    link_from = nearest(s, r3, a[13])
    s.mark(r3[(r3.index(link_from) + 3) % 6], "notable", "blade_dance")
    s.chain(link_from, [], 2, "quick_hands", "blade_dance", end=a[13])
    # low guard: diamond of evasive routes, then the swordsong trail
    _, _, _, tip = diamond(s, c[-1], (440, -206), (470, -240), (484, -184), (515, -218), "nimble", "evasion_diamond",
                           "sidestep")
    ss = s.chain(tip, [(560, -236), (600, -262), cp("duelist", -1, "outer")], 4, "nimble", "swordsong")
    s.mark(ss[-1], "notable", "riposte_rhythm")
    s.named["outer-"] = ss[-1]
    # disarming touch: short branch off the low guard
    d = s.chain(c[3], [(352, -114), (386, -122)], 2, "fencers_poise", "disarm")
    s.mark(s.chain(d[-1], [(420, -132)], 1, "fencers_poise", "disarm")[0], "notable", "disarming_touch")
    # precision star below the riposte line
    star(s, a[2], (318, -66), [-30, 20], "keen_edge", "precision_star", "measured_cut")
    # spine notables and the keystone spurs
    s.mark(a[-1], "notable", "lunge_mastery")
    s.mark(a[5], "notable", "en_garde")
    spur_dir(s, a[-1], 40, 1, "fencers_poise", "keystone_swift", "swift_hands", exclusive_group="duelist_style")
    spur_dir(s, a[-1], -40, 1, "keen_edge", "keystone_blade", "thousand_cuts", exclusive_group="duelist_style")
    spur_dir(s, r2[3], -40, 1, "nimble", "keystone_wind", "wind_dancer")
    return s


def arc(radius, degrees, cu=0.0):
    return [(cu + radius * math.cos(math.radians(d)), radius * math.sin(math.radians(d))) for d in degrees]


def arc_about(center, r_from, r_to, deg_from, deg_to, steps=10):
    """Arc around a local centre, radius and angle interpolated linearly (endpoints excluded at start)."""
    out = []
    for i in range(1, steps + 1):
        t = i / steps
        r = r_from + (r_to - r_from) * t
        d = math.radians(deg_from + (deg_to - deg_from) * t)
        out.append((center[0] + r * math.cos(d), center[1] + r * math.sin(d)))
    return out


def polar_about(center, point):
    du, dv = point[0] - center[0], point[1] - center[1]
    return math.hypot(du, dv), math.degrees(math.atan2(dv, du))


def guardian(g):
    """Fortress: concentric curtain walls arced around the hub, pierced by a straight keep road."""
    s = Sector(g, "guardian", ANGLES["guardian"], ROOTS["guardian"])
    hub = (180.0, 0.0)
    a = s.chain(s.root, [(450, 0), (720, 0)], 15, "vitality", "keep_road", name="A")
    gate1, gate2, gate3 = a[3], a[6], a[11]
    # inner curtain (radius 144 around the hub)
    c1p = s.chain(gate1, arc_about(hub, 144, 144, 0, 58), 4, "plating", "inner_curtain")
    c1m = s.chain(gate1, arc_about(hub, 144, 144, 0, -58), 4, "plating", "inner_curtain")
    # middle wall ends exactly on the two inner corridors
    rp, dp = polar_about(hub, cp("guardian", +1))
    rm, dm = polar_about(hub, cp("guardian", -1))
    w2p = s.chain(gate2, arc_about(hub, 252, rp, 0, dp), 6, "tempered", "middle_wall")
    w2m = s.chain(gate2, arc_about(hub, 252, rm, 0, dm), 6, "tempered", "middle_wall")
    s.named["inner+"], s.named["inner-"] = w2p[-1], w2m[-1]
    # buttresses between the inner curtain and the middle wall
    s.chain(c1p[-2], [], 2, "rooted", "west_buttress", end=nearest(s, w2p, c1p[-2]))
    s.chain(c1m[-2], [], 2, "rooted", "east_buttress", end=nearest(s, w2m, c1m[-2]))
    # outer wall only on the eastern (duelist) flank, ending in a tower
    w3m = s.chain(gate3, arc_about(hub, 432, 432, 0, -31), 7, "tempered", "outer_wall")
    tm = s.ring(w3m[-1], (650, -280), 4, "vitality", "east_tower")
    s.mark(tm[2], "notable", "beacon_keep")
    # western watch road from the middle wall out to the warded gate corridor
    road = s.chain(w2p[-1], [(420, 196), (500, 230), cp("guardian", +1, "outer")], 7, "rooted", "watch_road")
    s.named["outer+"] = road[-1]
    # barracks between the middle wall and the watch road; armoury diamond on the east side
    bh, leaves = star(s, w2p[2], (500, 110), [30, 110, -40], "vitality", "barracks", "warden")
    post = s.ring(a[12], (660, 110), 5, "vitality", "sentinel_post")
    s.mark(post[2], "notable", "sentinel")
    s.chain(w2m[4], [], 3, "rooted", "last_line", end=nearest(s, w3m, w2m[4]))
    diamond(s, w2m[2], (490, -110), (520, -150), (530, -90), (565, -128), "bulwark_drill", "armoury", "shield_drill")
    for k, slug in ((gate1, "gatekeeper"), (gate2, "inner_sanctum"), (gate3, "unbroken"), (a[8], "iron_will"),
                    (c1p[-1], "shield_wall"), (c1m[-1], "stalwart"), (road[3], "watchtower"), (w3m[3], "oathbound")):
        s.mark(k, "notable", slug)
    spur_dir(s, a[-1], 0, 1, "vitality", "keystone_bastion", "bastion", exclusive_group="guardian_vow")
    spur_dir(s, tm[3], -10, 1, "bulwark_drill", "keystone_aegis", "living_aegis", exclusive_group="guardian_vow")
    spur_dir(s, leaves[0], 20, 0, "plating", "keystone_root", "rooted_stance")
    return s


def arcanist(g):
    """Alchemical spiral: one long winding helix with spokes, flanked by flask clusters."""
    s = Sector(g, "arcanist", ANGLES["arcanist"], ROOTS["arcanist"])
    entry = s.chain(s.root, [(240, -6), (300, 0)], 4, "distillation", "aether_path")
    cu, r0, r1, turns = 490.0, 190.0, 80.0, 1.15
    pts = []
    for i in range(1, 49):
        t = i / 48
        ang = math.pi - t * 2 * math.pi * turns
        r = r0 - (r0 - r1) * t
        pts.append((cu + r * math.cos(ang), -r * math.sin(ang)))
    sp = s.chain(entry[-1], pts, 28, "warding_salts", "great_spiral", name="spiral")
    for i, slug in ((5, "sympathetic_link"), (9, "resonance")):
        outer = sp[i]
        inner = min(sp[i + 14:], key=lambda k: (g.pos(k)[0] - g.pos(outer)[0]) ** 2 + (g.pos(k)[1] - g.pos(outer)[1]) ** 2)
        spoke = s.chain(outer, [], 2, "clear_mind", "spokes", end=inner)
        s.mark(spoke[0], "notable", slug)
    spur_dir(s, sp[-1], 0, 0, "warding_salts", "keystone_concentrate", "volatile_concentrate",
             exclusive_group="arcanist_formula")
    b = s.chain(s.root, [(232, 66), (320, 150), cp("arcanist", +1)], 7, "restorative_brew", "east_still")
    c = s.chain(s.root, [(232, -60), (300, -130), cp("arcanist", -1)], 7, "distillation", "west_still")
    s.named["inner+"], s.named["inner-"] = b[-1], c[-1]
    s.mark(b[3], "notable", "herbalism")
    s.mark(c[3], "notable", "distillers_patience")
    f1 = s.ring(b[-1], (560, 245), 5, "restorative_brew", "east_flask")
    s.mark(f1[2], "notable", "panacea")
    ward = s.chain(c[-1], [(440, -222), (520, -264), cp("arcanist", -1, "outer")], 7, "warding_salts", "warded_path")
    s.named["outer-"] = ward[-1]
    s.mark(ward[3], "notable", "warded_path")
    f2 = s.ring(ward[-1], (700, -250), 5, "distillation", "west_flask")
    s.mark(f2[2], "notable", "tincture_mastery")
    spur_dir(s, f1[3], 10, 1, "restorative_brew", "keystone_elixir", "elixir_of_ages", exclusive_group="arcanist_formula")
    spur_dir(s, sp[14], 0, 1, "clear_mind", "keystone_ward", "arcane_ward")
    rim = min(sp[:24], key=lambda k: (local_of(s, k)[0] - 640) ** 2 + (local_of(s, k)[1] - 130) ** 2)
    ath = s.ring(rim, (720, 170), 5, "clear_mind", "athanor")
    s.chain(nearest(s, ath, f1[1]), [], 2, "restorative_brew", "athanor", end=nearest(s, f1, ath[0]))
    for i, slug in ((2, "steady_hand"), (8, "catalyst"), (13, "transmutation"), (19, "quintessence"), (24, "philosophers_eye")):
        s.mark(sp[i], "notable", slug)
    return s


def marksman(g):
    """Bow and arrow: a straight shaft, a drawn bow arc and two bowstrings forming long loops."""
    s = Sector(g, "marksman", ANGLES["marksman"], ROOTS["marksman"])
    a = s.chain(s.root, [(480, 0), (770, 0)], 16, "fletching", "arrow_shaft", name="A")
    nock = a[10]
    nu = 180 + (770 - 180) / 16 * 11
    bp = s.chain(nock, arc(290, [8, 16, 24, 30, 36], nu - 290), 5, "steady_draw", "bow_limb")
    bm = s.chain(nock, arc(290, [-8, -16, -24, -30, -36], nu - 290), 5, "steady_draw", "bow_limb")
    sp_ = s.chain(s.root, [(250, 66), (380, 130)], 8, "hunters_stride", "bowstring", end=bp[-1])
    sm_ = s.chain(s.root, [(250, -66), (380, -130)], 8, "bolt_smith", "bowstring", end=bm[-1])
    s.named["inner+"] = s.chain(sp_[5], [cp("marksman", +1)], 2, "hunters_stride", "flank")[-1]
    s.named["inner-"] = s.chain(sm_[6], [cp("marksman", -1)], 2, "bolt_smith", "flank")[-1]
    s.named["outer+"] = s.chain(bp[-1], [(590, 250), cp("marksman", +1, "outer")], 4, "steady_draw", "outer_flank")[-1]
    spur_dir(s, a[-1], 0, 0, "fletching", "keystone_patient", "patient_aim", exclusive_group="marksman_discipline")
    spur_dir(s, a[-1], 140, 0, "fletching", "arrowhead", "barbed_tip", ntype="minor")
    spur_dir(s, a[-1], -140, 0, "fletching", "arrowhead", "barbed_tip2", ntype="minor")
    q = s.ring(a[13], (690, -110), 5, "draw_weight", "quiver")
    s.mark(q[2], "notable", "full_quiver")
    spur_dir(s, q[3], -40, 1, "draw_weight", "keystone_volley", "volley_doctrine", exclusive_group="marksman_discipline")
    spur_dir(s, bm[-1], -60, 1, "bolt_smith", "keystone_bolt", "heavy_bolts")
    for k, slug in ((nock, "nocking_point"), (bp[2], "upper_limb"), (bm[2], "lower_limb"), (sp_[3], "taut_string"),
                    (sm_[3], "crossbow_drill"), (a[4], "eagle_eye"), (a[7], "long_shot")):
        s.mark(k, "notable", slug)
    return s


def artificer(g):
    """Mine workings: orthogonal shafts and galleries with ore-vein combs (sharp right-angle routes)."""
    s = Sector(g, "artificer", ANGLES["artificer"], ROOTS["artificer"])
    a = s.chain(s.root, [(420, 0)], 7, "prospector", "main_shaft", smooth=False, name="A")
    g1p = s.chain(a[-1], [(420, 140)], 4, "careful_hands", "upper_gallery", smooth=False)
    g1m = s.chain(a[-1], [(420, -110)], 3, "careful_hands", "lower_gallery", smooth=False)
    a2 = s.chain(a[-1], [(640, 0)], 6, "prospector", "deep_shaft", smooth=False)
    g2p = s.chain(a2[-1], [(640, 170)], 5, "pathfinder", "deep_gallery_east", smooth=False)
    g2m = s.chain(a2[-1], [(640, -200)], 6, "sure_footing", "deep_gallery_west", smooth=False)
    s.named["inner+"] = s.chain(g1p[-1], [(420, 179), cp("artificer", +1)], 2, "pathfinder", "winze", smooth=False)[-1]
    s.named["inner-"] = s.chain(g1m[-1], [(420, -198), cp("artificer", -1)], 3, "sure_footing", "winze", smooth=False)[-1]
    s.named["outer-"] = s.chain(g2m[-1], [cp("artificer", -1, "outer")], 3, "sure_footing", "far_winze", smooth=False)[-1]
    for k in (g2p[1], g2p[3]):
        spur_dir(s, k, 0, 0, "prospector", "ore_vein", "vein", ntype="minor")
    for k in (g1m[1], g2m[2], g2m[4]):
        spur_dir(s, k, 180, 0, "careful_hands", "ore_vein", "vein", ntype="minor")
    camp = s.ring(g2p[-1], (720, 210), 4, "pathfinder", "prospectors_camp")
    s.mark(camp[2], "notable", "prospectors_camp")
    tip = s.chain(a2[-1], [(740, 0)], 2, "prospector", "deep_shaft", smooth=False)
    spur_dir(s, tip[-1], 0, 0, "prospector", "keystone_excavator", "master_excavator", exclusive_group="artificer_craft")
    spur_dir(s, g2m[-1], 0, 1, "careful_hands", "keystone_tinker", "tinkers_bargain", exclusive_group="artificer_craft")
    spur_dir(s, camp[3], 30, 0, "pathfinder", "keystone_wayfinder", "wayfinder")
    s.chain(s.root, [(230, 70), (300, 120)], 3, "lung_capacity", "air_shaft", smooth=False)
    s.chain(s.root, [(240, -60), (330, -60)], 2, "long_reach", "tool_rack", smooth=False, end=g1m[1])
    for k, slug in ((a[3], "deep_breath"), (a[-1], "junction_lamp"), (g1p[2], "tool_care"), (a2[2], "rich_seams"),
                    (a2[-1], "foremans_post"), (g2p[2], "trailblazer"), (g2m[3], "reinforced_handles")):
        s.mark(k, "notable", slug)
    return s


def berserker(g):
    """Lightning: jagged zig-zag routes, rage stars and blood-pact triangles."""
    s = Sector(g, "berserker", ANGLES["berserker"], ROOTS["berserker"])
    zz = [(240, 44), (300, -44), (360, 44), (420, -44), (480, 44), (540, -44), (600, 44), (660, -40), (720, 0)]
    a = s.chain(s.root, zz, 18, "brute_force", "war_path", smooth=False, name="A")
    b = s.chain(s.root, [(205, 110), (286, 112), (330, 168), cp("berserker", +1)], 9, "thick_hide", "red_trail",
                smooth=False)
    c = s.chain(s.root, [(236, -76), (296, -100), cp("berserker", -1)], 7, "heavy_swing", "iron_trail", smooth=False)
    s.named["inner+"], s.named["inner-"] = b[-1], c[-1]
    w = s.chain(b[-1], [(470, 236), (520, 300), cp("berserker", +1, "outer")], 6, "momentum", "warband_road",
                smooth=False)
    s.named["outer+"] = w[-1]
    cl = s.chain(c[4], [(345, -84), (400, -130), (450, -170), (520, -230), (590, -250)], 9, "heavy_swing", "cleavers_line",
                 smooth=False)
    _, rl = star(s, a[5], (400, 140), [20, 100], "fury", "rage_star", "rising_rage")
    s.link(nearest(s, b, rl[1]), rl[1])
    _, bl = star(s, a[7], (470, -110), [-60, 30], "bloodthirst", "bloodletting_star", "bloodletting")
    star(s, a[13], (630, 150), [10, 80, 150], "fury", "frenzy_star", "frenzy")
    t1 = s.ring(w[2], (560, 210), 3, "bloodthirst", "blood_pact")
    s.mark(t1[2], "notable", "blood_pact")
    t2 = s.ring(cl[-1], (640, -280), 3, "thick_hide", "scar_pact")
    s.mark(t2[1], "notable", "scar_tissue")
    s.chain(t1[1], [], 3, "momentum", "bloodied_steps", end=a[9])
    s.link(bl[0], nearest(s, cl, bl[0]))
    spur_dir(s, a[-1], 0, 1, "brute_force", "keystone_fury", "reckless_fury", exclusive_group="berserker_oath")
    spur_dir(s, a[-1], -50, 1, "heavy_swing", "keystone_juggernaut", "juggernaut", exclusive_group="berserker_oath")
    spur_dir(s, t2[2], -20, 1, "fury", "keystone_deathwish", "death_wish")
    for k, slug in ((a[2], "battle_cry"), (a[9], "skull_splitter"), (a[16], "berserk"), (b[3], "scarred_hide"),
                    (c[2], "cleaving_blows"), (w[4], "warlords_march"), (cl[3], "heavy_handed")):
        s.mark(k, "notable", slug)
    return s


CORRIDORS = [
    # (sector, its node name, clockwise neighbour, neighbour node name, corridor id)
    ("marksman", "inner+", "artificer", "inner-", "corridor/marksman_artificer"),
    ("artificer", "inner+", "berserker", "inner-", "corridor/artificer_berserker"),
    ("berserker", "inner+", "duelist", "inner-", "corridor/berserker_duelist"),
    ("duelist", "inner+", "guardian", "inner-", "corridor/duelist_guardian"),
    ("guardian", "inner+", "arcanist", "inner-", "corridor/guardian_arcanist"),
    ("arcanist", "inner+", "marksman", "inner-", "corridor/arcanist_marksman"),
    ("marksman", "outer+", "artificer", "outer-", "corridor/far_reaches"),
    ("berserker", "outer+", "duelist", "outer-", "corridor/red_duel"),
    ("guardian", "outer+", "arcanist", "outer-", "corridor/warded_gate"),
]


def build():
    g = Graph()
    sectors = {}
    for fn in (marksman, artificer, berserker, duelist, guardian, arcanist):
        sec = fn(g)
        sectors[sec.name] = sec
    for sa, na, sb, nb, cid in CORRIDORS:
        g.add_edge(sectors[sa].named[na], sectors[sb].named[nb], [], "corridor", corridor=cid)
        g.corridors.append(cid)
    return g
