package dev.nostalgiacraft.skills.graph;

import java.util.List;

/** At most {@code maxActive} members of the group may be allocated at once. */
public record ExclusiveGroup(String id, String nameKey, int maxActive, List<String> members) {
}
