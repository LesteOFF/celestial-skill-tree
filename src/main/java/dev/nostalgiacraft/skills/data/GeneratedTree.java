package dev.nostalgiacraft.skills.data;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.generation.Catalog;
import dev.nostalgiacraft.skills.generation.CatalogParser;
import dev.nostalgiacraft.skills.generation.TreeDraw;
import dev.nostalgiacraft.skills.generation.TreeGenerator;
import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.ValidationReport;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GeneratedTree {
    private record Pending(CatalogParser.Outcome catalog, List<String> readErrors, Map<String, String> migrations) {
    }

    private static volatile Pending pending;

    private GeneratedTree() {
    }

    static void accept(CatalogParser.Outcome catalog, List<String> readErrors, Map<String, String> migrations) {
        pending = new Pending(catalog, List.copyOf(readErrors), Map.copyOf(migrations));
    }

    public static void generateFor(MinecraftServer server) {
        Pending current = pending;
        if (current == null || server.overworld() == null) {
            return;
        }
        GraphParser.Outcome outcome = current.catalog().catalog().map(catalog -> generate(catalog, current.catalog().report(), server))
                .orElseGet(() -> new GraphParser.Outcome(Optional.empty(), current.catalog().report()));
        SkillTreeManager.LoadResult result = SkillTreeManager.install(outcome, current.readErrors(), current.migrations());
        outcome.report().errors().forEach(issue -> NostalgiaSkills.LOGGER.error("[skill tree] {}", issue));
        outcome.report().warnings().forEach(issue -> NostalgiaSkills.LOGGER.warn("[skill tree] {}", issue));
        SkillTreeReloadListener.logResult(result);
    }

    public static void regenerateFor(MinecraftServer server) {
        WorldSkillData.of(server).setTreeDraw(TreeDraw.EMPTY);
        generateFor(server);
    }

    private static GraphParser.Outcome generate(Catalog catalog, ValidationReport catalogReport, MinecraftServer server) {
        WorldSkillData data = WorldSkillData.of(server);
        long seed = server.getWorldData().worldGenOptions().seed();
        TreeGenerator.Result result = TreeGenerator.generate(SkillTreeManager.TREE_ID, catalog, seed, data.treeDraw());
        ValidationReport report = new ValidationReport();
        report.addAll(catalogReport);
        report.addAll(result.report());
        if (result.graph().isPresent()) {
            data.setTreeDraw(result.draw());
        }
        Optional<SkillGraphSnapshot> priced = result.graph()
                .map(graph -> SkillTreeManager.withCostOverrides(graph, SkillTreeReloadListener.costOverrides()));
        return new GraphParser.Outcome(priced, report);
    }
}
