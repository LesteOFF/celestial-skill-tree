package dev.nostalgiacraft.skills.data;

import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.graph.ValidationReport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the server's active, immutable graph snapshot. A new snapshot replaces the old one only after it fully
 * validated; otherwise the last valid snapshot stays active for the session and the errors are kept for
 * {@code /nostalgiaskills validate}.
 */
public final class SkillTreeManager {
    public static final String TREE_ID = "nostalgiaskills:main";

    public enum LoadResult { APPLIED, KEPT_PREVIOUS, FALLBACK_BUNDLED, FAILED }

    private static volatile SkillGraphSnapshot active;
    private static volatile int revision;
    private static volatile ValidationReport lastReport = new ValidationReport();
    private static volatile Map<String, String> migrations = Map.of();
    private static volatile LoadResult lastResult = LoadResult.FAILED;
    private static volatile List<String> readErrors = List.of();

    private SkillTreeManager() {
    }

    public static Optional<SkillGraphSnapshot> graph() {
        return Optional.ofNullable(active);
    }

    /** Increments on every successful replacement during this server session. */
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

    public static Map<String, String> migrations() {
        return migrations;
    }

    static synchronized LoadResult install(GraphParser.Outcome outcome, List<String> fileErrors,
                                           Map<String, String> newMigrations,
                                           java.util.function.Supplier<GraphParser.Outcome> bundled) {
        readErrors = List.copyOf(fileErrors);
        lastReport = outcome.report();
        if (outcome.snapshot().isPresent() && fileErrors.isEmpty()) {
            set(outcome.snapshot().get());
            migrations = Map.copyOf(newMigrations);
            lastResult = LoadResult.APPLIED;
        } else if (active != null) {
            lastResult = LoadResult.KEPT_PREVIOUS;
        } else {
            GraphParser.Outcome fb = bundled.get();
            if (fb.snapshot().isPresent()) {
                set(fb.snapshot().get());
                migrations = Map.copyOf(newMigrations);
                lastResult = LoadResult.FALLBACK_BUNDLED;
            } else {
                lastReport.addAll(fb.report());
                lastResult = LoadResult.FAILED;
            }
        }
        return lastResult;
    }

    private static void set(SkillGraphSnapshot g) {
        if (active == null || !active.fingerprint().equals(g.fingerprint())) {
            revision++;
        }
        active = g;
    }

    /** Applies the server's per-type cost settings (-1 = keep data cost). */
    public static SkillGraphSnapshot withCostOverrides(SkillGraphSnapshot g, Map<NodeType, Integer> overrides) {
        if (overrides.values().stream().allMatch(v -> v < 0)) {
            return g;
        }
        List<SkillNodeDefinition> nodes = g.nodes().values().stream().map(n -> {
            Integer o = overrides.get(n.type());
            if (o == null || o < 0 || o == n.cost()) {
                return n;
            }
            return new SkillNodeDefinition(n.id(), n.tree(), n.sector(), n.type(), n.position(), n.nameKey(),
                    n.descriptionKey(), n.icon(), n.frame(), o, n.maxRank(), n.requirements(), n.effects(), n.tags(),
                    n.cluster(), n.exclusiveGroup());
        }).toList();
        return new SkillGraphSnapshot(g.treeId(), nodes, g.connections(), g.sectors(),
                List.copyOf(g.exclusiveGroups().values()));
    }

    /** Clears state between integrated-server sessions. */
    public static synchronized void reset() {
        active = null;
        lastReport = new ValidationReport();
        lastResult = LoadResult.FAILED;
    }
}
