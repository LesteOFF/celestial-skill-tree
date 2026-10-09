package dev.nostalgiacraft.skills.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.graph.KnownIds;
import dev.nostalgiacraft.skills.graph.SignParser;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillSign;
import dev.nostalgiacraft.skills.graph.ValidationReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignServiceTest {
    private static final Predicate<String> EVERY_MOD = mod -> true;
    private static final KnownIds EVERYTHING_KNOWN = new KnownIds() {
        @Override
        public boolean isAttribute(String id) {
            return true;
        }

        @Override
        public boolean isMobEffect(String id) {
            return true;
        }
    };

    @Test
    void everyShippedSignSwapsToAPartnerAttributeThatHasItsOwnText() throws IOException {
        List<SkillSign> signs = shippedSigns(EVERY_MOD);
        assertEquals(12, signs.size());
        assertTrue(signs.stream().map(SignServiceTest::onlyAttribute).noneMatch(SignServiceTest::isBuiltIn));
        for (String language : List.of("en_us", "pt_br")) {
            JsonObject translations = JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/nostalgiaskills/lang/" + language + ".json"))).getAsJsonObject();
            signs.forEach(sign -> assertTrue(translations.has(sign.descriptionKey()), () -> language + " lacks " + sign.descriptionKey()));
        }
    }

    private static List<SkillSign> shippedSigns(Predicate<String> isModLoaded) throws IOException {
        Map<String, JsonElement> files = new HashMap<>();
        Path directory = Path.of("src/main/resources/data/nostalgiaskills/skill_signs");
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString().replace(".json", "");
                files.put("nostalgiaskills:" + name, JsonParser.parseString(Files.readString(path)));
            }
        }
        ValidationReport report = new ValidationReport();
        List<SkillSign> signs = new SignParser(EVERYTHING_KNOWN, isModLoaded).parse(files, report);
        assertFalse(report.hasErrors(), report.errors().toString());
        return signs;
    }

    private static String onlyAttribute(SkillSign sign) {
        assertEquals(1, sign.effects().size(), sign.id());
        return ((SkillEffect.Attribute) sign.effects().get(0)).attribute();
    }

    private static boolean isBuiltIn(String attribute) {
        return attribute.startsWith("minecraft:") || attribute.startsWith("nostalgiaskills:");
    }
}
