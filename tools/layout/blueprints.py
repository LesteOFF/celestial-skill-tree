"""Hand-authored sector blueprints. Each sector uses its own route vocabulary so the six
sectors are recognisable by shape alone (they are NOT rotated copies of one template).

Local frame per sector: u = outward along the sector axis, v = lateral (+v is clockwise on
screen, i.e. towards the neighbour whose axis angle is 60 degrees larger).
"""
from builder import Graph, Sector, diamond, nearest, spur, spur_dir, star

ROOTS = {  # authored tree-space coordinates (spec section 4.2)
    "duelist": (-180, 0), "guardian": (-90, -156), "arcanist": (90, -156),
    "marksman": (180, 0), "artificer": (90, 156), "berserker": (-90, 156),
}
ANGLES = {"marksman": 0, "artificer": 60, "berserker": 120, "duelist": 180, "guardian": 240, "arcanist": 300}


def duelist(g):
    """Rapier lines: a long serpentine riposte line, thin lateral feints, small parry rings."""
    s = Sector(g, "duelist", ANGLES["duelist"], ROOTS["duelist"])
    a = s.chain(s.root, [(260, -14), (340, 14), (420, -14), (500, 14), (580, -10), (650, 6), (700, 0)], 16,
                "quick_hands", "riposte_line", name="A")
    b = s.chain(s.root, [(228, 46), (290, 104), (352, 156), (405, 196)], 9, "light_step", "feint_arc", name="B")
    c = s.chain(s.root, [(222, -56), (290, -104), (348, -150), (400, -188)], 8, "fencers_poise", "low_guard",
                name="C")
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
    ss = s.chain(tip, [(560, -236), (610, -258), (668, -262)], 3, "nimble", "swordsong")
    s.mark(s.chain(ss[-1], [(712, -246)], 1, "cutting_remark", "swordsong")[0], "notable", "riposte_rhythm")
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


def build():
    g = Graph()
    duelist(g)
    return g
