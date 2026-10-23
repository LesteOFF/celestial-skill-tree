package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.graph.KnownIds;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.Allocation;
import dev.lesteoff.celestialskilltree.progression.FallenStarService;
import dev.lesteoff.celestialskilltree.progression.LedgerService;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.ProgressionService;
import dev.lesteoff.celestialskilltree.progression.Reconciler;
import dev.lesteoff.celestialskilltree.progression.RefundService;
import dev.lesteoff.celestialskilltree.progression.XpCurve;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

public final class ProgressionBenchCli {
    private static final String TREE = "celestialskilltree:main";
    private static final String RULES = "celestialskilltree:main";
    private static final int ROUNDS = 7;
    private static volatile Object sink;
    private static final long ROUND_BUDGET_NANOS = 200_000_000L;
    private static final int GRANTED_POINTS = 1_000_000;
    private static final int PURCHASE_LIMIT = 10_000;
    private static final long HUGE_XP = Long.MAX_VALUE / 4;
    private static final long SHUFFLE_SEED = 42;

    private ProgressionBenchCli() {
    }

    public static void main(String[] args) throws IOException {
        SkillGraphSnapshot graph = shippedTree(Path.of(args.length > 0 ? args[0] : "src/main/resources/data"));
        PlayerSkillState build = fullBuild(graph);
        List<String> learned = build.orderedAllocations().stream().map(Allocation::nodeId).toList();
        String middle = learned.get(learned.size() / 2);
        List<String> batch = shuffledBatch(graph, learned);
        AllocationService.BatchResult check = AllocationService.confirm(graph, freshState(), batch);
        System.out.printf("tree: %d skills, build: %d learned, batch accepted: %s %s%n", graph.nodes().size(), learned.size(),
                check.ok(), check.rejection().map(Object::toString).orElse(""));
        measure("Reconciler.survivors (full build)", () -> Reconciler.survivors(build, graph, Set.of()));
        measure("RefundService.plan (middle skill)", () -> RefundService.plan(graph, build, middle, true));
        measure("FallenStarService.dropNewest", () -> FallenStarService.dropNewest(build.copy(), graph));
        measure("AllocationService.confirm (" + batch.size() + " skills, shuffled)", () -> AllocationService.confirm(graph, freshState(), batch));
        ProgressionService.Settings settings = new ProgressionService.Settings(true, PURCHASE_LIMIT, XpCurve.DEFAULT);
        measure("ProgressionService.purchasePoints (" + PURCHASE_LIMIT + " points)",
                () -> ProgressionService.purchasePoints(new PlayerSkillState(), HUGE_XP, settings, PURCHASE_LIMIT));
    }

    private static SkillGraphSnapshot shippedTree(Path data) throws IOException {
        CatalogSources sources = new CatalogSources(CatalogValidatorCli.read(data, "skill_classes"), CatalogValidatorCli.read(data, "skill_themes"),
                CatalogValidatorCli.read(data, "skill_keystones"), Optional.ofNullable(CatalogValidatorCli.read(data, "skill_rules").get(RULES)));
        Catalog catalog = new CatalogParser(KnownIds.PERMISSIVE).parse(sources).catalog().orElseThrow();
        return TreeGenerator.generate(TREE, catalog, 0, TreeDraw.EMPTY).graph().orElseThrow();
    }

    private static PlayerSkillState fullBuild(SkillGraphSnapshot graph) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, GRANTED_POINTS, GRANTED_POINTS, true);
        Deque<String> frontier = new ArrayDeque<>(List.of(graph.roots().get(0)));
        Set<String> seen = new HashSet<>(frontier);
        while (!frontier.isEmpty()) {
            String id = frontier.poll();
            if (AllocationService.confirm(graph, state, List.of(id)).ok()) {
                graph.neighbours(id).stream().filter(seen::add).forEach(frontier::add);
            }
        }
        return state;
    }

    private static PlayerSkillState freshState() {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, GRANTED_POINTS, GRANTED_POINTS, true);
        return state;
    }

    private static List<String> shuffledBatch(SkillGraphSnapshot graph, List<String> learned) {
        List<String> batch = new ArrayList<>(learned.stream().filter(id -> id.equals(learned.get(0)) || !graph.nodes().get(id).isRoot())
                .limit(AllocationService.MAX_BATCH).toList());
        Collections.shuffle(batch, new Random(SHUFFLE_SEED));
        return batch;
    }

    private static void measure(String label, Supplier<?> work) {
        int iterations = calibrate(work);
        double[] perCall = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                sink = work.get();
            }
            perCall[round] = (System.nanoTime() - start) / 1_000.0 / iterations;
        }
        Arrays.sort(perCall);
        System.out.printf("%-60s %12.1f us/call (median of %d x %d)%n", label, perCall[ROUNDS / 2], ROUNDS, iterations);
    }

    private static int calibrate(Supplier<?> work) {
        long start = System.nanoTime();
        int calls = 0;
        while (System.nanoTime() - start < ROUND_BUDGET_NANOS) {
            sink = work.get();
            calls++;
        }
        return calls;
    }
}
