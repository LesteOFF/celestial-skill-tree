package dev.nostalgiacraft.skills.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTest {
    @Test
    void exactCostPurchaseSucceedsAndShortByOneFails() {
        PlayerSkillState state = new PlayerSkillState();
        ProgressionService.PurchaseResult shortBy1 = ProgressionService.purchasePoint(state, 99, ProgressionService.Settings.DEFAULT);
        assertFalse(shortBy1.ok());
        assertEquals(RejectReason.INSUFFICIENT_XP, shortBy1.reason().orElseThrow());
        assertEquals(0, state.purchasedPoints());
        ProgressionService.PurchaseResult exact = ProgressionService.purchasePoint(state, 100, ProgressionService.Settings.DEFAULT);
        assertTrue(exact.ok());
        assertEquals(0, exact.newXpTotal());
        assertEquals(1, state.availablePoints());
        assertTrue(state.ledgerHolds());
    }

    @Test
    void anEclipseLastsTheDaylightOfItsDayAndAShowerItsNight() {
        long day7 = 7 * 24_000L;
        assertEquals(SkyEvent.ECLIPSE, SkyEventSchedule.current(7, 7, 7, day7));
        assertEquals(SkyEvent.ECLIPSE, SkyEventSchedule.current(7, 9, 7, day7 + 12_541));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(7, 9, 7, day7 + 12_542));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(7, 9, 7, day7 + 23_999));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(7, 9, 8, day7 + 24_000));
        assertEquals(SkyEvent.STAR_SHOWER, SkyEventSchedule.current(7, 7, 7, day7 + 12_542));
        assertEquals(SkyEvent.STAR_SHOWER, SkyEventSchedule.current(9, 7, 7, day7 + 23_459));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(9, 7, 7, day7 + 23_460));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(9, 7, 7, day7 + 6_000));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(SkyEventSchedule.UNSCHEDULED, SkyEventSchedule.UNSCHEDULED, 7, day7));
    }

    @Test
    void aClockSetBackStillCountsANewDay() {
        assertEquals(3, SkyEventSchedule.dayAfter(3, 6_000, 6_020));
        assertEquals(4, SkyEventSchedule.dayAfter(3, 23_990, 10));
        assertEquals(4, SkyEventSchedule.dayAfter(3, 14_000, 1_000));
        assertEquals(SkyEvent.ECLIPSE, SkyEventSchedule.current(4, 9, 4, 1_000));
    }
}
