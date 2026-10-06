package dev.nostalgiacraft.skills.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player duplicate protection and revision checks for mutating requests. Remembers the last
 * {@link #CAPACITY} request ids (and their outcome) so a replayed packet can never be applied twice.
 */
public final class RequestGuard {
    public static final int CAPACITY = 64;

    private final Map<UUID, RejectReason> recent = new LinkedHashMap<>(CAPACITY, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, RejectReason> e) {
            return size() > CAPACITY;
        }
    };

    /** Returns a rejection if the request must not run; otherwise records it as in-flight/processed. */
    public synchronized Optional<RejectReason> admit(UUID requestId, long expectedStateRevision, long stateRevision,
                                                     long expectedGraphRevision, long graphRevision,
                                                     boolean graphDependent) {
        if (recent.containsKey(requestId)) {
            return Optional.of(RejectReason.DUPLICATE_REQUEST);
        }
        recent.put(requestId, null);
        if (expectedStateRevision != stateRevision) {
            return Optional.of(RejectReason.STALE_STATE_REVISION);
        }
        if (graphDependent && expectedGraphRevision != graphRevision) {
            return Optional.of(RejectReason.STALE_GRAPH_REVISION);
        }
        return Optional.empty();
    }
}
