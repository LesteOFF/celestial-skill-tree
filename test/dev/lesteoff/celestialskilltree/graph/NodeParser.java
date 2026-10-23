package dev.lesteoff.celestialskilltree.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class NodeParser {
    private static final int MAX_TAG_LENGTH = 64;
    private static final int MAX_TERM_LENGTH = 65;
    private static final int MAX_SECTOR_POINTS = 10_000;

    private final EffectParser effectParser;

    NodeParser(KnownIds known) {
        this.effectParser = new EffectParser(known);
    }

    Optional<SkillNodeDefinition> parse(String id, JsonElement element, String source, ValidationReport report) {
        if (!element.isJsonObject()) {
            report.error(source, "", "node file must be a JSON object");
            return Optional.empty();
        }
        JsonFields node = new JsonFields(element.getAsJsonObject(), source, "", report);
        int errorsBefore = report.errors().size();
        node.checkSchemaVersion();
        requireMatchingId(node, id);
        String tree = node.id("tree");
        String sector = node.text("sector");
        NodeType type = type(node);
        Vec2 position = position(node);
        String name = node.text("name");
        String description = node.text("description");
        String icon = node.id("icon");
        String frame = node.text("frame");
        int cost = type == null ? 0 : node.integerWithin("cost", type.defaultCost(), 0, GraphParser.MAX_COST);
        int maxRank = node.integerWithin("max_rank", SkillNodeDefinition.SINGLE_RANK, SkillNodeDefinition.SINGLE_RANK,
                SkillNodeDefinition.MAX_RANK);
        Requirements requirements = requirements(node);
        List<SkillEffect> effects = effectParser.parseAll(node);
        Set<String> tags = tags(node);
        String cluster = node.rawText("cluster", "");
        Optional<String> exclusiveGroup = node.optionalRawText("exclusive_group");
        NodeTraits traits = TraitsParser.parse(node);
        if (report.errors().size() > errorsBefore) {
            return Optional.empty();
        }
        return Optional.of(new SkillNodeDefinition(id, tree, sector, type, position, name, description, icon, frame, cost,
                maxRank, requirements, effects, Collections.unmodifiableSet(tags), cluster, exclusiveGroup, traits));
    }

    private static void requireMatchingId(JsonFields node, String id) {
        if (!Ids.isValid(id) || id.length() > GraphParser.MAX_NODE_ID) {
            node.error("id", "invalid id derived from path (max " + GraphParser.MAX_NODE_ID + " chars): '" + id + "'");
        }
        String explicitId = node.rawText("id", id);
        if (!explicitId.equals(id)) {
            node.error("id", "explicit id '" + explicitId + "' disagrees with file path id '" + id + "'");
        }
    }

    private static NodeType type(JsonFields node) {
        try {
            return NodeType.parse(node.text("type"));
        } catch (IllegalArgumentException invalidType) {
            node.error("type", invalidType.getMessage());
            return null;
        }
    }

    private static Vec2 position(JsonFields node) {
        Optional<JsonFields> fields = node.object("position");
        if (fields.isEmpty()) {
            return null;
        }
        Vec2 position = new Vec2(fields.get().number("x"), fields.get().number("y"));
        boolean withinBounds = Math.abs(position.x()) <= GraphParser.MAX_COORDINATE
                && Math.abs(position.y()) <= GraphParser.MAX_COORDINATE;
        if (!position.isFinite() || !withinBounds) {
            node.error("position", "coordinates must be finite and within +/-" + GraphParser.MAX_COORDINATE);
        }
        return position;
    }

    private static Requirements requirements(JsonFields node) {
        if (!node.has("requirements")) {
            return Requirements.NONE;
        }
        JsonObject json = node.json().getAsJsonObject("requirements");
        JsonFields requirements = node.child(json, "requirements.");
        List<String> allOf = terms(requirements, "all_of");
        List<String> anyOf = terms(requirements, "any_of");
        int minimumSectorPoints = requirements.integerWithin("minimum_sector_points", 0, 0, MAX_SECTOR_POINTS);
        return new Requirements(allOf, anyOf, minimumSectorPoints);
    }

    private static List<String> terms(JsonFields requirements, String key) {
        if (!requirements.has(key)) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        JsonArray array = requirements.json().getAsJsonArray(key);
        for (int i = 0; i < array.size(); i++) {
            String term = array.get(i).getAsString();
            boolean valid = term.startsWith("#") ? term.length() > 1 && term.length() <= MAX_TERM_LENGTH : Ids.isValid(term);
            if (!valid) {
                requirements.error(key + "[" + i + "]", "expected a node id or #tag, got '" + term + "'");
            }
            terms.add(term);
        }
        return List.copyOf(terms);
    }

    private static Set<String> tags(JsonFields node) {
        Set<String> tags = new TreeSet<>();
        if (!node.has("tags")) {
            return tags;
        }
        JsonArray array = node.json().getAsJsonArray("tags");
        if (array.size() > GraphParser.MAX_TAGS) {
            node.error("tags", "at most " + GraphParser.MAX_TAGS + " tags");
        }
        for (JsonElement element : array) {
            String tag = element.getAsString();
            if (tag.isEmpty() || tag.length() > MAX_TAG_LENGTH) {
                node.error("tags", "tag length must be 1.." + MAX_TAG_LENGTH);
            }
            tags.add(tag);
        }
        return tags;
    }
}
