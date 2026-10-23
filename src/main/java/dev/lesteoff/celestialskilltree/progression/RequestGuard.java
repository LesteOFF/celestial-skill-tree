package dev.lesteoff.celestialskilltree.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RequestGuard {
    public static final int CAPACITY = 64;

    private final Map<UUID, RejectReason> recent = new LinkedHashMap<>(CAPACITY, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, RejectReason> e) {
            return size() > CAPACITY;
        }
    };

    public Optional<RejectReason> admit(UUID requestId, long expectedStateRevision, long stateRevision,
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
