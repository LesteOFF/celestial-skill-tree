package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;

import java.util.Optional;

public final class ToggleService {
    private ToggleService() {
    }

    public static Optional<RejectReason> rejection(SkillGraphSnapshot graph, PlayerSkillState state, String nodeId) {
        Optional<SkillNodeDefinition> node = graph.node(nodeId);
        if (node.isEmpty()) {
            return Optional.of(RejectReason.UNKNOWN_NODE);
        }
        if (!state.isAllocated(nodeId)) {
            return Optional.of(RejectReason.NOT_ALLOCATED);
        }
        return node.get().traits().toggleable() ? Optional.empty() : Optional.of(RejectReason.NOT_TOGGLEABLE);
    }

    public static Optional<RejectReason> toggle(SkillGraphSnapshot graph, PlayerSkillState state, String nodeId) {
        Optional<RejectReason> rejection = rejection(graph, state, nodeId);
        if (rejection.isEmpty()) {
            state.setDisabled(nodeId, !state.isDisabled(nodeId));
            state.bumpRevision();
            state.log((state.isDisabled(nodeId) ? "disabled " : "enabled ") + nodeId);
        }
        return rejection;
    }
}
