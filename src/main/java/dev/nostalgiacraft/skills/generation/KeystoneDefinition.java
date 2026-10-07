package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.graph.NodeTraits;

public record KeystoneDefinition(String id, String classId, int weight, SkillTemplate skill, NodeTraits traits) {
}
