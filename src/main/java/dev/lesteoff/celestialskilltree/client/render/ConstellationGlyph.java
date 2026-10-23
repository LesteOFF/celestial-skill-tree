package dev.lesteoff.celestialskilltree.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ConstellationGlyph {
    private static final int MIN_STARS = 5;
    private static final int EXTRA_STARS = 3;
    private static final double MIN_SPACING = 0.45;
    private static final int PLACEMENT_ATTEMPTS = 40;

    private record Star(double x, double y, double brightness) {
    }

    private final List<Star> stars;

    private ConstellationGlyph(List<Star> stars) {
        this.stars = stars;
    }

    public static ConstellationGlyph of(String seed) {
        Random random = new Random(seed.hashCode() * 31L + 7);
        List<Star> stars = new ArrayList<>();
        int count = MIN_STARS + random.nextInt(EXTRA_STARS);
        for (int attempt = 0; stars.size() < count && attempt < count * PLACEMENT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 0.25 + 0.75 * Math.sqrt(random.nextDouble());
            Star candidate = new Star(Math.cos(angle) * distance, Math.sin(angle) * distance, 0.6 + 0.4 * random.nextDouble());
            boolean crowded = stars.stream().anyMatch(star -> Math.hypot(star.x() - candidate.x(), star.y() - candidate.y()) < MIN_SPACING);
            if (!crowded) {
                stars.add(candidate);
            }
        }
        stars.sort((first, second) -> Double.compare(Math.atan2(first.y(), first.x()), Math.atan2(second.y(), second.x())));
        return new ConstellationGlyph(List.copyOf(stars));
    }

    public void draw(LineBatch batch, double centerX, double centerY, double scale, int lineColor, int starColor, double twinkle) {
        for (int i = 1; i < stars.size(); i++) {
            Star from = stars.get(i - 1);
            Star to = stars.get(i);
            batch.segment(centerX + from.x() * scale, centerY + from.y() * scale, centerX + to.x() * scale,
                    centerY + to.y() * scale, (float) Math.max(1, scale * 0.06), lineColor);
        }
        for (int i = 0; i < stars.size(); i++) {
            Star star = stars.get(i);
            double pulse = 0.85 + 0.15 * Math.sin(twinkle * 2.2 + i * 1.7);
            Shapes.sparkle(batch, centerX + star.x() * scale, centerY + star.y() * scale,
                    Math.max(1.5, scale * 0.16 * star.brightness() * pulse), starColor);
        }
    }
}
