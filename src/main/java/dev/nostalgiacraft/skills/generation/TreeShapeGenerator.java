package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.generation.TreeShape.Link;
import dev.nostalgiacraft.skills.generation.TreeShape.LinkKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TreeShapeGenerator {
    public static final int MAX_KEYSTONES = 9;
    private static final int FIRST_ROW_KEYSTONES = 3;
    private static final double SECOND_ROW_KEYSTONE_DISTANCE = 250;
    private static final double[] SECOND_ROW_KEYSTONE_DEGREES = {-20, 20, -60, 60, -80, 80};
    private static final int HUB_SLOTS = 1;
    private static final int CROWN_STEM_SLOTS = 1;
    private static final double KEYSTONE_DISTANCE = 130;
    private static final double KEYSTONE_SPREAD_DEGREES = 40;
    private static final double PORTAL_ARC_OFFSET = 110;
    private static final double PORTAL_TRUNK_OFFSET = 1.5;

    private record RingPlan(int ring, int slotsPerSide, boolean reachesBoundary) {
    }

    record PortalPair(int first, int second) {
        boolean joins(int classIndex) {
            return first == classIndex || second == classIndex;
        }
    }

    private TreeShapeGenerator() {
    }

    public static TreeShape generate(ShapeRequest request) {
        Wheel wheel = new Wheel(request.classes().size());
        List<ClassShape> classes = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        List<PortalPair> portalPairs = request.portals() ? portalPairs(wheel.classCount()) : List.of();
        for (int index = 0; index < request.classes().size(); index++) {
            int classIndex = index;
            int portals = (int) portalPairs.stream().filter(pair -> pair.joins(classIndex)).count();
            classes.add(buildClass(wheel, index, request.classes().get(index), portals, links));
        }
        linkNeighbours(classes, links);
        linkPortals(classes, portalPairs, links);
        return new TreeShape(List.copyOf(classes), List.copyOf(links));
    }

    static List<PortalPair> portalPairs(int classCount) {
        int half = classCount / 2;
        List<PortalPair> pairs = new ArrayList<>();
        for (int index = 0; index < half; index++) {
            pairs.add(new PortalPair(index, index + half));
        }
        boolean oneClassLeftOver = classCount > 1 && classCount % 2 == 1;
        if (oneClassLeftOver) {
            pairs.add(new PortalPair(classCount - 1, half));
        }
        return pairs;
    }

    private static List<RingPlan> planRings(Wheel wheel, ShapeRequest.ClassBudget budget) {
        int remaining = budget.skills() - HUB_SLOTS - CROWN_STEM_SLOTS - keystoneCount(budget);
        List<RingPlan> rings = new ArrayList<>();
        for (int ring = 1; ; ring++) {
            int capacity = wheel.ribCapacity(ring);
            int slotsPerSide = Math.min(capacity, (remaining - Wheel.TRUNK_SLOTS_PER_RING) / 2);
            if (slotsPerSide < Wheel.MIN_RIB_SLOTS) {
                return rings;
            }
            rings.add(new RingPlan(ring, slotsPerSide, slotsPerSide == capacity));
            remaining -= Wheel.TRUNK_SLOTS_PER_RING + 2 * slotsPerSide;
        }
    }

    private static int keystoneCount(ShapeRequest.ClassBudget budget) {
        return Math.max(0, Math.min(MAX_KEYSTONES, budget.keystones()));
    }

    private static ClassShape buildClass(Wheel wheel, int index, ShapeRequest.ClassBudget budget, int portalCount,
                                         List<Link> links) {
        List<RingPlan> rings = planRings(wheel, budget);
        Slot hub = new Slot(index + "/hub", SlotKind.HUB, wheel.point(index, wheel.hubRadius(), 0));
        List<Slot> trunk = buildTrunk(wheel, index, hub, rings.size(), links);
        List<Rib> ribs = new ArrayList<>();
        for (RingPlan plan : rings) {
            Slot anchor = trunk.get(plan.ring() * Wheel.TRUNK_SLOTS_PER_RING - 1);
            for (Rib.Side side : Rib.Side.values()) {
                ribs.add(buildRib(wheel, index, plan, side, anchor, links));
            }
        }
        List<Slot> keystones = buildKeystones(wheel, index, trunk.get(trunk.size() - 1), keystoneCount(budget), links);
        List<Slot> portals = new ArrayList<>();
        for (int number = 0; number < portalCount; number++) {
            portals.add(buildPortal(wheel, index, trunk, rings.size(), number, links));
        }
        return new ClassShape(index, hub, List.copyOf(trunk), List.copyOf(ribs), List.copyOf(keystones), List.copyOf(portals));
    }

    private static List<Slot> buildTrunk(Wheel wheel, int index, Slot hub, int ringCount, List<Link> links) {
        List<Slot> trunk = new ArrayList<>();
        Slot previous = hub;
        int length = ringCount * Wheel.TRUNK_SLOTS_PER_RING + CROWN_STEM_SLOTS;
        for (int position = 1; position <= length; position++) {
            Slot slot = new Slot(index + "/trunk_" + position, SlotKind.TRUNK, wheel.point(index, wheel.trunkRadius(position), 0));
            links.add(new Link(previous.key(), slot.key(), LinkKind.PATH));
            trunk.add(slot);
            previous = slot;
        }
        return trunk;
    }

    private static Rib buildRib(Wheel wheel, int index, RingPlan plan, Rib.Side side, Slot anchor, List<Link> links) {
        List<Slot> slots = new ArrayList<>();
        Slot previous = anchor;
        for (int position = 1; position <= plan.slotsPerSide(); position++) {
            SlotKind kind = position == plan.slotsPerSide() ? SlotKind.RIB_END : SlotKind.RIB;
            String key = index + "/rib_" + plan.ring() + side.label() + "_" + position;
            double arc = side.direction() * position * Wheel.RIB_STEP;
            Slot slot = new Slot(key, kind, wheel.point(index, wheel.ringRadius(plan.ring()), arc));
            links.add(new Link(previous.key(), slot.key(), LinkKind.PATH));
            slots.add(slot);
            previous = slot;
        }
        return new Rib(plan.ring(), side, List.copyOf(slots), plan.reachesBoundary());
    }

    private static List<Slot> buildKeystones(Wheel wheel, int index, Slot stem, int count, List<Link> links) {
        List<Slot> keystones = new ArrayList<>();
        int firstRow = Math.min(count, FIRST_ROW_KEYSTONES);
        for (int position = 0; position < count; position++) {
            boolean inFirstRow = position < firstRow;
            double degrees = inFirstRow ? (position - (firstRow - 1) / 2.0) * KEYSTONE_SPREAD_DEGREES
                    : SECOND_ROW_KEYSTONE_DEGREES[position - firstRow];
            double distance = inFirstRow ? KEYSTONE_DISTANCE : SECOND_ROW_KEYSTONE_DISTANCE;
            Slot slot = new Slot(index + "/keystone_" + (position + 1), SlotKind.KEYSTONE,
                    wheel.fanned(stem.position(), index, distance, degrees));
            links.add(new Link(stem.key(), slot.key(), LinkKind.PATH));
            keystones.add(slot);
        }
        return keystones;
    }

    private static Slot buildPortal(Wheel wheel, int index, List<Slot> trunk, int ringCount, int number, List<Link> links) {
        int trunkSlotsBefore = ringCount / 2 * Wheel.TRUNK_SLOTS_PER_RING;
        double radius = wheel.trunkRadius(0) + (trunkSlotsBefore + PORTAL_TRUNK_OFFSET) * Wheel.TRUNK_STEP;
        Rib.Side side = number == 0 ? Rib.Side.CLOCKWISE : Rib.Side.COUNTER_CLOCKWISE;
        String key = index + "/portal" + (number == 0 ? "" : "_" + (number + 1));
        Slot portal = new Slot(key, SlotKind.PORTAL, wheel.point(index, radius, side.direction() * PORTAL_ARC_OFFSET));
        links.add(new Link(trunk.get(trunkSlotsBefore).key(), portal.key(), LinkKind.PATH));
        return portal;
    }

    private static void linkNeighbours(List<ClassShape> classes, List<Link> links) {
        for (int index = 0; index < classes.size(); index++) {
            ClassShape current = classes.get(index);
            ClassShape next = classes.get((index + 1) % classes.size());
            boolean alone = current == next;
            boolean secondPassOverTheSamePair = classes.size() == 2 && index == 1;
            boolean bridged = linkInnermostFacingRibs(current, next, alone ? LinkKind.PATH : LinkKind.BRIDGE, true, links);
            if (!bridged && !alone && !secondPassOverTheSamePair) {
                linkWithoutSharedBoundary(current, next, links);
            }
        }
    }

    private static boolean linkInnermostFacingRibs(ClassShape current, ClassShape next, LinkKind kind, boolean onlyAtBoundary,
                                                   List<Link> links) {
        for (Rib rib : current.ribs()) {
            Optional<Rib> facing = next.rib(rib.ring(), Rib.Side.COUNTER_CLOCKWISE);
            boolean meets = rib.side() == Rib.Side.CLOCKWISE && facing.isPresent()
                    && (!onlyAtBoundary || (rib.reachesBoundary() && facing.get().reachesBoundary()));
            if (meets) {
                links.add(new Link(rib.tip().key(), facing.get().tip().key(), kind));
                return true;
            }
        }
        return false;
    }

    private static void linkWithoutSharedBoundary(ClassShape current, ClassShape next, List<Link> links) {
        if (!linkInnermostFacingRibs(current, next, LinkKind.BRIDGE, false, links)) {
            links.add(new Link(current.hub().key(), next.hub().key(), LinkKind.BRIDGE));
        }
    }

    private static void linkPortals(List<ClassShape> classes, List<PortalPair> pairs, List<Link> links) {
        int[] used = new int[classes.size()];
        for (PortalPair pair : pairs) {
            Slot first = classes.get(pair.first()).portals().get(used[pair.first()]++);
            Slot second = classes.get(pair.second()).portals().get(used[pair.second()]++);
            links.add(new Link(first.key(), second.key(), LinkKind.PORTAL));
        }
    }
}
