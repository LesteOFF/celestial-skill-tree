package dev.lesteoff.celestialskilltree.progression;

import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BuildSwapService {
    public record Build(Optional<String> startingRoot, List<String> nodes, Set<String> disabled) {
        public static final Build EMPTY = new Build(Optional.empty(), List.of(), Set.of());

        public static Build of(PlayerSkillState state) {
            Optional<String> root = state.startingRoot();
            List<String> nodes = state.orderedAllocations().stream()
                    .filter(allocation -> !root.map(allocation.nodeId()::equals).orElse(false))
                    .flatMap(allocation -> Collections.nCopies(allocation.rank(), allocation.nodeId()).stream()).toList();
            return new Build(root, nodes, Set.copyOf(state.disabled()));
        }

        public List<String> learned() {
            return nodes.stream().distinct().toList();
        }

        public int size() {
            return learned().size() + (startingRoot.isPresent() ? 1 : 0);
        }
    }

    public record Result(Build previous, Optional<AllocationService.Rejection> rejection) {
        public boolean ok() {
            return rejection.isEmpty();
        }
    }

    private BuildSwapService() {
    }

    public static Result swap(SkillGraphSnapshot graph, PlayerSkillState state, Build incoming) {
        Build previous = Build.of(state);
        Optional<AllocationService.Rejection> problem = replace(graph, state.copy(), incoming);
        if (problem.isEmpty()) {
            replace(graph, state, incoming);
            incoming.disabled().stream().filter(id -> ToggleService.rejection(graph, state, id).isEmpty())
                    .forEach(id -> state.setDisabled(id, true));
        }
        return new Result(previous, problem);
    }

    private static Optional<AllocationService.Rejection> replace(SkillGraphSnapshot graph, PlayerSkillState state, Build incoming) {
        RespecService.resetAll(state);
        Optional<AllocationService.Rejection> problem = incoming.startingRoot()
                .flatMap(root -> AllocationService.confirm(graph, state, List.of(root)).rejection());
        for (int from = 0; from < incoming.nodes().size() && problem.isEmpty(); from += AllocationService.MAX_BATCH) {
            int to = Math.min(incoming.nodes().size(), from + AllocationService.MAX_BATCH);
            problem = AllocationService.confirm(graph, state, incoming.nodes().subList(from, to)).rejection();
        }
        return problem;
    }
}
