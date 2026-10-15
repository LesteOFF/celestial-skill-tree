package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.ExclusiveGroup;
import dev.nostalgiacraft.skills.graph.RequirementCheck;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

public final class AllocationService {
    public static final int MAX_BATCH = 400;

    public record Accepted(String nodeId, int cost, boolean startingRoot) {
    }

    public record Rejection(RejectReason reason, String nodeId, String detail) {
    }

    public record Expansion(List<Accepted> accepted, Map<String, Rejection> rejected) {
        public int totalCost() {
            return accepted.stream().mapToInt(Accepted::cost).sum();
        }

        public boolean choosesStartingRoot() {
            return accepted.stream().anyMatch(Accepted::startingRoot);
        }
    }

    public record BatchResult(boolean ok, List<Accepted> accepted, int totalCost, Optional<Rejection> rejection) {
        public List<String> nodeIds() {
            return accepted.stream().map(Accepted::nodeId).toList();
        }

        static BatchResult reject(RejectReason reason, String nodeId, String detail) {
            return reject(new Rejection(reason, nodeId, detail));
        }

        static BatchResult reject(Rejection rejection) {
            return new BatchResult(false, List.of(), 0, Optional.of(rejection));
        }
    }

    private AllocationService() {
    }

    public static BatchResult validate(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> request) {
        Set<String> ids = new TreeSet<>(request);
        Optional<Rejection> malformed = malformedRequest(graph, state, request, ids);
        if (malformed.isPresent()) {
            return BatchResult.reject(malformed.get());
        }
        Expansion expansion = expand(graph, state, ids);
        if (!expansion.rejected().isEmpty()) {
            return BatchResult.reject(expansion.rejected().values().iterator().next());
        }
        List<Accepted> accepted = new ArrayList<>(expansion.accepted());
        extraRanks(state, request).forEach((id, ranks) -> {
            for (int rank = 0; rank < ranks; rank++) {
                accepted.add(new Accepted(id, graph.nodes().get(id).cost(), false));
            }
        });
        int total = accepted.stream().mapToInt(Accepted::cost).sum();
        if (total > state.availablePoints()) {
            return BatchResult.reject(RejectReason.INSUFFICIENT_POINTS, "", total + "/" + state.availablePoints());
        }
        return new BatchResult(true, List.copyOf(accepted), total, Optional.empty());
    }

    private static Optional<Rejection> malformedRequest(SkillGraphSnapshot graph, PlayerSkillState state,
                                                        Collection<String> request, Set<String> ids) {
        if (request.isEmpty()) {
            return Optional.of(new Rejection(RejectReason.EMPTY_REQUEST, "", ""));
        }
        if (request.size() > MAX_BATCH) {
            return Optional.of(new Rejection(RejectReason.REQUEST_TOO_LARGE, "", String.valueOf(request.size())));
        }
        for (String id : ids) {
            if (!graph.contains(id)) {
                return Optional.of(new Rejection(RejectReason.UNKNOWN_NODE, id, ""));
            }
        }
        for (Map.Entry<String, Integer> extra : extraRanks(state, request).entrySet()) {
            if (extra.getValue() > rankRoom(graph, state, extra.getKey())) {
                return Optional.of(new Rejection(RejectReason.ALREADY_ALLOCATED, extra.getKey(), ""));
            }
        }
        return state.startingRoot().isPresent() ? Optional.empty() : startingRootProblem(graph, ids);
    }

    private static Optional<Rejection> startingRootProblem(SkillGraphSnapshot graph, Set<String> ids) {
        long roots = ids.stream().filter(id -> graph.nodes().get(id).isRoot()).count();
        if (roots == 0) {
            return Optional.of(new Rejection(RejectReason.STARTING_ROOT_REQUIRED, ids.iterator().next(), ""));
        }
        if (roots > 1) {
            return Optional.of(new Rejection(RejectReason.MULTIPLE_STARTING_ROOTS, "", String.valueOf(roots)));
        }
        return Optional.empty();
    }

    public static BatchResult confirm(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> request) {
        BatchResult result = validate(graph, state, request);
        if (!result.ok()) {
            return result;
        }
        for (Accepted accepted : result.accepted()) {
            if (accepted.startingRoot()) {
                state.setStartingRoot(accepted.nodeId());
            }
            if (state.isAllocated(accepted.nodeId())) {
                state.rankUp(accepted.nodeId(), accepted.cost());
            } else {
                state.allocate(accepted.nodeId(), accepted.cost());
            }
        }
        state.bumpRevision();
        state.log("allocated " + result.accepted().size() + " node(s) for " + result.totalCost() + " point(s)");
        return result;
    }

    public static Expansion expand(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> request) {
        return new Placement(graph, state, request).placeAll();
    }

    public static boolean canRankUp(SkillGraphSnapshot graph, PlayerSkillState state, String id) {
        return state.isAllocated(id) && graph.node(id).filter(node -> state.rank(id) < node.maxRank()).isPresent();
    }

    public static Map<String, Integer> extraRanks(PlayerSkillState state, Collection<String> request) {
        Map<String, Integer> counts = new TreeMap<>();
        request.forEach(id -> counts.merge(id, 1, Integer::sum));
        Map<String, Integer> extra = new TreeMap<>();
        counts.forEach((id, count) -> {
            int ranks = state.isAllocated(id) ? count : count - 1;
            if (ranks > 0) {
                extra.put(id, ranks);
            }
        });
        return extra;
    }

    public static int rankRoom(SkillGraphSnapshot graph, PlayerSkillState state, String id) {
        int held = state.isAllocated(id) ? state.rank(id) : SkillNodeDefinition.SINGLE_RANK;
        return graph.node(id).map(node -> Math.max(0, node.maxRank() - held)).orElse(0);
    }

