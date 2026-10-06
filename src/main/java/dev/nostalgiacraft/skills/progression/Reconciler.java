package dev.nostalgiacraft.skills.progression;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Brings a saved build in line with the active graph after a reload, datapack change or offline period.
 * <ol>
 *   <li>Renamed nodes are migrated through the explicit id map (recorded price and order kept, marker stored).</li>
 *   <li>The build is replayed oldest acquisition first (ties by id): a node survives only if it is connected to
 *   surviving nodes, its requirements hold and no older surviving node of its exclusive group blocks it. Passes
 *   repeat until stable, so cascades resolve deterministically and the oldest valid acquisitions win.</li>
 *   <li>Every removed node refunds its recorded paid cost exactly once. If the starting root disappears, the whole
 *   build is refunded and a new free root may be chosen.</li>
 * </ol>
 * Historical counters (purchased points, net grants) are never changed.
 */
public final class Reconciler {
    public record Report(boolean changed, List<String> migrated, List<String> removed, int refunded) {
    }

    private Reconciler() {
    }

    public static Report reconcile(PlayerSkillState s, SkillGraphSnapshot g, Map<String, String> migrations) {
        List<String> migrated = new ArrayList<>();
        for (Allocation a : s.orderedAllocations()) {
            String target = migrations.get(a.nodeId());
            if (!g.contains(a.nodeId()) && target != null && g.contains(target) && !s.isAllocated(target)) {
                String marker = "migrate:" + a.nodeId() + "->" + target;
                s.rename(a.nodeId(), target);
                s.addMarker(marker);
                migrated.add(marker);
            }
        }
        List<String> removed = new ArrayList<>();
        int refunded = 0;
        String start = s.startingRoot().orElse(null);
        boolean startValid = start != null && g.contains(start) && g.nodes().get(start).isRoot() && s.isAllocated(start);
        if (!startValid) {
            for (Allocation a : s.orderedAllocations()) {
                refunded += s.refund(a.nodeId());
                removed.add(a.nodeId());
            }
            s.setStartingRoot(null);
        } else {
            Set<String> kept = survivors(s, g, Set.of());
            for (Allocation a : s.orderedAllocations()) {
                if (!kept.contains(a.nodeId())) {
                    refunded += s.refund(a.nodeId());
                    removed.add(a.nodeId());
                }
            }
        }
        boolean changed = !migrated.isEmpty() || !removed.isEmpty();
        if (changed) {
            s.bumpRevision();
            s.log("reconciled: migrated " + migrated.size() + ", removed " + removed.size() + ", refunded " + refunded);
        }
        s.setReconciledFingerprint(g.fingerprint());
        return new Report(changed, migrated, removed, refunded);
    }

    /**
     * The allocations that remain valid when {@code excluded} is taken out of the build: starting from the
     * starting root, nodes are kept oldest acquisition first (ties by id) while they stay connected to kept nodes,
     * their requirements hold and no older kept node of their exclusive group blocks them. Pure query, no mutation.
     * Returns an empty set when the starting root is missing, invalid or itself excluded.
     */
    public static Set<String> survivors(PlayerSkillState s, SkillGraphSnapshot g, Set<String> excluded) {
        String start = s.startingRoot().orElse(null);
        if (start == null || excluded.contains(start) || !g.contains(start) || !g.nodes().get(start).isRoot()
                || !s.isAllocated(start)) {
            return Set.of();
        }
        Set<String> kept = new LinkedHashSet<>();
        kept.add(start);
        Map<String, Integer> sectorPoints = new HashMap<>();
        sectorPoints.merge(g.nodes().get(start).sector(), s.allocations().get(start).paidCost(), Integer::sum);
        List<Allocation> order = s.orderedAllocations();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Allocation a : order) {
                if (kept.contains(a.nodeId()) || excluded.contains(a.nodeId()) || !g.contains(a.nodeId())) {
                    continue;
                }
                SkillNodeDefinition n = g.nodes().get(a.nodeId());
                if (AllocationService.reason(g, n, kept, sectorPoints) == null) {
                    kept.add(a.nodeId());
                    sectorPoints.merge(n.sector(), a.paidCost(), Integer::sum);
                    progress = true;
                    break; // restart so older nodes always get the first chance at exclusive slots
                }
            }
        }
        return kept;
    }
}
