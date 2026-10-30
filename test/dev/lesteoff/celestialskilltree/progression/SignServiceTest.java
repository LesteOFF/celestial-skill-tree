package dev.lesteoff.celestialskilltree.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.graph.KnownIds;
import dev.lesteoff.celestialskilltree.graph.SignParser;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignServiceTest {
    private static final Predicate<String> EVERY_MOD = mod -> true;
    private static final Predicate<String> EVERY_MOD_BUT_ASTRAL_SORCERY = mod -> !mod.equals("astralsorcery");
    private static final Predicate<String> ONLY_ASTRAL_SORCERY = "astralsorcery"::equals;
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
        List<SkillSign> signs = shippedSigns(EVERY_MOD_BUT_ASTRAL_SORCERY);
        assertEquals(12, signs.size());
        assertTrue(signs.stream().map(SignServiceTest::onlyAttribute).noneMatch(SignServiceTest::isBuiltIn));
        assertEveryDescriptionIsTranslated(signs);
    }

    @Test
    void astralSorceryAddsADiscoveryBonusOnTopOfTheActiveSign() throws IOException {
        for (Predicate<String> mods : List.of(EVERY_MOD, ONLY_ASTRAL_SORCERY)) {
            List<SkillSign> signs = shippedSigns(mods);
            assertEquals(12, signs.size());
            for (SkillSign sign : signs) {
                assertEquals(1, sign.bonuses().size(), sign.id());
                assertEquals(2, sign.allEffects().size(), sign.id());
                SkillEffect.ConditionalAttribute bonus = astralBonus(sign);
                assertEquals("astral_discovered", bonus.condition().type(), sign.id());
                assertTrue(bonus.condition().string("constellation").startsWith("astralsorcery:"), sign.id());
                assertEquals(2, sign.descriptionKeys().size(), sign.id());
            }
            assertEquals(12, signs.stream().map(sign -> astralBonus(sign).condition().string("constellation")).distinct().count());
            assertEveryDescriptionIsTranslated(signs);
        }
    }

    @Test
    void withoutPartnerModsASignKeepsItsOwnEffectAndText() throws IOException {
        for (SkillSign sign : shippedSigns(mod -> false)) {
            assertTrue(isBuiltIn(onlyAttribute(sign)), sign.id());
            assertEquals(List.of(SkillSign.defaultDescriptionKey(sign.id())), sign.descriptionKeys());
            assertTrue(sign.bonuses().isEmpty(), sign.id());
        }
    }

    private static void assertEveryDescriptionIsTranslated(List<SkillSign> signs) throws IOException {
        for (String language : List.of("en_us", "pt_br")) {
            JsonObject translations = JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/celestialskilltree/lang/" + language + ".json"))).getAsJsonObject();
            signs.stream().flatMap(sign -> sign.descriptionKeys().stream())
                    .forEach(key -> assertTrue(translations.has(key), () -> language + " lacks " + key));
        }
    }

    @Test
    void rerollAlwaysLandsOnAnotherSignAndLogsIt() {
        PlayerSkillState state = new PlayerSkillState();
        SignService.set(state, "a");
        Random random = new Random(7);
        for (int roll = 0; roll < 20; roll++) {
            String before = state.sign().orElseThrow();
            String after = SignService.reroll(state, List.of("a", "b", "c"), random).orElseThrow();
            assertFalse(after.equals(before));
        }
        assertTrue(state.audit().getLast().startsWith("sign set to "));
    }

    @Test
    void rerollWithNoOtherSignKeepsTheCurrentOne() {
        PlayerSkillState state = new PlayerSkillState();
        SignService.set(state, "a");

        assertEquals(Optional.empty(), SignService.reroll(state, List.of("a"), new Random(1)));
        assertEquals(Optional.of("a"), state.sign());
    }

    private static List<SkillSign> shippedSigns(Predicate<String> isModLoaded) throws IOException {
        Map<String, JsonElement> files = new HashMap<>();
        Path directory = Path.of("src/main/resources/data/celestialskilltree/skill_signs");
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString().replace(".json", "");
                files.put("celestialskilltree:" + name, JsonParser.parseString(Files.readString(path)));
            }
        }
        ValidationReport report = new ValidationReport();
        List<SkillSign> signs = new SignParser(EVERYTHING_KNOWN, isModLoaded).parse(files, report);
        assertFalse(report.hasErrors(), report.errors().toString());
        return signs;
    }

    private static SkillEffect.ConditionalAttribute astralBonus(SkillSign sign) {
        return (SkillEffect.ConditionalAttribute) sign.bonuses().get(0).effects().get(0);
    }

    private static String onlyAttribute(SkillSign sign) {
        assertEquals(1, sign.effects().size(), sign.id());
        return ((SkillEffect.Attribute) sign.effects().get(0)).attribute();
    }

    private static boolean isBuiltIn(String attribute) {
        return attribute.startsWith("minecraft:") || attribute.startsWith("celestialskilltree:");
    }
}
