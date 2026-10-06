package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class SignParser {
    private final EffectParser effectParser;

    public SignParser(KnownIds known) {
        this.effectParser = new EffectParser(known);
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
        sign.requireSupportedSchema();
        if (!Ids.isValid(id)) {
            sign.error("id", "invalid id derived from path: '" + id + "'");
        }
        List<SkillEffect> effects = new ArrayList<>();
        JsonArray array = sign.array("effects").orElseGet(JsonArray::new);
        if (array.size() > GraphParser.MAX_EFFECTS) {
            sign.error("effects", "at most " + GraphParser.MAX_EFFECTS + " effects");
        }
        for (int i = 0; i < array.size(); i++) {
            effectParser.parse(sign.child(array.get(i).getAsJsonObject(), "effects[" + i + "].")).ifPresent(effects::add);
        }
        return report.errors().size() > errorsBefore ? Optional.empty() : Optional.of(new SkillSign(id, List.copyOf(effects)));
    }
}
