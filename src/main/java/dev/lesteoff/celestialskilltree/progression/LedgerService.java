package dev.lesteoff.celestialskilltree.progression;

import java.util.Optional;

public final class LedgerService {
    public record Result(boolean ok, Optional<RejectReason> reason, int available) {
    }

    private LedgerService() {
    }

    public static Result grant(PlayerSkillState state, int amount, int pointLimit, boolean override) {
        if (amount == 0 || state.availablePoints() + amount < 0) {
            return new Result(false, Optional.of(RejectReason.INVALID_AMOUNT), state.availablePoints());
        }
        if (amount > 0 && state.totalPoints() + amount > pointLimit && !override) {
            return new Result(false, Optional.of(RejectReason.OVER_LIMIT_NEEDS_OVERRIDE), state.availablePoints());
        }
        state.adjustGranted(amount);
        state.bumpRevision();
        state.log("admin grant " + amount + (override ? " (limit override)" : ""));
        return new Result(true, Optional.empty(), state.availablePoints());
    }

    public static Result setAvailable(PlayerSkillState state, int available, int pointLimit, boolean override) {
        if (available < 0) {
            return new Result(false, Optional.of(RejectReason.INVALID_AMOUNT), state.availablePoints());
        }
        int delta = available - state.availablePoints();
        if (delta == 0) {
            return new Result(true, Optional.empty(), available);
        }
        if (delta > 0 && state.totalPoints() + delta > pointLimit && !override) {
            return new Result(false, Optional.of(RejectReason.OVER_LIMIT_NEEDS_OVERRIDE), state.availablePoints());
        }
        state.adjustGranted(delta);
        state.bumpRevision();
        state.log("admin set available " + available + (override ? " (limit override)" : ""));
        return new Result(true, Optional.empty(), state.availablePoints());
    }
}
