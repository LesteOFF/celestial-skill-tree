package dev.lesteoff.celestialskilltree.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class CommunityParser {
    public static final int MAX_STARS = 64;
    private static final int MAX_XP_REQUIRED = 1_000_000_000;
    private static final String FOLDER = "skill_community";

    private final EffectParser effectParser;

    public CommunityParser(KnownIds known) {
        this.effectParser = new EffectParser(known);
    }

    public List<CommunityTier> parse(Map<String, JsonElement> files, ValidationReport report) {
        Map<String, CommunityTier> stars = new TreeMap<>();
        for (Map.Entry<String, JsonElement> file : new TreeMap<>(files).entrySet()) {
            String source = source(file.getKey());
            try {
                tier(file.getKey(), file.getValue(), source, report).ifPresent(star -> stars.put(star.id(), star));
            } catch (RuntimeException unreadable) {
                report.error(source, "", "unreadable community tier: " + unreadable.getMessage());
            }
        }
        dropBrokenExclusiveGroups(stars, report);
        List<CommunityTier> linked = linked(stars, report);
        if (linked.size() > MAX_STARS) {
            report.error(FOLDER, "", "at most " + MAX_STARS + " community stars");
            return List.of();
        }
        return linked;
    }

    private static String source(String id) {
        return FOLDER + "/" + id;
    }

    private Optional<CommunityTier> tier(String id, JsonElement element, String source, ValidationReport report) {
        if (!element.isJsonObject()) {
            report.error(source, "", "community tier file must be a JSON object");
            return Optional.empty();
        }
        int errorsBefore = report.errors().size();
        JsonFields tier = new JsonFields(element.getAsJsonObject(), source, "", report);
        tier.checkSchemaVersion();
        if (!Ids.isValid(id)) {
            tier.error("id", "invalid id derived from path: '" + id + "'");
        }
        int xpRequired = tier.integerWithin("xp_required", 0, 1, MAX_XP_REQUIRED);
        Optional<String> parent = tier.optionalId("parent");
        if (parent.filter(id::equals).isPresent()) {
            tier.error("parent", "a star cannot be its own parent");
        }
        Optional<String> exclusive = tier.optionalId("exclusive");
        String icon = tier.optionalId("icon").orElse(CommunityTier.DEFAULT_ICON);
        Vec2 position = tier.object("position").map(CommunityParser::position).orElse(new Vec2(0, 0));
        List<SkillEffect> effects = effects(tier);
        boolean valid = report.errors().size() == errorsBefore;
        return valid ? Optional.of(new CommunityTier(id, xpRequired, parent, exclusive, icon, position, List.copyOf(effects)))
                : Optional.empty();
    }

    private static Vec2 position(JsonFields position) {
        double x = position.number("x");
        double y = position.number("y");
        position.requireBounded("x", x);
        position.requireBounded("y", y);
        return new Vec2(x, y);
    }

    private List<SkillEffect> effects(JsonFields tier) {
        List<SkillEffect> effects = new ArrayList<>();
        JsonArray array = tier.array("effects").orElseGet(JsonArray::new);
        if (array.size() > GraphParser.MAX_EFFECTS) {
            tier.error("effects", "at most " + GraphParser.MAX_EFFECTS + " effects");
        }
        for (int i = 0; i < array.size(); i++) {
            effectParser.parse(tier.child(array.get(i).getAsJsonObject(), "effects[" + i + "].")).ifPresent(effects::add);
        }
        return effects;
    }

    private static void dropBrokenExclusiveGroups(Map<String, CommunityTier> stars, ValidationReport report) {
        Map<String, List<CommunityTier>> groups = stars.values().stream().filter(star -> star.exclusive().isPresent())
                .collect(Collectors.groupingBy(star -> star.exclusive().get(), TreeMap::new, Collectors.toList()));
        groups.forEach((group, members) -> {
            if (members.stream().map(CommunityTier::parent).distinct().count() > 1) {
                members.forEach(member -> {
                    report.error(source(member.id()), "exclusive", "every star of '" + group + "' must share one parent");
                    stars.remove(member.id());
                });
            } else if (members.size() == 1) {
                report.warn(source(members.get(0).id()), "exclusive", "'" + group + "' has no rival star");
            }
        });
    }

    private static List<CommunityTier> linked(Map<String, CommunityTier> stars, ValidationReport report) {
        Map<String, Integer> depths = new HashMap<>();
        stars.values().forEach(star -> depth(star, stars, report).ifPresent(depth -> depths.put(star.id(), depth)));
        return stars.values().stream().filter(star -> depths.containsKey(star.id()))
                .sorted(Comparator.comparingInt((CommunityTier star) -> depths.get(star.id())).thenComparing(CommunityTier::id))
                .toList();
    }

    private static OptionalInt depth(CommunityTier star, Map<String, CommunityTier> stars, ValidationReport report) {
        Set<String> visited = new HashSet<>();
        CommunityTier current = star;
        while (current.parent().isPresent()) {
            if (!visited.add(current.id())) {
                report.error(source(star.id()), "parent", "the parent chain loops back to '" + current.id() + "'");
                return OptionalInt.empty();
            }
            String parentId = current.parent().get();
            current = stars.get(parentId);
            if (current == null) {
                report.error(source(star.id()), "parent", "unknown or invalid parent '" + parentId + "'");
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(visited.size());
    }
}
