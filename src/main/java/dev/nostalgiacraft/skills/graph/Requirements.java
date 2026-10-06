package dev.nostalgiacraft.skills.graph;

import java.util.List;

public record Requirements(List<String> allOf, List<String> anyOf, int minimumSectorPoints) {
    public static final Requirements NONE = new Requirements(List.of(), List.of(), 0);

    public boolean isEmpty() {
        return allOf.isEmpty() && anyOf.isEmpty() && minimumSectorPoints <= 0;
    }
}
