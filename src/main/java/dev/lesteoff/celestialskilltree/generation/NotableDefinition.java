package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.graph.NodeTraits;

public record NotableDefinition(String id, SkillTemplate skill, int maxRank, NodeTraits traits) {
}
