package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.graph.NodeTraits;

public record NotableDefinition(String id, SkillTemplate skill, int maxRank, NodeTraits traits) {
}
