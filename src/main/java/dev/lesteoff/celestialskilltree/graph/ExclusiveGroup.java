package dev.lesteoff.celestialskilltree.graph;

import java.util.List;

public record ExclusiveGroup(String id, String nameKey, int maxActive, List<String> members) {
}
