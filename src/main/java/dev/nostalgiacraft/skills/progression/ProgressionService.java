package dev.nostalgiacraft.skills.progression;

import java.util.Optional;

public final class ProgressionService {
    public record Settings(boolean xpPurchaseEnabled, int pointLimit, XpCurve curve) {
        public static final Settings DEFAULT = new Settings(true, 120, XpCurve.DEFAULT);
    }

    public record PurchaseResult(boolean ok, Optional<RejectReason> reason, int cost, long xpBefore, long newXpTotal) {
    }

    public record BulkPurchaseResult(int bought, Optional<RejectReason> reason, long totalCost, long newXpTotal) {
        public boolean ok() {
            return bought > 0;
        }
    }

    private ProgressionService() {
    }

    public static BulkPurchaseResult purchasePoints(PlayerSkillState state, long xpBalance, Settings settings, int count) {
        int bought = 0;
        long balance = xpBalance;
        Optional<RejectReason> reason = Optional.empty();
        while (bought < count && reason.isEmpty()) {
            PurchaseResult result = purchasePoint(state, balance, settings);
            reason = result.reason();
            if (result.ok()) {
                bought++;
                balance = result.newXpTotal();
            }
        }
        return new BulkPurchaseResult(bought, bought > 0 ? Optional.empty() : reason, xpBalance - balance, balance);
    }

    public static PurchaseResult purchasePoint(PlayerSkillState state, long xpBalance, Settings settings) {
        int cost = settings.curve().cost(state.purchasedPoints());
        if (!settings.xpPurchaseEnabled()) {
            return new PurchaseResult(false, Optional.of(RejectReason.PROGRESSION_DISABLED), cost, xpBalance, xpBalance);
        }
        if (state.purchasedPoints() >= settings.pointLimit()) {
            return new PurchaseResult(false, Optional.of(RejectReason.POINT_LIMIT_REACHED), cost, xpBalance, xpBalance);
        }
        if (xpBalance < cost) {
            return new PurchaseResult(false, Optional.of(RejectReason.INSUFFICIENT_XP), cost, xpBalance, xpBalance);
        }
        state.addPurchasedPoint();
        state.bumpRevision();
        state.log("bought point #" + state.purchasedPoints() + " for " + cost + " xp");
        return new PurchaseResult(true, Optional.empty(), cost, xpBalance, xpBalance - cost);
    }
}
