package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;

import java.util.List;
import java.util.Optional;

public final class BuildSwapService {
    public record Build(Optional<String> startingRoot, List<String> nodes) {
        public static final Build EMPTY = new Build(Optional.empty(), List.of());

        public static Build of(PlayerSkillState state) {
            Optional<String> root = state.startingRoot();
            List<String> nodes = state.orderedAllocations().stream().map(Allocation::nodeId)
                    .filter(id -> !root.map(id::equals).orElse(false)).toList();
            return new Build(root, nodes);
        }

        public int size() {
            return nodes.size() + (startingRoot.isPresent() ? 1 : 0);
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
