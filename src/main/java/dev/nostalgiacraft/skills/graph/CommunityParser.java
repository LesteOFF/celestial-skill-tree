package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class CommunityParser {
    private static final int MAX_XP_REQUIRED = 1_000_000_000;

    private final EffectParser effectParser;

    public CommunityParser(KnownIds known) {
        this.effectParser = new EffectParser(known);
    }

    public List<CommunityTier> parse(Map<String, JsonElement> files, ValidationReport report) {
        List<CommunityTier> tiers = new ArrayList<>();
        for (Map.Entry<String, JsonElement> file : new TreeMap<>(files).entrySet()) {
            String source = "skill_community/" + file.getKey();
            try {
                tier(file.getKey(), file.getValue(), source, report).ifPresent(tiers::add);
            } catch (RuntimeException unreadable) {
                report.error(source, "", "unreadable community tier: " + unreadable.getMessage());
            }
        }
        tiers.sort(Comparator.comparingLong(CommunityTier::xpRequired).thenComparing(CommunityTier::id));
        return List.copyOf(tiers);
    }

    private Optional<CommunityTier> tier(String id, JsonElement element, String source, ValidationReport report) {
        if (!element.isJsonObject()) {
            report.error(source, "", "community tier file must be a JSON object");
            return Optional.empty();
        }
        int errorsBefore = report.errors().size();
        JsonFields tier = new JsonFields(element.getAsJsonObject(), source, "", report);
        tier.requireSupportedSchema();
        if (!Ids.isValid(id)) {
            tier.error("id", "invalid id derived from path: '" + id + "'");
        }
        int xpRequired = tier.integerWithin("xp_required", 0, 1, MAX_XP_REQUIRED);
        List<SkillEffect> effects = new ArrayList<>();
        JsonArray array = tier.array("effects").orElseGet(JsonArray::new);
        if (array.size() > GraphParser.MAX_EFFECTS) {
            tier.error("effects", "at most " + GraphParser.MAX_EFFECTS + " effects");
        }
        for (int i = 0; i < array.size(); i++) {
            effectParser.parse(tier.child(array.get(i).getAsJsonObject(), "effects[" + i + "].")).ifPresent(effects::add);
        }
        boolean valid = report.errors().size() == errorsBefore;
        return valid ? Optional.of(new CommunityTier(id, xpRequired, List.copyOf(effects))) : Optional.empty();
    }
}
