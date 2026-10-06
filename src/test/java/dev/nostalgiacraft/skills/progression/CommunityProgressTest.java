package dev.nostalgiacraft.skills.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.graph.CommunityParser;
import dev.nostalgiacraft.skills.graph.CommunityTier;
import dev.nostalgiacraft.skills.graph.KnownIds;
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

class CommunityProgressTest {
    private static final List<Long> TIERS = List.of(100L, 400L, 1000L);

    @Test
    void tiersUnlockAtTheirThresholds() {
        assertEquals(1000, CommunityProgress.goal(TIERS));
        assertEquals(0, CommunityProgress.unlockedCount(TIERS, 99));
        assertEquals(1, CommunityProgress.unlockedCount(TIERS, 100));
        assertEquals(3, CommunityProgress.unlockedCount(TIERS, 5000));
        assertEquals(Optional.of(400L), CommunityProgress.nextThreshold(TIERS, 100));
        assertEquals(Optional.empty(), CommunityProgress.nextThreshold(TIERS, 1000));
        assertEquals(0, CommunityProgress.goal(List.of()));
    }

    @Test
    void thePathFillsEvenlyBetweenTiersWhateverTheirDistance() {
        assertEquals(0.0, CommunityProgress.pathFraction(TIERS, 0));
        assertEquals(1.0 / 6, CommunityProgress.pathFraction(TIERS, 50), 1e-9);
        assertEquals(1.0 / 3, CommunityProgress.pathFraction(TIERS, 100), 1e-9);
        assertEquals(0.5, CommunityProgress.pathFraction(TIERS, 250), 1e-9);
        assertEquals(1.0, CommunityProgress.pathFraction(TIERS, 1000));
        assertEquals(1.0, CommunityProgress.pathFraction(TIERS, 99999));
        assertEquals(0.0, CommunityProgress.pathFraction(List.of(), 500));
    }

    @Test
    void aDonationIsCappedByTheBalanceAndByWhatIsStillMissing() {
        assertEquals(50, CommunityProgress.donate(TIERS, 0, 50, 200).accepted());
        assertEquals(200, CommunityProgress.donate(TIERS, 0, 500, 200).accepted());
        assertEquals(30, CommunityProgress.donate(TIERS, 970, 500, 200).accepted());
        assertEquals(500, CommunityProgress.donate(TIERS, 0, 500, Integer.MAX_VALUE).accepted());
    }

    @Test
    void impossibleDonationsAreRejectedWithAReason() {
        assertEquals(RejectReason.INVALID_AMOUNT, CommunityProgress.donate(TIERS, 0, 500, 0).reason().orElseThrow());
        assertEquals(RejectReason.COMMUNITY_COMPLETE, CommunityProgress.donate(TIERS, 1000, 500, 10).reason().orElseThrow());
        assertEquals(RejectReason.COMMUNITY_COMPLETE, CommunityProgress.donate(List.of(), 0, 500, 10).reason().orElseThrow());
        assertEquals(RejectReason.INSUFFICIENT_XP, CommunityProgress.donate(TIERS, 0, 0, 10).reason().orElseThrow());
        assertFalse(CommunityProgress.donate(TIERS, 0, 0, 10).ok());
    }

    @Test
    void theShippedTiersParseInAscendingOrder() throws IOException {
        Map<String, JsonElement> files = new HashMap<>();
        try (Stream<Path> paths = Files.list(Path.of("src/main/resources/data/nostalgiaskills/skill_community"))) {
            for (Path path : paths.toList()) {
                files.put("nostalgiaskills:" + path.getFileName().toString().replace(".json", ""),
                        JsonParser.parseString(Files.readString(path)));
            }
        }
        ValidationReport report = new ValidationReport();
        List<CommunityTier> tiers = new CommunityParser(KnownIds.PERMISSIVE).parse(files, report);
        assertFalse(report.hasErrors(), report.errors().toString());
        assertEquals(List.of(2000L, 8000L, 20000L, 50000L), tiers.stream().map(CommunityTier::xpRequired).toList());
        assertTrue(tiers.stream().noneMatch(tier -> tier.effects().isEmpty()));
    }

    @Test
    void aTierWithoutAThresholdIsReportedAndSkipped() {
        Map<String, JsonElement> files = Map.of("t:broken", JsonParser.parseString("{\"schema_version\":1,\"effects\":[]}"));
        ValidationReport report = new ValidationReport();
        assertTrue(new CommunityParser(KnownIds.PERMISSIVE).parse(files, report).isEmpty());
        assertTrue(report.errors().toString().contains("xp_required"));
    }
}
