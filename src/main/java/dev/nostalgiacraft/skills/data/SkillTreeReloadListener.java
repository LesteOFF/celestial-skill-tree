package dev.nostalgiacraft.skills.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.generation.CatalogEditor;
import dev.nostalgiacraft.skills.generation.CatalogParser;
import dev.nostalgiacraft.skills.generation.CatalogSources;
import dev.nostalgiacraft.skills.graph.CommunityParser;
import dev.nostalgiacraft.skills.graph.CommunityTier;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.ModRequirement;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SignParser;
import dev.nostalgiacraft.skills.graph.SkillSign;
import dev.nostalgiacraft.skills.graph.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class SkillTreeReloadListener extends SimplePreparableReloadListener<SkillTreeReloadListener.Prepared> {
    private static final String JSON_SUFFIX = ".json";
    private static final String RULES_ID = "nostalgiaskills:main";

    public record Prepared(Map<String, String> migrations, List<String> errors,
                           Map<String, JsonElement> signFiles, Map<String, JsonElement> communityFiles,
                           CatalogSources catalog) {
    }

    @Override
    protected Prepared prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        Map<String, String> migrations = new TreeMap<>();
        read(resourceManager, "skill_migrations", errors)
                .forEach((fileId, json) -> readMigrations("skill_migrations/" + fileId, json, migrations, errors));
        List<String> signReadErrors = new ArrayList<>();
        Map<String, JsonElement> signFiles = read(resourceManager, "skill_signs", signReadErrors);
        Map<String, JsonElement> communityFiles = read(resourceManager, "skill_community", signReadErrors);
        signReadErrors.forEach(error -> NostalgiaSkills.LOGGER.error("[skill signs] {}", error));
        return new Prepared(migrations, errors, signFiles, communityFiles,
                readCatalog(resourceManager, errors));
    }

    private static void readMigrations(String source, JsonElement file, Map<String, String> migrations, List<String> errors) {
        try {
            JsonObject entries = file.getAsJsonObject().getAsJsonObject("migrations");
            for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                String from = entry.getKey();
                String to = entry.getValue().getAsString();
                if (Ids.isValid(from) && Ids.isValid(to)) {
                    migrations.put(from, to);
                } else {
                    errors.add(source + ": invalid id in '" + from + "' -> '" + to + "'");
                }
            }
        } catch (RuntimeException unreadable) {
            errors.add(source + ": " + unreadable.getMessage());
        }
    }

    private static CatalogSources readCatalog(ResourceManager resourceManager, List<String> errors) {
        Predicate<String> anyMod = mod -> true;
        Optional<JsonElement> rules = Optional.ofNullable(read(resourceManager, "skill_rules", errors, anyMod).get(RULES_ID));
        return new CatalogSources(read(resourceManager, "skill_classes", errors, anyMod),
                read(resourceManager, "skill_themes", errors, anyMod), read(resourceManager, "skill_keystones", errors, anyMod), rules);
    }

    private static Map<String, JsonElement> read(ResourceManager resourceManager, String directory, List<String> errors) {
        return read(resourceManager, directory, errors, ModList.get()::isLoaded);
    }

    private static Map<String, JsonElement> read(ResourceManager resourceManager, String directory, List<String> errors,
                                                 Predicate<String> isModLoaded) {
        Map<String, JsonElement> files = new HashMap<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(directory,
                location -> location.getPath().endsWith(JSON_SUFFIX));
        resources.forEach((location, resource) -> {
            String path = location.getPath();
            String id = location.getNamespace() + ":" + path.substring(directory.length() + 1, path.length() - JSON_SUFFIX.length());
            try (Reader reader = resource.openAsReader()) {
                JsonElement file = JsonParser.parseReader(reader);
                if (ModRequirement.isMet(file, isModLoaded)) {
                    files.put(id, file);
                }
            } catch (Exception unreadable) {
                errors.add(directory + "/" + id + " (pack " + resource.sourcePackId() + "): unreadable JSON: " + unreadable.getMessage());
            }
        });
        return files;
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        applyCatalog(prepared);
        installSigns(prepared.signFiles());
        installCommunityTiers(prepared.communityFiles());
    }

    private static void applyCatalog(Prepared prepared) {
        Predicate<String> isModLoaded = ModList.get()::isLoaded;
        CatalogEditor editor = new CatalogEditor(prepared.catalog().availableWith(isModLoaded), isModLoaded);
        SkillEvents.post(new SkillEvents.CatalogLoading(editor));
        GeneratedTree.accept(new CatalogParser(McKnownIds.INSTANCE).parse(editor.sources()), prepared.errors(), prepared.migrations());
        prepared.errors().forEach(error -> NostalgiaSkills.LOGGER.error("[skill tree] {}", error));
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            GeneratedTree.generateFor(server);
        }
    }

    private static void installCommunityTiers(Map<String, JsonElement> communityFiles) {
        ValidationReport report = new ValidationReport();
        List<CommunityTier> tiers = new CommunityParser(McKnownIds.INSTANCE).parse(communityFiles, report);
        report.errors().forEach(issue -> NostalgiaSkills.LOGGER.error("[skill community] {}", issue));
        SkillTreeManager.installCommunityTiers(tiers);
        NostalgiaSkills.LOGGER.info("Community tiers loaded: {}", tiers.size());
    }

    private static void installSigns(Map<String, JsonElement> signFiles) {
        ValidationReport report = new ValidationReport();
        List<SkillSign> signs = new SignParser(McKnownIds.INSTANCE).parse(signFiles, report);
        report.errors().forEach(issue -> NostalgiaSkills.LOGGER.error("[skill signs] {}", issue));
        SkillTreeManager.installSigns(signs);
        NostalgiaSkills.LOGGER.info("Skill signs loaded: {}", signs.size());
    }

    static void logResult(SkillTreeManager.LoadResult result) {
        switch (result) {
            case APPLIED -> NostalgiaSkills.LOGGER.info("Skill tree loaded: {} nodes, revision {}",
                    SkillTreeManager.graph().map(graph -> graph.nodes().size()).orElse(0), SkillTreeManager.revision());
            case KEPT_PREVIOUS -> NostalgiaSkills.LOGGER.error("Skill tree reload rejected; keeping the last valid tree (revision {})",
                    SkillTreeManager.revision());
            case FAILED -> NostalgiaSkills.LOGGER.error("No valid skill tree available; skill features are disabled until a valid tree loads");
        }
    }

    static Map<NodeType, Integer> costOverrides() {
        Map<NodeType, Integer> overrides = new EnumMap<>(NodeType.class);
        if (ServerConfig.SPEC.isLoaded()) {
            overrides.put(NodeType.MINOR, ServerConfig.COST_MINOR.get());
            overrides.put(NodeType.NOTABLE, ServerConfig.COST_NOTABLE.get());
            overrides.put(NodeType.KEYSTONE, ServerConfig.COST_KEYSTONE.get());
            overrides.put(NodeType.ROOT, ServerConfig.COST_ROOT.get());
        }
        return overrides;
    }
}
