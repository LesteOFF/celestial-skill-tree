package dev.nostalgiacraft.skills.graph;

/** A point in tree space (+X right, +Y down, logical units). */
public record Vec2(double x, double y) {
    public double distanceTo(Vec2 o) {
        return Math.hypot(x - o.x, y - o.y);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y);
    }
}
