package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Optional;

public record TreeRegion(double minX, double minY, double maxX, double maxY) {
    private static final double FADE_DISTANCE = 500;

    public static boolean isHidden(SkillNodeDefinition node) {
        return node.traits().hiddenUntil().isPresent();
    }

    public static Optional<TreeRegion> hidden(SkillGraphSnapshot graph) {
        return around(graph.nodes().values().stream().filter(TreeRegion::isHidden).toList());
    }

    public static Optional<TreeRegion> main(SkillGraphSnapshot graph) {
        return around(graph.nodes().values().stream().filter(node -> !isHidden(node)).toList());
    }

    private static Optional<TreeRegion> around(List<SkillNodeDefinition> nodes) {
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        DoubleSummaryStatistics x = nodes.stream().mapToDouble(node -> node.position().x()).summaryStatistics();
        DoubleSummaryStatistics y = nodes.stream().mapToDouble(node -> node.position().y()).summaryStatistics();
        return Optional.of(new TreeRegion(x.getMin(), y.getMin(), x.getMax(), y.getMax()));
    }

    public double presenceAt(double x, double y) {
        double outsideX = Math.max(0, Math.max(minX - x, x - maxX));
        double outsideY = Math.max(0, Math.max(minY - y, y - maxY));
        return Math.max(0, 1 - Math.hypot(outsideX, outsideY) / FADE_DISTANCE);
    }
}
