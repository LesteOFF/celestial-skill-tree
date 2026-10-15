package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonArray;
import dev.nostalgiacraft.skills.graph.EffectParser;
import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.JsonFields;
import dev.nostalgiacraft.skills.graph.KnownIds;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.graph.TraitsParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class CatalogEntryParser {
    static final String HUB_SUFFIX = "/hub";
    static final String PORTAL_SUFFIX = "/portal";
    static final String TRUNK_INFIX = "/trunk_";
    static final String MINOR_INFIX = "_";
    private static final int LARGEST_INDEX_DIGITS = 3;
    private static final int MAX_CLASS_ID = GraphParser.MAX_NODE_ID - TRUNK_INFIX.length() - LARGEST_INDEX_DIGITS;
    private static final int MAX_THEME_ID = GraphParser.MAX_NODE_ID - MINOR_INFIX.length() - LARGEST_INDEX_DIGITS;
    private static final int MAX_NOTABLES = 32;
    private static final int MAX_PRIORITY = 1_000;
    private static final int DEFAULT_WEIGHT = 10;
    private static final int MAX_WEIGHT = 1_000;
    private static final Pattern LOCAL_ID = Pattern.compile("[a-z0-9_]+");

    private final EffectParser effects;

    CatalogEntryParser(KnownIds known) {
        this.effects = new EffectParser(known);
    }

    ClassDefinition classDefinition(String id, JsonFields file) {
        requireIdWithin(file, id, MAX_CLASS_ID);
        String name = file.text("name");
        String icon = file.id("icon");
        int priority = file.integerWithin("priority", 0, -MAX_PRIORITY, MAX_PRIORITY);
        Optional<Integer> skills = file.has("nodes") ? Optional.of(
                file.integerWithin("nodes", 0, TreeRules.MIN_NODES_PER_CLASS, TreeRules.MAX_NODES_PER_CLASS)) : Optional.empty();
        SkillTemplate hub = nested(file, "hub", name, icon);
        SkillTemplate trunk = nested(file, "trunk", null, icon);
        return new ClassDefinition(id, priority, file.optionalId("fallback_class"), skills, hub, trunk);
    }

    ThemeDefinition theme(String id, JsonFields file) {
        requireIdWithin(file, id, MAX_THEME_ID);
        String classId = file.id("class");
        String name = file.text("name");
        String icon = file.id("icon");
        SkillTemplate minor = nested(file, "minor", name, icon);
        return new ThemeDefinition(id, classId, weight(file), minor, notables(file, id, icon));
    }

    KeystoneDefinition keystone(String id, JsonFields file, Map<String, ClassDefinition> classes) {
        requireIdWithin(file, id, GraphParser.MAX_NODE_ID);
        String classId = file.id("class");
        if (!file.has("text")) {
            file.error("text", "a keystone must describe itself in plain words");
        }
        String classIcon = Optional.ofNullable(classes.get(classId)).map(found -> found.hub().icon()).orElse(null);
        SkillTemplate skill = skill(file, file.text("name"), classIcon);
        return new KeystoneDefinition(id, classId, weight(file), skill, TraitsParser.parse(file), file.optionalId("exclusive_with"));
    }

    private List<NotableDefinition> notables(JsonFields theme, String themeId, String themeIcon) {
        if (!theme.has("notables")) {
            return List.of();
        }
        JsonArray array = theme.array("notables").orElseGet(JsonArray::new);
        if (array.size() > MAX_NOTABLES) {
            theme.error("notables", "at most " + MAX_NOTABLES + " notables");
        }
        List<NotableDefinition> notables = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonFields notable = theme.child(array.get(i).getAsJsonObject(), "notables[" + i + "].");
            String notableId = notableId(notable, themeId, seen);
            SkillTemplate skill = skill(notable, notable.text("name"), themeIcon);
            warnWhenHardToRead(notable, skill);
            int maxRank = notable.integerWithin("max_rank", SkillNodeDefinition.SINGLE_RANK, SkillNodeDefinition.SINGLE_RANK,
                    SkillNodeDefinition.MAX_RANK);
            notables.add(new NotableDefinition(notableId, skill, maxRank, TraitsParser.blackHoleOnly(notable)));
        }
        return List.copyOf(notables);
    }

    private static String notableId(JsonFields notable, String themeId, Set<String> seen) {
        String id = notable.text("id");
        if (id == null) {
            return null;
        }
        if (!LOCAL_ID.matcher(id).matches()) {
            notable.error("id", "use only lowercase letters, digits and underscores, got '" + id + "'");
        } else if (!seen.add(id)) {
            notable.error("id", "duplicate notable '" + id + "' in this theme");
        } else if (notableNodeId(themeId, id).length() > GraphParser.MAX_NODE_ID) {
            notable.error("id", "the theme's folder plus the notable id must fit in " + GraphParser.MAX_NODE_ID + " characters");
        }
        return id;
    }

    static String notableNodeId(String themeId, String notableId) {
        int folderEnd = Math.max(themeId.lastIndexOf('/'), themeId.indexOf(':'));
        return themeId.substring(0, folderEnd + 1) + notableId;
    }

    private SkillTemplate nested(JsonFields owner, String key, String inheritedName, String inheritedIcon) {
        Optional<JsonFields> fields = owner.object(key);
        if (fields.isEmpty()) {
            return null;
        }
        String name = inheritedName == null || fields.get().has("name") ? fields.get().text("name") : inheritedName;
        SkillTemplate skill = skill(fields.get(), name, inheritedIcon);
        warnWhenHardToRead(fields.get(), skill);
        return skill;
    }

    private SkillTemplate skill(JsonFields fields, String name, String inheritedIcon) {
        String icon = fields.has("icon") || inheritedIcon == null ? fields.id("icon") : inheritedIcon;
        boolean declaredEmpty = fields.has("effects") && fields.json().get("effects").isJsonArray()
                && fields.json().getAsJsonArray("effects").isEmpty();
        if (declaredEmpty) {
            fields.error("effects", "needs at least one effect");
        }
        List<SkillEffect> parsed = effects.parseAll(fields);
        return new SkillTemplate(name, icon, parsed, fields.optionalText("text"));
    }

    private static void warnWhenHardToRead(JsonFields fields, SkillTemplate skill) {
        if (skill.isHardToRead() && skill.text().isEmpty()) {
            fields.warn("text", "combines a trigger with other effects, or has three or more effects, but has no plain-language text");
        }
    }

    private static int weight(JsonFields file) {
        return file.integerWithin("weight", DEFAULT_WEIGHT, 1, MAX_WEIGHT);
    }

    private static void requireIdWithin(JsonFields file, String id, int maxLength) {
        if (id.length() > maxLength) {
            file.error("id", "the id from the file path may have at most " + maxLength + " characters: '" + id + "'");
        }
    }
}
