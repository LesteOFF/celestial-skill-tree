package dev.nostalgiacraft.skills.graph;

import java.util.List;

public record ExclusiveGroup(String id, String nameKey, int maxActive, List<String> members) {
}
