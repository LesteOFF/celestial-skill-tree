package dev.lesteoff.celestialskilltree.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.graph.CommunityParser;
import dev.lesteoff.celestialskilltree.graph.CommunityTier;
import dev.lesteoff.celestialskilltree.graph.KnownIds;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress.StarStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityProgressTest {
    private static final String NS = "celestialskilltree:";
    private static final String KINDLING = NS + "kindling";
    private static final String HEARTH = NS + "hearth";
    private static final String BEACON = NS + "beacon";
    private static final String ZENITH = NS + "zenith";
    private static final String FORGE = NS + "forge";
    private static final String HUNT = NS + "hunt";
    private static final String DEEP_VEIN = NS + "deep_vein";

    private static List<CommunityTier> shipped() throws IOException {
        Map<String, JsonElement> files = new HashMap<>();
        try (Stream<Path> paths = Files.list(Path.of("src/main/resources/data/celestialskilltree/skill_community"))) {
            for (Path path : paths.toList()) {
                files.put(NS + path.getFileName().toString().replace(".json", ""), JsonParser.parseString(Files.readString(path)));
            }
        }
        ValidationReport report = new ValidationReport();
        List<CommunityTier> stars = new CommunityParser(KnownIds.PERMISSIVE).parse(files, report);
        assertFalse(report.hasErrors(), report.errors().toString());
        return stars;
    }

    private static ValidationReport parse(String... idAndJson) {
        Map<String, JsonElement> files = new HashMap<>();
        for (int i = 0; i < idAndJson.length; i += 2) {
            files.put(NS + idAndJson[i], JsonParser.parseString(idAndJson[i + 1]));
        }
        ValidationReport report = new ValidationReport();
        new CommunityParser(KnownIds.PERMISSIVE).parse(files, report);
        return report;
    }

    private static String star(String parent, String exclusive) {
        return "{\"xp_required\": 10, \"position\": {\"x\": 0, \"y\": 0}, \"effects\": []"
                + (parent == null ? "" : ", \"parent\": \"" + NS + parent + "\"")
                + (exclusive == null ? "" : ", \"exclusive\": \"" + NS + exclusive + "\"") + "}";
    }

    @Test
    void theShippedConstellationStartsAtItsTrunk() throws IOException {
        List<CommunityTier> stars = shipped();
        assertEquals(10, stars.size());
        assertEquals(KINDLING, stars.get(0).id());
        assertTrue(stars.stream().noneMatch(star -> star.effects().isEmpty()));
    }

    @Test
    void starsCarryTheirIconOrTheDefault() throws IOException {
        assertEquals(NS + "textures/skill_icons/health.png", CommunityProgress.find(shipped(), KINDLING).orElseThrow().icon());
        List<CommunityTier> plain = new CommunityParser(KnownIds.PERMISSIVE)
                .parse(Map.of(NS + "plain", JsonParser.parseString(star(null, null))), new ValidationReport());
        assertEquals(CommunityTier.DEFAULT_ICON, plain.get(0).icon());
    }

    @Test
    void pouringFillsOnlyTheTrunkAndKeepsTheOldTotals() throws IOException {
        List<CommunityTier> stars = shipped();
        CommunityProgress.Poured full = CommunityProgress.pour(stars, Map.of(), 1_000_000);
        assertEquals(50_000, full.accepted());
        assertEquals(Set.of(KINDLING, HEARTH, BEACON, ZENITH), CommunityProgress.lit(stars, full.progress()));
        CommunityProgress.Poured legacy = CommunityProgress.pour(stars, Map.of(), 9000);
        assertEquals(Map.of(KINDLING, 2000L, HEARTH, 6000L, BEACON, 1000L), legacy.given());
        assertEquals(Set.of(KINDLING, HEARTH), CommunityProgress.lit(stars, legacy.progress()));
    }

    @Test
    void donationsGoOnlyToOpenStarsAndStopAtWhatIsMissing() throws IOException {
        List<CommunityTier> stars = shipped();
        Map<String, Long> progress = Map.of(KINDLING, 2000L, HEARTH, 5900L);
        assertEquals(RejectReason.STAR_LOCKED, CommunityProgress.donate(stars, progress, BEACON, 500, 100).reason().orElseThrow());
        assertEquals(RejectReason.STAR_LIT, CommunityProgress.donate(stars, progress, KINDLING, 500, 100).reason().orElseThrow());
        assertEquals(RejectReason.STAR_LOCKED, CommunityProgress.donate(stars, progress, NS + "nowhere", 500, 100).reason().orElseThrow());
        assertEquals(RejectReason.INSUFFICIENT_XP, CommunityProgress.donate(stars, progress, HEARTH, 0, 100).reason().orElseThrow());
        assertEquals(100, CommunityProgress.donate(stars, progress, HEARTH, 500, 1000).accepted());
    }

    @Test
    void lightingAStarClosesItsRival() throws IOException {
        List<CommunityTier> stars = shipped();
        Map<String, Long> progress = Map.of(KINDLING, 2000L, HEARTH, 6000L, FORGE, 8000L);
        CommunityTier hunt = CommunityProgress.find(stars, HUNT).orElseThrow();
        assertEquals(StarStatus.EXCLUDED, CommunityProgress.status(stars, progress, hunt));
        assertEquals(StarStatus.OPEN, CommunityProgress.status(stars, progress, CommunityProgress.find(stars, DEEP_VEIN).orElseThrow()));
        assertEquals(StarStatus.LOCKED, CommunityProgress.status(stars, progress, CommunityProgress.find(stars, NS + "blood_moon").orElseThrow()));
        assertEquals(RejectReason.STAR_EXCLUDED, CommunityProgress.donate(stars, progress, HUNT, 500, 100).reason().orElseThrow());
    }

    @Test
    void brokenConstellationsAreReported() {
        assertTrue(parse("a", star("b", null), "b", star("a", null)).hasErrors());
        assertTrue(parse("a", star("missing", null)).hasErrors());
        assertTrue(parse("root", star(null, null), "a", star("root", "pair"), "b", star(null, "pair")).hasErrors());
        assertFalse(parse("root", star(null, null), "a", star("root", "pair"), "b", star("root", "pair")).hasErrors());
    }

    @Test
    void theRankingLeavesOutTheAnonymousPatron() {
        UUID small = new UUID(1, 1);
        UUID big = new UUID(2, 2);
        Map<UUID, CommunityProgress.Patron> patrons = Map.of(
                CommunityProgress.ANONYMOUS, new CommunityProgress.Patron("", Map.of(KINDLING, 9000L)),
                small, new CommunityProgress.Patron("Alex", Map.of(KINDLING, 100L)),
                big, new CommunityProgress.Patron("Steve", Map.of(KINDLING, 50L, HEARTH, 500L)));
        assertEquals(List.of(big, small), CommunityProgress.ranking(patrons));
        assertEquals(List.of("", "Alex", "Steve"),
                CommunityProgress.topDonors(patrons.values(), KINDLING, 3).stream().map(CommunityProgress.Patron::name).toList());
    }
}
