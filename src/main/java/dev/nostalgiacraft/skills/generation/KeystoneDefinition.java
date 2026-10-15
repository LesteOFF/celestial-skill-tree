package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.graph.NodeTraits;

import java.util.Optional;

public record KeystoneDefinition(String id, String classId, int weight, SkillTemplate skill, NodeTraits traits,
                                 Optional<String> exclusiveWith) {
}
