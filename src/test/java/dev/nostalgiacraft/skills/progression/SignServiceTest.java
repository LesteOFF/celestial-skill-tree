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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignServiceTest {
    private static final List<String> SIGNS = List.of("t:a", "t:b", "t:c");
    private static final UUID FIRST_PLAYER = new UUID(0, 0);
    private static final UUID SECOND_PLAYER = new UUID(0, 1);
    private static final Predicate<String> NO_MODS = mod -> false;
    private static final Predicate<String> EVERY_MOD = mod -> true;
    private static final String ARMOR = "{\"type\":\"attribute\",\"attribute\":\"minecraft:generic.armor\","
            + "\"operation\":\"add_value\",\"amount\":1}";
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
    void theWorldDayPicksTheSignAndItNeverChangesAfterwards() {
        PlayerSkillState state = new PlayerSkillState();
        long revision = state.stateRevision();
        assertEquals(Optional.of("t:b"), SignService.assignIfMissing(state, SIGNS, 4, FIRST_PLAYER));
        assertEquals(Optional.of("t:b"), state.sign());
        assertEquals(revision + 1, state.stateRevision());
        assertEquals(Optional.empty(), SignService.assignIfMissing(state, SIGNS, 5, FIRST_PLAYER));
        assertEquals(Optional.of("t:b"), state.sign());
        assertEquals(revision + 1, state.stateRevision());
    }

    @Test
    void noSignsMeansNoAssignmentAndDaysWrapAround() {
        PlayerSkillState state = new PlayerSkillState();
        assertEquals(Optional.empty(), SignService.assignIfMissing(state, List.of(), 3, FIRST_PLAYER));
        assertTrue(state.sign().isEmpty());
        assertEquals(Optional.of("t:a"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 0, FIRST_PLAYER));
        assertEquals(Optional.of("t:c"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 2, FIRST_PLAYER));
        assertEquals(Optional.of("t:a"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 3, FIRST_PLAYER));
    }

    @Test
    void playersBornOnTheSameDayGetDifferentSigns() {
        assertEquals(Optional.of("t:a"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 0, FIRST_PLAYER));
        assertEquals(Optional.of("t:b"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 0, SECOND_PLAYER));
    }

    @Test
    void theSignSurvivesACopyAndAFullReset() {
        PlayerSkillState state = new PlayerSkillState();
        SignService.assignIfMissing(state, SIGNS, 1, FIRST_PLAYER);
        RespecService.resetAll(state);
        assertEquals(Optional.of("t:b"), state.sign());
        assertEquals(Optional.of("t:b"), state.copy().sign());
    }

    @Test
    void everyShippedSignParsesWithOneAttributeEffect() throws IOException {
        List<SkillSign> signs = shippedSigns(NO_MODS);
        assertEquals(12, signs.size());
        assertTrue(signs.stream().allMatch(sign -> sign.descriptionKey().equals(SkillSign.defaultDescriptionKey(sign.id()))));
        assertTrue(signs.stream().map(SignServiceTest::onlyAttribute).allMatch(SignServiceTest::isBuiltIn));
    }

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

    @Test
    void aPartnerVariantOnlyAppliesWhileItsModIsLoaded() {
        Map<String, JsonElement> files = Map.of("t:sign", JsonParser.parseString("{\"schema_version\":1,\"effects\":[" + ARMOR
                + "],\"partner_effects\":[{\"requires_mod\":\"partner\",\"description\":\"t.partner\",\"effects\":[" + ARMOR + "," + ARMOR + "]}]}"));
        SkillSign alone = new SignParser(EVERYTHING_KNOWN, NO_MODS).parse(files, new ValidationReport()).get(0);
        SkillSign together = new SignParser(EVERYTHING_KNOWN, "partner"::equals).parse(files, new ValidationReport()).get(0);
        assertEquals(1, alone.effects().size());
        assertEquals("sign.t.sign.description", alone.descriptionKey());
        assertEquals(2, together.effects().size());
        assertEquals("t.partner", together.descriptionKey());
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

    @Test
    void aBrokenSignIsReportedAndSkipped() {
        Map<String, JsonElement> files = Map.of(
                "t:good", JsonParser.parseString("{\"schema_version\":1,\"effects\":[" + ARMOR + "]}"),
                "t:bad", JsonParser.parseString("{\"schema_version\":1,\"effects\":[{\"type\":\"nonsense\"}]}"));
        ValidationReport report = new ValidationReport();
        List<SkillSign> signs = new SignParser(EVERYTHING_KNOWN, NO_MODS).parse(files, report);
        assertEquals(List.of("t:good"), signs.stream().map(SkillSign::id).toList());
        assertTrue(report.hasErrors());
    }
}
