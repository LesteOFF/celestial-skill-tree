package dev.nostalgiacraft.skills.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTest {
    @Test
    void defaultCurveMatchesDesign() {
        XpCurve xpCurve = XpCurve.DEFAULT;
        assertEquals(100, xpCurve.cost(0));
        assertEquals(122, xpCurve.cost(1));
        assertEquals(500, xpCurve.cost(10));
        for (int p = 0; p < 200; p++) {
            assertTrue(xpCurve.cost(p + 1) >= xpCurve.cost(p), "curve must never decrease");
        }
    }

    @Test
    void discountNeverProducesFreeOrNegativeCosts() {
        XpCurve xpCurve = new XpCurve(1, 0, 0, 0.9);
        assertEquals(1, xpCurve.cost(0));
        assertEquals(90, new XpCurve(100, 0, 0, 0.1).cost(0));
        assertThrows(IllegalArgumentException.class, () -> new XpCurve(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new XpCurve(100, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new XpCurve(100, 0, 0, 1.0));
    }

    @Test
    void xpTotalsMatchVanillaLevelCosts() {
        long sum = 0;
        for (int level = 0; level < 200; level++) {
            assertEquals(sum, XpMath.totalForLevel(level), "level " + level);
            sum += XpMath.xpNeededForNextLevel(level);
        }
        assertEquals(17, XpMath.xpNeededForNextLevel(5));
        assertEquals(37, XpMath.xpNeededForNextLevel(15));
        assertEquals(42, XpMath.xpNeededForNextLevel(16));
        assertEquals(107, XpMath.xpNeededForNextLevel(29));
        assertEquals(112, XpMath.xpNeededForNextLevel(30));
        assertEquals(121, XpMath.xpNeededForNextLevel(31));
    }

    @Test
    void conversionsRoundTripAcrossLevelBoundaries() {
        for (long total : new long[]{0, 1, 315, 352, 353, 1395, 1507, 1508, 1628, 50_000}) {
            XpMath.LevelState levelState = XpMath.fromTotal(total);
            assertEquals(total, XpMath.balance(levelState.level(), levelState.progress()), "total " + total);
        }
        assertEquals(15, XpMath.fromTotal(315).level());
        assertEquals(16, XpMath.fromTotal(352).level());
        assertEquals(30, XpMath.fromTotal(1395).level());
        assertEquals(31, XpMath.fromTotal(1507).level());
    }

    @Test
    void balanceRoundsBarDownSoNoFreeXp() {
        float almost = 0.99999f;
        assertTrue(XpMath.balance(0, almost) <= 6);
        assertEquals(0, XpMath.balance(0, 0f));
    }

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
    void levelZeroCannotBuy() {
        PlayerSkillState state = new PlayerSkillState();
        assertFalse(ProgressionService.purchasePoint(state, XpMath.balance(0, 0f), ProgressionService.Settings.DEFAULT).ok());
    }

    @Test
    void consecutivePurchasesFollowTheCurveFromAHighBalance() {
        PlayerSkillState state = new PlayerSkillState();
        long xp = XpMath.totalForLevel(60);
        long spent = 0;
        for (int i = 0; i < 10; i++) {
            ProgressionService.PurchaseResult result = ProgressionService.purchasePoint(state, xp, ProgressionService.Settings.DEFAULT);
            assertTrue(result.ok());
            assertEquals(XpCurve.DEFAULT.cost(i), result.cost());
            spent += result.cost();
            xp = result.newXpTotal();
        }
        assertEquals(XpMath.totalForLevel(60) - spent, xp);
        assertEquals(10, state.purchasedPoints());
        assertEquals(500, XpCurve.DEFAULT.cost(state.purchasedPoints()));
    }

    @Test
    void pointLimitIsEnforced() {
        PlayerSkillState state = new PlayerSkillState();
        ProgressionService.Settings two = new ProgressionService.Settings(true, 2, XpCurve.DEFAULT);
        assertTrue(ProgressionService.purchasePoint(state, 10_000, two).ok());
        assertTrue(ProgressionService.purchasePoint(state, 10_000, two).ok());
        assertEquals(RejectReason.POINT_LIMIT_REACHED, ProgressionService.purchasePoint(state, 10_000, two).reason().orElseThrow());
        ProgressionService.Settings off = new ProgressionService.Settings(false, 120, XpCurve.DEFAULT);
        assertEquals(RejectReason.PROGRESSION_DISABLED, ProgressionService.purchasePoint(new PlayerSkillState(), 10_000, off).reason().orElseThrow());
    }

    @Test
    void totalCostSumsTheCurveAndAffordableCountStopsAtTheBalance() {
        XpCurve xpCurve = XpCurve.DEFAULT;
        assertEquals(0, xpCurve.totalCost(0, 0));
        assertEquals(100 + 122 + 148, xpCurve.totalCost(0, 3));
        assertEquals(xpCurve.cost(5) + xpCurve.cost(6), xpCurve.totalCost(5, 2));
        assertEquals(3, xpCurve.affordableCount(0, 370, 120));
        assertEquals(2, xpCurve.affordableCount(0, 369, 120));
        assertEquals(1, xpCurve.affordableCount(0, 10_000, 1));
        assertEquals(0, xpCurve.affordableCount(0, 99, 120));
    }

    @Test
    void bulkPurchaseBuysWhatTheBalanceAllowsAndChargesExactly() {
        PlayerSkillState state = new PlayerSkillState();
        ProgressionService.BulkPurchaseResult partial = ProgressionService.purchasePoints(state, 300, ProgressionService.Settings.DEFAULT, 5);
        assertTrue(partial.ok());
        assertEquals(2, partial.bought());
        assertEquals(222, partial.totalCost());
        assertEquals(78, partial.newXpTotal());
        assertEquals(2, state.purchasedPoints());
        assertTrue(state.ledgerHolds());
        ProgressionService.BulkPurchaseResult none = ProgressionService.purchasePoints(state, 78, ProgressionService.Settings.DEFAULT, 5);
        assertFalse(none.ok());
        assertEquals(RejectReason.INSUFFICIENT_XP, none.reason().orElseThrow());
        assertEquals(78, none.newXpTotal());
        assertEquals(2, state.purchasedPoints());
    }

    @Test
    void bulkPurchaseStopsAtThePointLimit() {
        PlayerSkillState state = new PlayerSkillState();
        ProgressionService.Settings two = new ProgressionService.Settings(true, 2, XpCurve.DEFAULT);
        ProgressionService.BulkPurchaseResult result = ProgressionService.purchasePoints(state, 1_000_000, two, 50);
        assertEquals(2, result.bought());
        assertEquals(222, result.totalCost());
        assertEquals(RejectReason.POINT_LIMIT_REACHED,
                ProgressionService.purchasePoints(state, 1_000_000, two, 50).reason().orElseThrow());
    }

    @Test
    void skyEventsAreScheduledStrictlyInTheFuture() {
        assertTrue(SkyEventSchedule.needsScheduling(SkyEventSchedule.UNSCHEDULED, 0));
        assertTrue(SkyEventSchedule.needsScheduling(4, 5));
        assertFalse(SkyEventSchedule.needsScheduling(5, 5));
        assertEquals(6, SkyEventSchedule.nextAfter(5, 20, 0.0));
        assertEquals(44, SkyEventSchedule.nextAfter(5, 20, 1.0));
        assertEquals(6, SkyEventSchedule.nextAfter(5, 1, 0.9));
        for (int i = 0; i <= 100; i++) {
            long next = SkyEventSchedule.nextAfter(10, 20, i / 100.0);
            assertTrue(next > 10 && next <= 49, "next " + next);
        }
    }

    @Test
    void theClockDecidesNightRegardlessOfWeather() {
        assertEquals(0, WorldClock.day(23_999));
        assertEquals(3, WorldClock.day(3 * 24_000L + 6_000));
        assertTrue(WorldClock.isDaylight(0) && WorldClock.isDaylight(12_541));
        assertFalse(WorldClock.isDaylight(12_542) || WorldClock.isDaylight(23_999));
        assertTrue(WorldClock.isNight(12_542) && WorldClock.isNight(5 * 24_000L + 23_459));
        assertFalse(WorldClock.isNight(6_000) || WorldClock.isNight(23_460));
    }

    @Test
    void anEclipseLastsTheDaylightOfItsDayAndAShowerItsNight() {
        long day7 = 7 * 24_000L;
        assertEquals(SkyEvent.ECLIPSE, SkyEventSchedule.current(7, 7, day7));
        assertEquals(SkyEvent.ECLIPSE, SkyEventSchedule.current(7, 9, day7 + 12_541));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(7, 9, day7 + 12_542));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(7, 9, day7 + 23_999));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(7, 9, day7 + 24_000));
        assertEquals(SkyEvent.STAR_SHOWER, SkyEventSchedule.current(7, 7, day7 + 12_542));
        assertEquals(SkyEvent.STAR_SHOWER, SkyEventSchedule.current(9, 7, day7 + 23_459));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(9, 7, day7 + 23_460));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(9, 7, day7 + 6_000));
        assertEquals(SkyEvent.NONE, SkyEventSchedule.current(SkyEventSchedule.UNSCHEDULED, SkyEventSchedule.UNSCHEDULED, day7));
    }

    @Test
    void respecDoesNotRestartTheCurve() {
        PlayerSkillState state = new PlayerSkillState();
        for (int i = 0; i < 3; i++) {
            ProgressionService.purchasePoint(state, 100_000, ProgressionService.Settings.DEFAULT);
        }
        RespecService.resetAll(state);
        RespecService.resetAll(state);
        assertEquals(3, state.purchasedPoints());
        assertEquals(XpCurve.DEFAULT.cost(3), XpCurve.DEFAULT.cost(state.purchasedPoints()));
    }
}
