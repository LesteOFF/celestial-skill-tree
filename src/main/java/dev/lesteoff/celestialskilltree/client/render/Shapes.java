package dev.lesteoff.celestialskilltree.client.render;

public final class Shapes {
    private static final int RING_SEGMENTS = 64;

    private Shapes() {
    }

    public static void ring(LineBatch batch, double centerX, double centerY, double radius, float width, int argb) {
        arc(batch, centerX, centerY, radius, 0, 2 * Math.PI, width, argb);
    }

    public static void arc(LineBatch batch, double centerX, double centerY, double radius, double fromAngle, double toAngle,
                           float width, int argb) {
        int segments = Math.max(2, (int) Math.ceil(RING_SEGMENTS * Math.abs(toAngle - fromAngle) / (2 * Math.PI)));
        for (int i = 0; i < segments; i++) {
            double start = fromAngle + (toAngle - fromAngle) * i / segments;
            double end = fromAngle + (toAngle - fromAngle) * (i + 1) / segments;
            batch.segment(centerX + Math.cos(start) * radius, centerY + Math.sin(start) * radius,
                    centerX + Math.cos(end) * radius, centerY + Math.sin(end) * radius, width, argb);
        }
    }

    public static void spoke(LineBatch batch, double centerX, double centerY, double angle, double fromRadius, double toRadius,
                             float width, int argb) {
        batch.segment(centerX + Math.cos(angle) * fromRadius, centerY + Math.sin(angle) * fromRadius,
                centerX + Math.cos(angle) * toRadius, centerY + Math.sin(angle) * toRadius, width, argb);
    }

    public static void sparkle(LineBatch batch, double centerX, double centerY, double size, int argb) {
        float core = (float) Math.max(1, size * 0.35);
        batch.segment(centerX - size, centerY, centerX + size, centerY, core, argb);
        batch.segment(centerX, centerY - size, centerX, centerY + size, core, argb);
        batch.rect((float) (centerX - core), (float) (centerY - core), (float) (centerX + core), (float) (centerY + core), argb);
    }

    public static int withAlpha(int argb, double alpha) {
        int clamped = (int) Math.round(255 * Math.max(0, Math.min(1, alpha)));
        return (clamped << 24) | (argb & 0xFFFFFF);
    }
}
