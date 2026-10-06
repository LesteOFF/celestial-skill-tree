package dev.nostalgiacraft.skills.progression;

/** A confirmed allocation with the price actually paid and its global acquisition order. */
public record Allocation(String nodeId, int rank, int paidCost, long acquisitionIndex) {
}
