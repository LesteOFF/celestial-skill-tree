package dev.nostalgiacraft.skills.graph;

import java.util.Optional;

public final class TraitsParser {
    private static final double MAX_NEIGHBOUR_MULTIPLIER = 4.0;

    private TraitsParser() {
    }

    public static NodeTraits parse(JsonFields node) {
        double multiplier = node.has("neighbour_multiplier") ? node.number("neighbour_multiplier") : NodeTraits.NO_MULTIPLIER;
        if (!(multiplier >= NodeTraits.NO_MULTIPLIER && multiplier <= MAX_NEIGHBOUR_MULTIPLIER)) {
            node.error("neighbour_multiplier", "must be within 1.." + MAX_NEIGHBOUR_MULTIPLIER);
        }
        return new NodeTraits(multiplier, node.optionalId("hidden_until"), node.flag("toggleable", false));
    }
}
