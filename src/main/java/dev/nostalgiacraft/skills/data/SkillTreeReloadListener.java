package dev.nostalgiacraft.skills.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.NodeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads data/<ns>/skill_trees, skill_nodes, skill_connections and skill_migrations on server start and /reload.
 * Files are read off-thread; parsing, validation and the snapshot swap happen in apply (game thread).
 * Higher-priority datapacks override a definition by providing the same resource path.
 */
public final class SkillTreeReloadListener extends SimplePreparableReloadListener<SkillTreeReloadListener.Prepared> {
    public record Prepared(GraphParser.Sources sources, Map<String, String> migrations, List<String> errors) {
    }

    @Override
    protected Prepared prepare(ResourceManager rm, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        Map<String, JsonElement> trees = read(rm, "skill_trees", errors);
        Map<String, JsonElement> nodes = read(rm, "skill_nodes", errors);
        Map<String, JsonElement> conns = read(rm, "skill_connections", errors);
        Map<String, String> migrations = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : read(rm, "skill_migrations", errors).entrySet()) {
            try {
                JsonObject m = e.getValue().getAsJsonObject().getAsJsonObject("migrations");
                for (Map.Entry<String, JsonElement> kv : m.entrySet()) {
                    String to = kv.getValue().getAsString();
                    if (!Ids.isValid(kv.getKey()) || !Ids.isValid(to)) {
                        errors.add("skill_migrations/" + e.getKey() + ": invalid id in '" + kv.getKey() + "' -> '" + to + "'");
                    } else {
                        migrations.put(kv.getKey(), to);
                    }
                }
            } catch (RuntimeException ex) {
                errors.add("skill_migrations/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        return new Prepared(new GraphParser.Sources(trees, nodes, conns), migrations, errors);
    }

    private static Map<String, JsonElement> read(ResourceManager rm, String dir, List<String> errors) {
        Map<String, JsonElement> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources(dir, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation rl = e.getKey();
            String path = rl.getPath().substring(dir.length() + 1, rl.getPath().length() - ".json".length());
            String id = rl.getNamespace() + ":" + path;
            try (Reader reader = e.getValue().openAsReader()) {
                out.put(id, JsonParser.parseReader(reader));
            } catch (Exception ex) {
                errors.add(dir + "/" + id + " (pack " + e.getValue().sourcePackId() + "): unreadable JSON: " + ex.getMessage());
            }
        }
        return out;
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager rm, ProfilerFiller profiler) {
        GraphParser parser = new GraphParser(McKnownIds.INSTANCE);
        GraphParser.Outcome outcome = parser.parse(SkillTreeManager.TREE_ID, prepared.sources());
        if (outcome.snapshot().isPresent()) {
            outcome = new GraphParser.Outcome(outcome.snapshot().map(g ->
                    SkillTreeManager.withCostOverrides(g, costOverrides())), outcome.report());
        }
        SkillTreeManager.LoadResult result = SkillTreeManager.install(outcome, prepared.errors(), prepared.migrations(), () -> {
            try {
                GraphParser.Outcome fb = parser.parse(SkillTreeManager.TREE_ID, BundledTree.load());
                return new GraphParser.Outcome(fb.snapshot().map(g -> SkillTreeManager.withCostOverrides(g, costOverrides())), fb.report());
            } catch (Exception ex) {
                GraphParser.Outcome failed = new GraphParser.Outcome(java.util.Optional.empty(), new dev.nostalgiacraft.skills.graph.ValidationReport());
                failed.report().error("bundled", "", "could not read bundled tree: " + ex.getMessage());
                return failed;
            }
        });
        prepared.errors().forEach(e -> NostalgiaSkills.LOGGER.error("[skill tree] {}", e));
        outcome.report().errors().forEach(i -> NostalgiaSkills.LOGGER.error("[skill tree] {}", i));
        outcome.report().warnings().forEach(i -> NostalgiaSkills.LOGGER.warn("[skill tree] {}", i));
        switch (result) {
            case APPLIED -> NostalgiaSkills.LOGGER.info("Skill tree loaded: {} nodes, revision {}",
                    SkillTreeManager.graph().map(g -> g.nodes().size()).orElse(0), SkillTreeManager.revision());
            case KEPT_PREVIOUS -> NostalgiaSkills.LOGGER.error("Skill tree reload rejected; keeping the last valid tree (revision {})",
                    SkillTreeManager.revision());
            case FALLBACK_BUNDLED -> NostalgiaSkills.LOGGER.error("Datapack skill tree invalid on initial load; using the verified bundled tree");
            case FAILED -> NostalgiaSkills.LOGGER.error("No valid skill tree available; skill features are disabled until a valid tree loads");
        }
    }

    private static Map<NodeType, Integer> costOverrides() {
        Map<NodeType, Integer> m = new EnumMap<>(NodeType.class);
        try {
            m.put(NodeType.MINOR, ServerConfig.COST_MINOR.get());
            m.put(NodeType.NOTABLE, ServerConfig.COST_NOTABLE.get());
            m.put(NodeType.KEYSTONE, ServerConfig.COST_KEYSTONE.get());
            m.put(NodeType.ROOT, ServerConfig.COST_ROOT.get());
        } catch (IllegalStateException notLoaded) {
            // server config not loaded yet (should not happen during reload): keep data costs
        }
        return m;
    }
}
