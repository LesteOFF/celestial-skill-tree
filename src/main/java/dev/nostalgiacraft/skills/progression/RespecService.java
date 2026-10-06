package dev.nostalgiacraft.skills.progression;

import java.util.List;

/**
 * Full reset (Orb of Reconfiguration or /nostalgiaskills reset): removes every allocation, refunds exactly the
 * recorded paid costs, clears the starting root so one new free root can be chosen, and keeps lifetime purchase
 * counters (the XP curve never restarts).
 */
public final class RespecService {
    public record Preview(int refund, List<String> removed) {
        public boolean isEmpty() {
            return removed.isEmpty();
        }
    }

    private RespecService() {
    }

    public static Preview preview(PlayerSkillState s) {
        return new Preview(s.activeSpentPoints(), s.orderedAllocations().stream().map(Allocation::nodeId).toList());
    }

    public static Preview resetAll(PlayerSkillState s) {
        Preview p = preview(s);
        for (String id : p.removed()) {
            s.refund(id);
        }
        s.setStartingRoot(null);
        s.bumpRevision();
        s.log("reset refunded " + p.refund() + " point(s), removed " + p.removed().size() + " node(s)");
        return p;
    }
}
