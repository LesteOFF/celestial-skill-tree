package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonElement;
import dev.nostalgiacraft.skills.graph.JsonFields;
import dev.nostalgiacraft.skills.graph.ValidationReport;

import java.util.Set;

final class RulesParser {
    static final String SOURCE = "skill_rules/main";
    private static final String NODES_PER_CLASS = "nodes_per_class";
    private static final String MAX_CLASSES = "max_classes";
    private static final String MIN_THEMES_FOR_OWN_CLASS = "min_themes_for_own_class";
    private static final String MAX_COPIES_PER_SKILL = "max_copies_per_skill";
    private static final String VARY_BY_SEED = "vary_by_seed";
    private static final String PORTALS = "portals";
    private static final Set<String> KNOWN = Set.of(NODES_PER_CLASS, MAX_CLASSES, MIN_THEMES_FOR_OWN_CLASS,
            MAX_COPIES_PER_SKILL, VARY_BY_SEED, PORTALS);

    private RulesParser() {
    }

    static TreeRules parse(JsonElement file, ValidationReport report) {
        if (!file.isJsonObject()) {
            report.error(SOURCE, "", "file must be a JSON object");
            return TreeRules.DEFAULT;
        }
        JsonFields rules = new JsonFields(file.getAsJsonObject(), SOURCE, "", report);
        file.getAsJsonObject().keySet().stream().filter(key -> !KNOWN.contains(key)).sorted()
                .forEach(key -> rules.warn(key, "unknown rule, ignored"));
        try {
            return read(rules);
        } catch (RuntimeException unreadable) {
            report.error(SOURCE, "", "unreadable rules: " + unreadable.getMessage());
            return TreeRules.DEFAULT;
        }
    }

    private static TreeRules read(JsonFields rules) {
        TreeRules standard = TreeRules.DEFAULT;
        return new TreeRules(
                rules.integerWithin(NODES_PER_CLASS, standard.nodesPerClass(), TreeRules.MIN_NODES_PER_CLASS, TreeRules.MAX_NODES_PER_CLASS),
                rules.integerWithin(MAX_CLASSES, standard.maxClasses(), 1, TreeRules.MAX_CLASSES),
                rules.integerWithin(MIN_THEMES_FOR_OWN_CLASS, standard.minThemesForOwnClass(), 1, TreeRules.MAX_THEMES_FOR_OWN_CLASS),
                rules.integerWithin(MAX_COPIES_PER_SKILL, standard.maxCopiesPerSkill(), 1, TreeRules.MAX_COPIES_PER_SKILL),
                rules.flag(VARY_BY_SEED, standard.varyBySeed()),
                rules.flag(PORTALS, standard.portals()));
    }
}
