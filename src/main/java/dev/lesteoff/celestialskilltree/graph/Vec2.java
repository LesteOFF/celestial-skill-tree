package dev.lesteoff.celestialskilltree.graph;

public record Vec2(double x, double y) {
    public double distanceTo(Vec2 point) {
        return Math.hypot(x - point.x, y - point.y);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y);
    }
}
