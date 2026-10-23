package dev.lesteoff.celestialskilltree.data;

import dev.lesteoff.celestialskilltree.graph.CommunityTier;
import dev.lesteoff.celestialskilltree.graph.GraphParser;
import dev.lesteoff.celestialskilltree.graph.NodeType;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class SkillTreeManager {
    public static final String TREE_ID = "celestialskilltree:main";
    private static final int NO_OVERRIDE = -1;

    public enum LoadResult { APPLIED, KEPT_PREVIOUS, FAILED }

    private static volatile SkillGraphSnapshot active;
    private static volatile int revision;
    private static volatile ValidationReport lastReport = new ValidationReport();
    private static volatile Map<String, String> migrations = Map.of();
    private static volatile LoadResult lastResult = LoadResult.FAILED;
    private static volatile List<String> readErrors = List.of();
    private static volatile Map<String, SkillSign> signs = Map.of();
    private static volatile List<CommunityTier> communityTiers = List.of();

    private SkillTreeManager() {
    }

    public static Optional<SkillGraphSnapshot> graph() {
        return Optional.ofNullable(active);
    }

    public static int revision() {
        return revision;
    }

    public static ValidationReport lastReport() {
        return lastReport;
    }

    public static List<String> readErrors() {
        return readErrors;
    }

    public static LoadResult lastResult() {
        return lastResult;
    }

    public static List<CommunityTier> communityTiers() {
        return communityTiers;
    }

    static void installCommunityTiers(List<CommunityTier> loaded) {
        communityTiers = List.copyOf(loaded);
    }

    public static Optional<SkillSign> sign(String signId) {
        return Optional.ofNullable(signs.get(signId));
    }

    public static List<String> sortedSignIds() {
        return signs.keySet().stream().sorted().toList();
    }

    static void installSigns(List<SkillSign> loaded) {
        Map<String, SkillSign> byId = new TreeMap<>();
        loaded.forEach(sign -> byId.put(sign.id(), sign));
        signs = Map.copyOf(byId);
    }

    public static Map<String, String> migrations() {
        return migrations;
    }

    static synchronized LoadResult install(GraphParser.Outcome outcome, List<String> fileErrors,
                                           Map<String, String> newMigrations) {
        readErrors = List.copyOf(fileErrors);
        lastReport = outcome.report();
        if (outcome.snapshot().isPresent() && fileErrors.isEmpty()) {
            activate(outcome.snapshot().get(), newMigrations);
            lastResult = LoadResult.APPLIED;
        } else {
            lastResult = active != null ? LoadResult.KEPT_PREVIOUS : LoadResult.FAILED;
        }
        return lastResult;
    }

    private static void activate(SkillGraphSnapshot graph, Map<String, String> newMigrations) {
        set(graph);
        migrations = Map.copyOf(newMigrations);
    }

    private static void set(SkillGraphSnapshot graph) {
        if (active == null || !active.fingerprint().equals(graph.fingerprint())) {
            revision++;
        }
        active = graph;
    }

    public static SkillGraphSnapshot withCostOverrides(SkillGraphSnapshot graph, Map<NodeType, Integer> overrides) {
        if (overrides.values().stream().allMatch(cost -> cost < 0)) {
            return graph;
        }
        List<SkillNodeDefinition> nodes = graph.nodes().values().stream()
                .map(node -> withCost(node, overrides.getOrDefault(node.type(), NO_OVERRIDE))).toList();
        return new SkillGraphSnapshot(graph.treeId(), nodes, graph.connections(), graph.sectors(),
                List.copyOf(graph.exclusiveGroups().values()));
    }

    private static SkillNodeDefinition withCost(SkillNodeDefinition node, int cost) {
        return cost < 0 || cost == node.cost() ? node : node.withCost(cost);
    }

    public static synchronized void reset() {
        active = null;
        lastReport = new ValidationReport();
        lastResult = LoadResult.FAILED;
    }
}
