package dev.nostalgiacraft.skills.progression;

import java.util.Optional;

/** Buying skill points with raw vanilla XP. Pure logic; the game layer applies the returned XP total. */
public final class ProgressionService {
    public record Settings(boolean xpPurchaseEnabled, int pointLimit, XpCurve curve) {
        public static final Settings DEFAULT = new Settings(true, 120, XpCurve.DEFAULT);
    }

    /** On success {@code newXpTotal} is the exact raw XP the player must be left with. */
    public record PurchaseResult(boolean ok, Optional<RejectReason> reason, int cost, long xpBefore, long newXpTotal) {
    }

    private ProgressionService() {
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
