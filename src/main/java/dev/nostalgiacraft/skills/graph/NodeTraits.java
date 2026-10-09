package dev.nostalgiacraft.skills.graph;

import java.util.Optional;

public record NodeTraits(double neighbourMultiplier, Optional<String> hiddenUntil, boolean toggleable) {
    public static final double NO_MULTIPLIER = 1.0;
    public static final NodeTraits NONE = new NodeTraits(NO_MULTIPLIER, Optional.empty(), false);

    public boolean amplifiesNeighbours() {
        return neighbourMultiplier > NO_MULTIPLIER;
    }

    public boolean standsAlone() {
        return hiddenUntil.isPresent();
    }
}
