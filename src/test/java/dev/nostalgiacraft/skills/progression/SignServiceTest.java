package dev.nostalgiacraft.skills.progression;

import com.google.gson.JsonElement;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignServiceTest {
    private static final List<String> SIGNS = List.of("t:a", "t:b", "t:c");
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
        assertEquals(Optional.of("t:b"), SignService.assignIfMissing(state, SIGNS, 4));
        assertEquals(Optional.of("t:b"), state.sign());
        assertEquals(revision + 1, state.stateRevision());
        assertEquals(Optional.empty(), SignService.assignIfMissing(state, SIGNS, 5));
        assertEquals(Optional.of("t:b"), state.sign());
        assertEquals(revision + 1, state.stateRevision());
    }

    @Test
    void noSignsMeansNoAssignmentAndDaysWrapAround() {
        PlayerSkillState state = new PlayerSkillState();
        assertEquals(Optional.empty(), SignService.assignIfMissing(state, List.of(), 3));
        assertTrue(state.sign().isEmpty());
        assertEquals(Optional.of("t:a"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 0));
        assertEquals(Optional.of("t:c"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 2));
        assertEquals(Optional.of("t:a"), SignService.assignIfMissing(new PlayerSkillState(), SIGNS, 3));
    }

    @Test
    void theSignSurvivesACopyAndAFullReset() {
        PlayerSkillState state = new PlayerSkillState();
        SignService.assignIfMissing(state, SIGNS, 1);
        RespecService.resetAll(state);
        assertEquals(Optional.of("t:b"), state.sign());
        assertEquals(Optional.of("t:b"), state.copy().sign());
    }

    @Test
    void everyShippedSignParsesWithOneAttributeEffect() throws IOException {
        Map<String, JsonElement> files = new HashMap<>();
        Path directory = Path.of("src/main/resources/data/nostalgiaskills/skill_signs");
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString().replace(".json", "");
                files.put("nostalgiaskills:" + name, JsonParser.parseString(Files.readString(path)));
            }
        }
        ValidationReport report = new ValidationReport();
        List<SkillSign> signs = new SignParser(EVERYTHING_KNOWN).parse(files, report);
        assertFalse(report.hasErrors(), report.errors().toString());
        assertEquals(12, signs.size());
        assertTrue(signs.stream().allMatch(sign -> sign.effects().size() == 1 && sign.effects().get(0) instanceof SkillEffect.Attribute));
    }

    @Test
    void aBrokenSignIsReportedAndSkipped() {
        Map<String, JsonElement> files = Map.of(
                "t:good", JsonParser.parseString("{\"schema_version\":1,\"effects\":[{\"type\":\"attribute\","
                        + "\"attribute\":\"minecraft:generic.armor\",\"operation\":\"add_value\",\"amount\":1}]}"),
                "t:bad", JsonParser.parseString("{\"schema_version\":1,\"effects\":[{\"type\":\"nonsense\"}]}"));
        ValidationReport report = new ValidationReport();
        List<SkillSign> signs = new SignParser(EVERYTHING_KNOWN).parse(files, report);
        assertEquals(List.of("t:good"), signs.stream().map(SkillSign::id).toList());
        assertTrue(report.hasErrors());
    }
}
