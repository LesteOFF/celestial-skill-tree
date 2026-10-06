package dev.nostalgiacraft.skills.progression;

import java.util.Optional;

/** Administrative point operations. None of them touch the lifetime purchase counter or the XP curve. */
public final class LedgerService {
    public record Result(boolean ok, Optional<RejectReason> reason, int available) {
    }

    private LedgerService() {
    }

    /** Grants (or with a negative amount, removes) available points as a net administrative grant. */
    public static Result grant(PlayerSkillState s, int amount, int pointLimit, boolean override) {
        if (amount == 0 || s.availablePoints() + amount < 0) {
            return new Result(false, Optional.of(RejectReason.INVALID_AMOUNT), s.availablePoints());
        }
        if (amount > 0 && s.totalPoints() + amount > pointLimit && !override) {
            return new Result(false, Optional.of(RejectReason.OVER_LIMIT_NEEDS_OVERRIDE), s.availablePoints());
        }
        s.adjustGranted(amount);
        s.bumpRevision();
        s.log("admin grant " + amount + (override ? " (limit override)" : ""));
        return new Result(true, Optional.empty(), s.availablePoints());
    }

    /** Sets the available balance; the difference is recorded as a net grant so active purchases stay intact. */
    public static Result setAvailable(PlayerSkillState s, int available, int pointLimit, boolean override) {
        if (available < 0) {
            return new Result(false, Optional.of(RejectReason.INVALID_AMOUNT), s.availablePoints());
        }
        int delta = available - s.availablePoints();
        if (delta == 0) {
            return new Result(true, Optional.empty(), available);
        }
        if (delta > 0 && s.totalPoints() + delta > pointLimit && !override) {
            return new Result(false, Optional.of(RejectReason.OVER_LIMIT_NEEDS_OVERRIDE), s.availablePoints());
        }
        s.adjustGranted(delta);
        s.bumpRevision();
        s.log("admin set available " + available + (override ? " (limit override)" : ""));
        return new Result(true, Optional.empty(), s.availablePoints());
    }
}
