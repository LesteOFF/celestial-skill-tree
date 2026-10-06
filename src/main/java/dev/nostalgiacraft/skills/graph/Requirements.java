package dev.nostalgiacraft.skills.graph;

import java.util.List;

/**
 * Extra purchase conditions, independent of graph adjacency.
 * Terms are either a node id ("ns:path", the node must be allocated) or a tag ("#tag", at least one allocated
 * node carries the tag). Empty lists mean "no restriction". minimumSectorPoints counts the recorded paid cost of
 * allocations already held in the node's sector, including nodes accepted earlier in the same batch but never the
 * node itself.
 */
public record Requirements(List<String> allOf, List<String> anyOf, int minimumSectorPoints) {
    public static final Requirements NONE = new Requirements(List.of(), List.of(), 0);

    public boolean isEmpty() {
        return allOf.isEmpty() && anyOf.isEmpty() && minimumSectorPoints <= 0;
    }
}
