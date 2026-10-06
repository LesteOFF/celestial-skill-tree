package dev.nostalgiacraft.skills.progression;

import java.util.List;

public final class RespecService {
    public record Preview(int refund, List<String> removed) {
        public boolean isEmpty() {
            return removed.isEmpty();
        }
    }

    private RespecService() {
    }

    public static Preview preview(PlayerSkillState state) {
        return new Preview(state.activeSpentPoints(), state.orderedAllocations().stream().map(Allocation::nodeId).toList());
    }

    public static Preview resetAll(PlayerSkillState state) {
        Preview removal = preview(state);
        for (String id : removal.removed()) {
            state.refund(id);
        }
        state.setStartingRoot(null);
        state.bumpRevision();
        state.log("reset refunded " + removal.refund() + " point(s), removed " + removal.removed().size() + " node(s)");
        return removal;
    }
}
