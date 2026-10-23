package dev.lesteoff.celestialskilltree.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.graph.CommunityParser;
import dev.lesteoff.celestialskilltree.graph.CommunityTier;
import dev.lesteoff.celestialskilltree.graph.KnownIds;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityProgressTest {
    @Test
    void theShippedTiersParseInAscendingOrder() throws IOException {
        Map<String, JsonElement> files = new HashMap<>();
        try (Stream<Path> paths = Files.list(Path.of("src/main/resources/data/celestialskilltree/skill_community"))) {
            for (Path path : paths.toList()) {
                files.put("celestialskilltree:" + path.getFileName().toString().replace(".json", ""),
                        JsonParser.parseString(Files.readString(path)));
            }
        }
        ValidationReport report = new ValidationReport();
        List<CommunityTier> tiers = new CommunityParser(KnownIds.PERMISSIVE).parse(files, report);
        assertFalse(report.hasErrors(), report.errors().toString());
        assertEquals(List.of(2000L, 8000L, 20000L, 50000L), tiers.stream().map(CommunityTier::xpRequired).toList());
        assertTrue(tiers.stream().noneMatch(tier -> tier.effects().isEmpty()));
    }
}
