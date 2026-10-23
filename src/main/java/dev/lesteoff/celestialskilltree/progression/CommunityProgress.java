package dev.lesteoff.celestialskilltree.progression;

import java.util.List;
import java.util.Optional;

public final class CommunityProgress {
    public record Donation(long accepted, Optional<RejectReason> reason) {
        public boolean ok() {
            return accepted > 0;
        }

        static Donation rejected(RejectReason reason) {
            return new Donation(0, Optional.of(reason));
        }
    }

    private CommunityProgress() {
    }

    public static long goal(List<Long> tierThresholds) {
        return tierThresholds.stream().mapToLong(Long::longValue).max().orElse(0);
    }

    public static int unlockedCount(List<Long> tierThresholds, long communityXp) {
        return (int) tierThresholds.stream().filter(threshold -> communityXp >= threshold).count();
    }

    public static Optional<Long> nextThreshold(List<Long> tierThresholds, long communityXp) {
        return tierThresholds.stream().filter(threshold -> communityXp < threshold).min(Long::compare);
    }

    public static double pathFraction(List<Long> sortedThresholds, long communityXp) {
        long previous = 0;
        for (int index = 0; index < sortedThresholds.size(); index++) {
            long threshold = sortedThresholds.get(index);
            if (communityXp < threshold) {
                double withinSegment = (communityXp - previous) / (double) Math.max(1, threshold - previous);
                return (index + Math.max(0, withinSegment)) / sortedThresholds.size();
            }
            previous = threshold;
        }
        return sortedThresholds.isEmpty() ? 0 : 1;
    }

    public static Donation donate(List<Long> tierThresholds, long communityXp, long xpBalance, long requested) {
        if (requested <= 0) {
            return Donation.rejected(RejectReason.INVALID_AMOUNT);
        }
        long remaining = goal(tierThresholds) - communityXp;
        if (remaining <= 0) {
            return Donation.rejected(RejectReason.COMMUNITY_COMPLETE);
        }
        if (xpBalance <= 0) {
            return Donation.rejected(RejectReason.INSUFFICIENT_XP);
        }
        return new Donation(Math.min(requested, Math.min(xpBalance, remaining)), Optional.empty());
    }
}
