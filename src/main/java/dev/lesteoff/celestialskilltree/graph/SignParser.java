package dev.lesteoff.celestialskilltree.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class SignParser {
    private static final String PARTNER_EFFECTS = "partner_effects";

    private final EffectParser effectParser;
    private final Predicate<String> isModLoaded;

    public SignParser(KnownIds known, Predicate<String> isModLoaded) {
        this.effectParser = new EffectParser(known);
        this.isModLoaded = isModLoaded;
    }

    public List<SkillSign> parse(Map<String, JsonElement> files, ValidationReport report) {
        List<SkillSign> signs = new ArrayList<>();
        for (Map.Entry<String, JsonElement> file : new TreeMap<>(files).entrySet()) {
            String source = "skill_signs/" + file.getKey();
            try {
                sign(file.getKey(), file.getValue(), source, report).ifPresent(signs::add);
            } catch (RuntimeException unreadable) {
                report.error(source, "", "unreadable sign: " + unreadable.getMessage());
            }
        }
        return List.copyOf(signs);
    }

    private Optional<SkillSign> sign(String id, JsonElement element, String source, ValidationReport report) {
        if (!element.isJsonObject()) {
            report.error(source, "", "sign file must be a JSON object");
            return Optional.empty();
        }
        int errorsBefore = report.errors().size();
        JsonFields sign = new JsonFields(element.getAsJsonObject(), source, "", report);
        sign.checkSchemaVersion();
        if (!Ids.isValid(id)) {
            sign.error("id", "invalid id derived from path: '" + id + "'");
        }
        JsonFields active = activePartner(sign).orElse(sign);
        String descriptionKey = active.optionalText("description").orElse(SkillSign.defaultDescriptionKey(id));
        List<SkillEffect> effects = effectParser.parseAll(active);
        return report.errors().size() > errorsBefore ? Optional.empty() : Optional.of(new SkillSign(id, effects, descriptionKey));
    }

    private Optional<JsonFields> activePartner(JsonFields sign) {
        if (!sign.has(PARTNER_EFFECTS)) {
            return Optional.empty();
        }
        JsonArray partners = sign.array(PARTNER_EFFECTS).orElseGet(JsonArray::new);
        for (int i = 0; i < partners.size(); i++) {
            JsonElement partner = partners.get(i);
            if (partner.isJsonObject() && ModRequirement.isMet(partner, isModLoaded)) {
                return Optional.of(sign.child(partner.getAsJsonObject(), PARTNER_EFFECTS + "[" + i + "]."));
            }
        }
        return Optional.empty();
    }
}
