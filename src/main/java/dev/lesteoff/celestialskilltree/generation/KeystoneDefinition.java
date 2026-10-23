package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.graph.NodeTraits;

import java.util.Optional;

public record KeystoneDefinition(String id, String classId, int weight, SkillTemplate skill, NodeTraits traits,
                                 Optional<String> exclusiveWith) {
}
