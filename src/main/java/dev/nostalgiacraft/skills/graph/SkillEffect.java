package dev.nostalgiacraft.skills.graph;

import java.util.Optional;
import java.util.Set;

public sealed interface SkillEffect {
    Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");
    Set<String> TRIGGERS = Set.of("on_crit", "on_dodge", "on_block", "on_kill", "on_projectile_kill", "on_hurt",
            "on_low_health", "on_potion", "on_full_charge_hit", "on_block_break");
    Set<String> DAMAGE_SCOPES = Set.of("melee", "projectile", "magic", "fire", "all");

    String type();

    record Attribute(String attribute, String operation, double amount) implements SkillEffect {
        public String type() {
            return "attribute";
        }
    }

    record ConditionalAttribute(String attribute, String operation, double amount, Condition condition)
            implements SkillEffect {
        public String type() {
            return "conditional_attribute";
        }
    }

    record DamageModifier(boolean dealt, String scope, double amount, Optional<Condition> condition)
            implements SkillEffect {
        public String type() {
            return "damage_modifier";
        }
    }

    record Trigger(String trigger, Action action, int cooldownTicks) implements SkillEffect {
        public String type() {
            return "trigger";
        }
    }

    record Action(String type, double amount, String effect, int durationTicks, int amplifier) {
        public static final Set<String> TYPES = Set.of("heal", "self_effect", "target_effect");
    }
}
