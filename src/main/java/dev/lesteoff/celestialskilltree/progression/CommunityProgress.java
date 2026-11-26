package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.CommunityTier;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CommunityProgress {
    public static final UUID ANONYMOUS = new UUID(0, 0);

    public enum StarStatus {
        LIT, OPEN, LOCKED, EXCLUDED;

        public static StarStatus byId(int id) {
            return values()[Math.floorMod(id, values().length)];
        }
    }

    public record Donation(long accepted, Optional<RejectReason> reason) {
        public boolean ok() {
            return accepted > 0;
        }

        static Donation rejected(RejectReason reason) {
            return new Donation(0, Optional.of(reason));
        }
    }

    public record Poured(Map<String, Long> progress, Map<String, Long> given) {
        public long accepted() {
            return given.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    public record Patron(String name, Map<String, Long> stars) {
        public long total() {
            return stars.values().stream().mapToLong(Long::longValue).sum();
        }

        public long gaveTo(String starId) {
            return stars.getOrDefault(starId, 0L);
        }

        public Patron credited(String newName, String starId, long xp) {
            Map<String, Long> updated = new HashMap<>(stars);
            updated.merge(starId, xp, Long::sum);
            return new Patron(newName, Map.copyOf(updated));
        }
    }

    private CommunityProgress() {
    }

    public static Optional<CommunityTier> find(List<CommunityTier> stars, String starId) {
        return stars.stream().filter(star -> star.id().equals(starId)).findFirst();
    }

    public static long donated(CommunityTier star, Map<String, Long> progress) {
        return progress.getOrDefault(star.id(), 0L);
    }

    public static long missing(CommunityTier star, Map<String, Long> progress) {
        return Math.max(0, star.xpRequired() - donated(star, progress));
    }

    public static boolean isLit(CommunityTier star, Map<String, Long> progress) {
        return missing(star, progress) == 0;
    }

    public static Set<String> lit(List<CommunityTier> stars, Map<String, Long> progress) {
        return stars.stream().filter(star -> isLit(star, progress)).map(CommunityTier::id).collect(Collectors.toUnmodifiableSet());
    }

    public static StarStatus status(List<CommunityTier> stars, Map<String, Long> progress, CommunityTier star) {
        if (isLit(star, progress)) {
            return StarStatus.LIT;
        }
        if (stars.stream().anyMatch(other -> star.rivals(other) && isLit(other, progress))) {
            return StarStatus.EXCLUDED;
        }
        boolean parentLit = star.parent().map(parentId -> find(stars, parentId).filter(parent -> isLit(parent, progress)).isPresent())
                .orElse(true);
        return parentLit ? StarStatus.OPEN : StarStatus.LOCKED;
    }

    public static Donation donate(List<CommunityTier> stars, Map<String, Long> progress, String starId, long xpBalance,
                                  long requested) {
        if (requested <= 0) {
            return Donation.rejected(RejectReason.INVALID_AMOUNT);
        }
        Optional<CommunityTier> star = find(stars, starId);
        if (star.isEmpty()) {
            return Donation.rejected(RejectReason.STAR_LOCKED);
        }
        Optional<RejectReason> closed = switch (status(stars, progress, star.get())) {
            case LIT -> Optional.of(RejectReason.STAR_LIT);
            case EXCLUDED -> Optional.of(RejectReason.STAR_EXCLUDED);
            case LOCKED -> Optional.of(RejectReason.STAR_LOCKED);
            case OPEN -> xpBalance <= 0 ? Optional.of(RejectReason.INSUFFICIENT_XP) : Optional.empty();
        };
        if (closed.isPresent()) {
            return Donation.rejected(closed.get());
        }
        return new Donation(Math.min(requested, Math.min(xpBalance, missing(star.get(), progress))), Optional.empty());
    }

    public static Optional<CommunityTier> autoTarget(List<CommunityTier> stars, Map<String, Long> progress) {
        return stars.stream().filter(star -> star.exclusive().isEmpty() && status(stars, progress, star) == StarStatus.OPEN)
                .findFirst();
    }

    public static Poured pour(List<CommunityTier> stars, Map<String, Long> progress, long xp) {
        Map<String, Long> poured = new HashMap<>(progress);
        Map<String, Long> given = new LinkedHashMap<>();
        long left = Math.max(0, xp);
        Optional<CommunityTier> target = autoTarget(stars, poured);
        while (left > 0 && target.isPresent()) {
            long amount = Math.min(left, missing(target.get(), poured));
            poured.merge(target.get().id(), amount, Long::sum);
            given.merge(target.get().id(), amount, Long::sum);
            left -= amount;
            target = autoTarget(stars, poured);
        }
        return new Poured(Map.copyOf(poured), given);
    }

    public static List<UUID> ranking(Map<UUID, Patron> patrons) {
        return patrons.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(ANONYMOUS) && entry.getValue().total() > 0)
                .sorted(Comparator.comparingLong((Map.Entry<UUID, Patron> entry) -> entry.getValue().total()).reversed()
                        .thenComparing(entry -> entry.getValue().name()))
                .map(Map.Entry::getKey).toList();
    }

    public static List<Patron> topDonors(Collection<Patron> patrons, String starId, int limit) {
        return patrons.stream().filter(patron -> patron.gaveTo(starId) > 0)
                .sorted(Comparator.comparingLong((Patron patron) -> patron.gaveTo(starId)).reversed())
                .limit(limit).toList();
    }
}
