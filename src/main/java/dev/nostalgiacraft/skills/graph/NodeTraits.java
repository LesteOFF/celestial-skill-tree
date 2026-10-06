package dev.nostalgiacraft.skills.graph;

import java.util.Optional;

public record NodeTraits(double neighbourMultiplier, Optional<String> hiddenUntil) {
    public static final double NO_MULTIPLIER = 1.0;
    public static final NodeTraits NONE = new NodeTraits(NO_MULTIPLIER, Optional.empty());

    public boolean amplifiesNeighbours() {
        return neighbourMultiplier > NO_MULTIPLIER;
    }
}
