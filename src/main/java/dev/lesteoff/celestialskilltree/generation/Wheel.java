package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.graph.Vec2;

record Wheel(int classCount) {
    static final double TRUNK_STEP = 90;
    static final double RIB_STEP = 80;
    static final int TRUNK_SLOTS_PER_RING = 3;
    static final int MIN_RIB_SLOTS = 2;
    private static final double MIN_HUB_RADIUS = 450;
    private static final double MIN_HUB_SPACING = 300;
    private static final double TOP = -Math.PI / 2;
    private static final double TIP_CLEARANCE_IN_STEPS = 0.5;
    private static final double COORDINATE_PRECISION = 10;

    double halfAngle() {
        return Math.PI / classCount;
    }

    double hubRadius() {
        if (classCount < 2) {
            return MIN_HUB_RADIUS;
        }
        double keepsHubsApart = MIN_HUB_SPACING / 2 / Math.sin(halfAngle());
        double fitsTheFirstRib = (MIN_RIB_SLOTS + TIP_CLEARANCE_IN_STEPS) * RIB_STEP / halfAngle() - ringDepth();
        return Math.max(MIN_HUB_RADIUS, Math.max(keepsHubsApart, fitsTheFirstRib));
    }

    double trunkRadius(int trunkIndex) {
        return hubRadius() + trunkIndex * TRUNK_STEP;
    }

    double ringRadius(int ring) {
        return hubRadius() + ring * ringDepth();
    }

    int ribCapacity(int ring) {
        return (int) Math.floor(ringRadius(ring) * halfAngle() / RIB_STEP - TIP_CLEARANCE_IN_STEPS);
    }

    Vec2 point(int classIndex, double radius, double arc) {
        double angle = axis(classIndex) + arc / radius;
        return rounded(radius * Math.cos(angle), radius * Math.sin(angle));
    }

    Vec2 fanned(Vec2 origin, int classIndex, double distance, double degreesFromAxis) {
        double angle = axis(classIndex) + Math.toRadians(degreesFromAxis);
        return rounded(origin.x() + distance * Math.cos(angle), origin.y() + distance * Math.sin(angle));
    }

    private double axis(int classIndex) {
        return TOP + classIndex * 2 * halfAngle();
    }

    private static double ringDepth() {
        return TRUNK_SLOTS_PER_RING * TRUNK_STEP;
    }

    private static Vec2 rounded(double x, double y) {
        return new Vec2(Math.round(x * COORDINATE_PRECISION) / COORDINATE_PRECISION,
                Math.round(y * COORDINATE_PRECISION) / COORDINATE_PRECISION);
    }
}
