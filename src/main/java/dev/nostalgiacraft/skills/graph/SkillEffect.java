package dev.nostalgiacraft.skills.graph;

import java.util.Optional;
import java.util.Set;

public sealed interface SkillEffect {
    Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");
    Set<String> TRIGGERS = Set.of("on_crit", "on_dodge", "on_block", "on_kill", "on_projectile_kill", "on_hurt",
            "on_low_health", "on_potion", "on_full_charge_hit", "on_block_break", "on_eat");
    Set<String> DAMAGE_SCOPES = Set.of("melee", "projectile", "magic", "fire", "all");
    String TAG_PREFIX = "#";

    String type();

    static boolean isTagScope(String scope) {
        return scope.startsWith(TAG_PREFIX);
    }

    static String tagOf(String scope) {
        return scope.substring(TAG_PREFIX.length());
    }

    static boolean isKnownScope(String scope) {
        return isTagScope(scope) ? Ids.isValid(tagOf(scope)) : DAMAGE_SCOPES.contains(scope);
    }

    static boolean isKnownTrigger(String trigger) {
        return TRIGGERS.contains(trigger) || Ids.isValid(trigger);
    }

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

    record Trigger(String trigger, Action action, int cooldownTicks, Optional<Condition> condition) implements SkillEffect {
        public Trigger(String trigger, Action action, int cooldownTicks) {
            this(trigger, action, cooldownTicks, Optional.empty());
        }

        public String type() {
            return "trigger";
        }
    }

    record Action(String type, double amount, String effect, int durationTicks, int amplifier) {
        public static final Set<String> TYPES = Set.of("heal", "restore_mana", "blood_for_mana", "self_effect", "target_effect",
                "ignite_target", "gust", "pull", "raise_ally", "cheat_death", "sunstrike",
                "stacking_self_effect", "blink_behind_target", "burst", "area_effect", "web_target", "launch_target", "smite_target", "lunge", "chaos_target");
    }
}