    public static int rankUpCost(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> request) {
        return extraRanks(state, request).entrySet().stream()
                .mapToInt(extra -> Math.min(extra.getValue(), rankRoom(graph, state, extra.getKey())) * graph.nodes().get(extra.getKey()).cost())
                .sum();
    }

    public static int priceOf(SkillGraphSnapshot graph, SkillNodeDefinition node, Predicate<String> held) {
        return graph.portalDestination(node.id()).filter(held).isPresent() ? 0 : node.cost();
    }

    public static Holdings holdingsAfter(SkillGraphSnapshot graph, PlayerSkillState state, Expansion expansion) {
        Holdings holdings = Holdings.of(graph, state);
        expansion.accepted().forEach(accepted -> holdings.add(accepted.nodeId(), accepted.cost()));
        return holdings;
    }

    public static Optional<Rejection> rejectionOf(SkillGraphSnapshot graph, SkillNodeDefinition node, Holdings holdings) {
        if (!holdings.hasDiscovered(node)) {
            return Optional.of(new Rejection(RejectReason.UNDISCOVERED, node.id(), node.traits().hiddenUntil().orElse("")));
        }
        Optional<String> sealer = holdings.amplifierSealing(node.id());
        if (sealer.isPresent()) {
            return Optional.of(new Rejection(RejectReason.CONSUMED, node.id(), sealer.get()));
        }
        Optional<ExclusiveGroup> group = graph.groupOf(node.id());
        if (group.isPresent()) {
            List<String> active = group.get().members().stream().filter(holdings::contains).toList();
            if (active.size() >= group.get().maxActive()) {
                return Optional.of(new Rejection(RejectReason.EXCLUSIVE_CONFLICT, node.id(), active.get(0)));
            }
        }
        if (!node.traits().standsAlone() && graph.accessFrom(node.id()).stream().noneMatch(holdings::contains)) {
            return Optional.of(new Rejection(RejectReason.NOT_CONNECTED, node.id(), ""));
        }
        String unmet = RequirementCheck.firstUnmet(graph, node, holdings.nodeIds(), holdings.pointsIn(node.sector()));
        return Optional.ofNullable(unmet).map(term -> new Rejection(RejectReason.REQUIREMENT_UNMET, node.id(), term));
    }

    public static Optional<Rejection> eligibility(SkillGraphSnapshot graph, PlayerSkillState state,
                                                  Collection<String> pending, String id) {
        SkillNodeDefinition node = graph.nodes().get(id);
        if (node == null) {
            return Optional.of(new Rejection(RejectReason.UNKNOWN_NODE, id, ""));
        }
        if (state.isAllocated(id)) {
            return Optional.of(new Rejection(RejectReason.ALREADY_ALLOCATED, id, ""));
        }
        Set<String> otherPending = new TreeSet<>(pending);
        otherPending.remove(id);
        Expansion expansion = expand(graph, state, otherPending);
        if (state.startingRoot().isEmpty() && !expansion.choosesStartingRoot()) {
            return node.isRoot() ? Optional.empty() : Optional.of(new Rejection(RejectReason.STARTING_ROOT_REQUIRED, id, ""));
        }
        return rejectionOf(graph, node, holdingsAfter(graph, state, expansion));
    }

    public static Set<String> dependentsOfPendingRemoval(SkillGraphSnapshot graph, PlayerSkillState state,
                                                        Collection<String> pending, String removed) {
        Set<String> rest = new TreeSet<>(pending);
        rest.remove(removed);
        return new TreeSet<>(expand(graph, state, rest).rejected().keySet());
    }

    private static final class Placement {
        private final SkillGraphSnapshot graph;
        private final Holdings holdings;
        private final Set<String> remaining;
        private final List<Accepted> accepted = new ArrayList<>();
        private boolean needsStartingRoot;

        Placement(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> request) {
            this.graph = graph;
            this.holdings = Holdings.of(graph, state);
            this.needsStartingRoot = state.startingRoot().isEmpty();
            this.remaining = new TreeSet<>(request);
            this.remaining.removeAll(holdings.nodeIds());
        }

        Expansion placeAll() {
            boolean progressed = true;
            while (progressed && !remaining.isEmpty()) {
                progressed = false;
                for (String id : List.copyOf(remaining)) {
                    progressed |= tryPlace(id);
                }
            }
            return new Expansion(List.copyOf(accepted), rejections());
        }

        private boolean tryPlace(String id) {
            SkillNodeDefinition node = graph.nodes().get(id);
            if (node == null || holdings.contains(id)) {
                return false;
            }
            boolean asStartingRoot = needsStartingRoot && node.isRoot();
            if (!asStartingRoot && rejectionOf(graph, node, holdings).isPresent()) {
                return false;
            }
            accept(id, asStartingRoot ? 0 : priceOf(graph, node, holdings::contains), asStartingRoot);
            graph.portalDestination(id).filter(twin -> !holdings.contains(twin)).ifPresent(twin -> accept(twin, 0, false));
            needsStartingRoot &= !asStartingRoot;
            return true;
        }

        private void accept(String id, int cost, boolean asStartingRoot) {
            accepted.add(new Accepted(id, cost, asStartingRoot));
            holdings.add(id, cost);
            remaining.remove(id);
        }

        private Map<String, Rejection> rejections() {
            Map<String, Rejection> rejected = new LinkedHashMap<>();
            for (String id : remaining) {
                Rejection rejection = graph.node(id)
                        .map(node -> rejectionOf(graph, node, holdings).orElse(new Rejection(RejectReason.NOT_CONNECTED, id, "")))
                        .orElse(new Rejection(RejectReason.UNKNOWN_NODE, id, ""));
                rejected.put(id, rejection);
            }
            return rejected;
        }
    }
}
