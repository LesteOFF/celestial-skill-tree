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
        int first = state.purchasedPoints();
        int bought = 0;
        long balance = xpBalance;
        Optional<RejectReason> reason = Optional.empty();
        while (bought < count && reason.isEmpty()) {
            reason = rejection(first + bought, balance, settings);
            if (reason.isEmpty()) {
                balance -= settings.curve().cost(first + bought);
                bought++;
            }
        }
        if (bought > 0) {
            record(state, bought, xpBalance - balance);
        }
        return new BulkPurchaseResult(bought, bought > 0 ? Optional.empty() : reason, xpBalance - balance, balance);
    }

    public static PurchaseResult purchasePoint(PlayerSkillState state, long xpBalance, Settings settings) {
        int cost = settings.curve().cost(state.purchasedPoints());
        Optional<RejectReason> reason = rejection(state.purchasedPoints(), xpBalance, settings);
        if (reason.isPresent()) {
            return new PurchaseResult(false, reason, cost, xpBalance, xpBalance);
        }
        record(state, 1, cost);
        return new PurchaseResult(true, Optional.empty(), cost, xpBalance, xpBalance - cost);
    }

    private static Optional<RejectReason> rejection(int purchased, long xpBalance, Settings settings) {
        if (!settings.xpPurchaseEnabled()) {
            return Optional.of(RejectReason.PROGRESSION_DISABLED);
        }
        if (purchased >= settings.pointLimit()) {
            return Optional.of(RejectReason.POINT_LIMIT_REACHED);
        }
        return xpBalance < settings.curve().cost(purchased) ? Optional.of(RejectReason.INSUFFICIENT_XP) : Optional.empty();
    }

    private static void record(PlayerSkillState state, int points, long cost) {
        state.addPurchasedPoints(points);
        state.bumpRevision();
        state.log(points == 1 ? "bought point #" + state.purchasedPoints() + " for " + cost + " xp"
                : "bought points #" + (state.purchasedPoints() - points + 1) + "-" + state.purchasedPoints() + " for " + cost + " xp");
    }
}
