package dev.nostalgiacraft.skills.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTest {
    @Test
    void defaultCurveMatchesDesign() {
        XpCurve c = XpCurve.DEFAULT;
        assertEquals(100, c.cost(0));
        assertEquals(122, c.cost(1));
        assertEquals(500, c.cost(10));
        for (int p = 0; p < 200; p++) {
            assertTrue(c.cost(p + 1) >= c.cost(p), "curve must never decrease");
        }
    }

    @Test
    void discountNeverProducesFreeOrNegativeCosts() {
        XpCurve c = new XpCurve(1, 0, 0, 0.9);
        assertEquals(1, c.cost(0));
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
            XpMath.LevelState st = XpMath.fromTotal(total);
            assertEquals(total, XpMath.balance(st.level(), st.progress()), "total " + total);
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
        PlayerSkillState s = new PlayerSkillState();
        ProgressionService.PurchaseResult shortBy1 = ProgressionService.purchasePoint(s, 99, ProgressionService.Settings.DEFAULT);
        assertFalse(shortBy1.ok());
        assertEquals(RejectReason.INSUFFICIENT_XP, shortBy1.reason().orElseThrow());
        assertEquals(0, s.purchasedPoints());
        ProgressionService.PurchaseResult exact = ProgressionService.purchasePoint(s, 100, ProgressionService.Settings.DEFAULT);
        assertTrue(exact.ok());
        assertEquals(0, exact.newXpTotal());
        assertEquals(1, s.availablePoints());
        assertTrue(s.ledgerHolds());
    }

    @Test
    void levelZeroCannotBuy() {
        PlayerSkillState s = new PlayerSkillState();
        assertFalse(ProgressionService.purchasePoint(s, XpMath.balance(0, 0f), ProgressionService.Settings.DEFAULT).ok());
    }

    @Test
    void consecutivePurchasesFollowTheCurveFromAHighBalance() {
        PlayerSkillState s = new PlayerSkillState();
        long xp = XpMath.totalForLevel(60);
        long spent = 0;
        for (int i = 0; i < 10; i++) {
            ProgressionService.PurchaseResult r = ProgressionService.purchasePoint(s, xp, ProgressionService.Settings.DEFAULT);
            assertTrue(r.ok());
            assertEquals(XpCurve.DEFAULT.cost(i), r.cost());
            spent += r.cost();
            xp = r.newXpTotal();
        }
        assertEquals(XpMath.totalForLevel(60) - spent, xp);
        assertEquals(10, s.purchasedPoints());
        assertEquals(500, XpCurve.DEFAULT.cost(s.purchasedPoints()));
    }

    @Test
    void pointLimitIsEnforced() {
        PlayerSkillState s = new PlayerSkillState();
        ProgressionService.Settings two = new ProgressionService.Settings(true, 2, XpCurve.DEFAULT);
        assertTrue(ProgressionService.purchasePoint(s, 10_000, two).ok());
        assertTrue(ProgressionService.purchasePoint(s, 10_000, two).ok());
        assertEquals(RejectReason.POINT_LIMIT_REACHED, ProgressionService.purchasePoint(s, 10_000, two).reason().orElseThrow());
        ProgressionService.Settings off = new ProgressionService.Settings(false, 120, XpCurve.DEFAULT);
        assertEquals(RejectReason.PROGRESSION_DISABLED, ProgressionService.purchasePoint(new PlayerSkillState(), 10_000, off).reason().orElseThrow());
    }

    @Test
    void respecDoesNotRestartTheCurve() {
        PlayerSkillState s = new PlayerSkillState();
        for (int i = 0; i < 3; i++) {
            ProgressionService.purchasePoint(s, 100_000, ProgressionService.Settings.DEFAULT);
        }
        RespecService.resetAll(s);
        RespecService.resetAll(s);
        assertEquals(3, s.purchasedPoints());
        assertEquals(XpCurve.DEFAULT.cost(3), XpCurve.DEFAULT.cost(s.purchasedPoints()));
    }
}
