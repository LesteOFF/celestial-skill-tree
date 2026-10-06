package dev.nostalgiacraft.skills.graph;

import java.util.Optional;
import java.util.Set;

/** Effect definitions as loaded from data. Gameplay application lives in the effects package. */
public sealed interface SkillEffect {
    Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");
    Set<String> TRIGGERS = Set.of("on_crit", "on_dodge", "on_block", "on_kill", "on_projectile_kill", "on_hurt",
            "on_low_health", "on_potion", "on_full_charge_hit", "on_block_break");
    Set<String> DAMAGE_SCOPES = Set.of("melee", "projectile", "magic", "fire", "all");

    String type();

    /** Permanent attribute modifier. */
    record Attribute(String attribute, String operation, double amount) implements SkillEffect {
        public String type() {
            return "attribute";
        }
    }

    /** Attribute modifier applied only while the condition holds. */
    record ConditionalAttribute(String attribute, String operation, double amount, Condition condition)
            implements SkillEffect {
        public String type() {
            return "conditional_attribute";
        }
    }

    /** Relative change of damage dealt or taken in a scope (0.10 = +10% of the incoming amount). */
    record DamageModifier(boolean dealt, String scope, double amount, Optional<Condition> condition)
            implements SkillEffect {
        public String type() {
            return "damage_modifier";
        }
    }

    /** An action executed when a trigger fires, rate-limited per player by its cooldown. */
    record Trigger(String trigger, Action action, int cooldownTicks) implements SkillEffect {
        public String type() {
            return "trigger";
        }
    }

    /** heal (amount in health points), self_effect / target_effect (mob effect id, duration, amplifier). */
    record Action(String type, double amount, String effect, int durationTicks, int amplifier) {
        public static final Set<String> TYPES = Set.of("heal", "self_effect", "target_effect");
    }
}
