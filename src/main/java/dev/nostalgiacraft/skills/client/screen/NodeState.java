package dev.nostalgiacraft.skills.client.screen;

/** Presentation states (design section 6.4). Focus is an overlay drawn independently of these. */
public enum NodeState {
    LOCKED,
    AVAILABLE,
    ALLOCATED,
    PENDING,
    CONFLICTING
}
