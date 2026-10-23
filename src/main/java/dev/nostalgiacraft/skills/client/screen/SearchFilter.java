package dev.nostalgiacraft.skills.client.screen;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

enum SearchFilter {
    ALL, AVAILABLE, ALLOCATED, LOCKED;

    boolean accepts(NodeState state) {
        return switch (this) {
            case ALL -> true;
            case AVAILABLE -> state == NodeState.AVAILABLE;
            case ALLOCATED -> state == NodeState.ALLOCATED || state == NodeState.PENDING;
            case LOCKED -> state == NodeState.LOCKED || state == NodeState.CONFLICTING || state == NodeState.SEALED;
        };
    }

    SearchFilter next() {
        return values()[(ordinal() + 1) % values().length];
    }

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    ResourceLocation icon() {
        return HudSprites.icon("filter_" + key());
    }
}
