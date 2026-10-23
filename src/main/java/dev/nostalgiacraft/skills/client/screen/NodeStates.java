package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.Holdings;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RejectReason;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class NodeStates {
    static final NodeStates EMPTY = new NodeStates(Map.of(), 0);

    private final Map<String, NodeState> states;
    private final int pendingCost;

    private NodeStates(Map<String, NodeState> states, int pendingCost) {
        this.states = states;
        this.pendingCost = pendingCost;
    }

    static NodeStates compute(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> pending) {
        AllocationService.Expansion expansion = AllocationService.expand(graph, state, pending);
        Holdings holdings = AllocationService.holdingsAfter(graph, state, expansion);
        boolean startChosen = state.startingRoot().isPresent() || expansion.choosesStartingRoot();
        Map<String, NodeState> states = new HashMap<>();
        Set<String> marked = new HashSet<>(pending);
        for (SkillNodeDefinition node : graph.nodes().values()) {
            states.put(node.id(), stateOf(graph, state, marked, holdings, startChosen, node));
        }
        return new NodeStates(states, expansion.totalCost() + AllocationService.rankUpCost(graph, state, pending));
    }

    private static NodeState stateOf(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> pending,
                                     Holdings holdings, boolean startChosen, SkillNodeDefinition node) {
        if (state.isAllocated(node.id())) {
            return NodeState.ALLOCATED;
        }
        if (pending.contains(node.id()) || holdings.contains(node.id())) {
            return NodeState.PENDING;
        }
        if (!holdings.hasDiscovered(node)) {
            return NodeState.HIDDEN;
        }
        if (!startChosen) {
            return node.isRoot() ? NodeState.AVAILABLE : NodeState.LOCKED;
        }
        return AllocationService.rejectionOf(graph, node, holdings).map(why -> blockedState(why.reason())).orElse(NodeState.AVAILABLE);
    }

    private static NodeState blockedState(RejectReason reason) {
        return switch (reason) {
            case EXCLUSIVE_CONFLICT -> NodeState.CONFLICTING;
            case CONSUMED -> NodeState.SEALED;
            default -> NodeState.LOCKED;
        };
    }

    NodeState of(String nodeId) {
        return states.getOrDefault(nodeId, NodeState.LOCKED);
    }

    int pendingCost() {
        return pendingCost;
    }
}
