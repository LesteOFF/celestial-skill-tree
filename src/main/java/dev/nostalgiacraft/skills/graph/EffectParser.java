package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

final class EffectParser {
    private static final double MIN_DAMAGE_MODIFIER = -0.95;
    private static final double MAX_DAMAGE_MODIFIER = 10;
    private static final int MAX_COOLDOWN_TICKS = 72_000;
    private static final double MAX_HEAL = 100;
    private static final int MAX_DURATION_TICKS = 12_000;
    private static final int MAX_AMPLIFIER = 4;

    private final KnownIds known;

    EffectParser(KnownIds known) {
        this.known = known;
    }

    Optional<SkillEffect> parse(JsonFields effect) {
        String type = effect.text("type");
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case "attribute" -> attribute(effect, false);
            case "conditional_attribute" -> attribute(effect, true);
            case "damage_modifier" -> Optional.of(damageModifier(effect));
            case "trigger" -> trigger(effect);
            default -> {
                effect.error("type", "unknown effect type '" + type + "'");
                yield Optional.empty();
            }
        };
    }

    private Optional<SkillEffect> attribute(JsonFields effect, boolean conditional) {
        String attribute = effect.id("attribute");
        String operation = effect.text("operation");
        double amount = effect.number("amount");
        if (attribute != null && !known.isAttribute(attribute)) {
            effect.error("attribute", "unknown attribute '" + attribute + "'");
        }
        if (operation != null && !SkillEffect.OPERATIONS.contains(operation)) {
            effect.error("operation", "unsupported operation '" + operation + "' (use one of "
                    + new TreeSet<>(SkillEffect.OPERATIONS) + ")");
        }
        effect.requireBounded("amount", amount);
        if (!conditional) {
            return Optional.of(new SkillEffect.Attribute(attribute, operation, amount));
        }
        return condition(effect).map(condition -> new SkillEffect.ConditionalAttribute(attribute, operation, amount, condition));
    }

    private SkillEffect damageModifier(JsonFields effect) {
        String direction = effect.text("direction");
        if (direction != null && !direction.equals("dealt") && !direction.equals("taken")) {
            effect.error("direction", "must be 'dealt' or 'taken'");
        }
        String scope = effect.text("scope");
        if (scope != null && !SkillEffect.isKnownScope(scope)) {
            effect.error("scope", "unknown scope '" + scope + "' (use one of " + new TreeSet<>(SkillEffect.DAMAGE_SCOPES)
                    + " or #<damage type tag id>)");
        }
        double amount = effect.number("amount");
        effect.requireBounded("amount", amount);
        if (amount < MIN_DAMAGE_MODIFIER || amount > MAX_DAMAGE_MODIFIER) {
            effect.error("amount", "must be within -0.95..10");
        }
        Optional<Condition> condition = effect.has("condition") ? condition(effect) : Optional.empty();
        return new SkillEffect.DamageModifier("dealt".equals(direction), scope, amount, condition);
    }

    private Optional<SkillEffect> trigger(JsonFields effect) {
        String trigger = effect.text("trigger");
        if (trigger != null && !SkillEffect.isKnownTrigger(trigger)) {
            effect.error("trigger", "unknown trigger '" + trigger + "' (use one of " + new TreeSet<>(SkillEffect.TRIGGERS)
                    + " or a namespaced id fired by another mod)");
        }
        int cooldown = effect.integerWithin("cooldown_ticks", 0, 0, MAX_COOLDOWN_TICKS);
        return effect.object("action").flatMap(this::action)
                .map(action -> new SkillEffect.Trigger(trigger, action, cooldown));
    }

    private Optional<SkillEffect.Action> action(JsonFields action) {
        String type = action.text("type");
        if (type == null || !SkillEffect.Action.TYPES.contains(type)) {
            action.error("type", "unknown action '" + type + "'");
            return Optional.empty();
        }
        return Optional.of(type.equals("heal") ? healAction(action, type) : mobEffectAction(action, type));
    }

    private static SkillEffect.Action healAction(JsonFields action, String type) {
        double amount = action.number("amount");
        if (!(amount > 0 && amount <= MAX_HEAL)) {
            action.error("amount", "heal amount must be within (0, 100]");
        }
        return new SkillEffect.Action(type, amount, "", 0, 0);
    }

    private SkillEffect.Action mobEffectAction(JsonFields action, String type) {
        String mobEffect = action.id("effect");
        if (mobEffect != null && !known.isMobEffect(mobEffect)) {
            action.error("effect", "unknown mob effect '" + mobEffect + "'");
        }
        int duration = action.integerWithin("duration_ticks", 0, 1, MAX_DURATION_TICKS);
        int amplifier = action.integerWithin("amplifier", 0, 0, MAX_AMPLIFIER);
        return new SkillEffect.Action(type, 0, mobEffect, duration, amplifier);
    }

    private Optional<Condition> condition(JsonFields owner) {
        JsonElement element = owner.json().get("condition");
        if (element == null || !element.isJsonObject()) {
            owner.error("condition", "missing condition object");
            return Optional.empty();
        }
        JsonObject json = element.getAsJsonObject();
        JsonFields condition = owner.child(json, "condition.");
        String type = condition.rawText("type", null);
        if (type == null || !Condition.TYPES.contains(type)) {
            condition.error("type", "unknown condition '" + type + "'");
            return Optional.empty();
        }
        Map<String, Object> parameters = parameters(json);
        requireValidParameters(condition, type, parameters);
        return Optional.of(new Condition(type, Collections.unmodifiableMap(parameters)));
    }

    private static Map<String, Object> parameters(JsonObject condition) {
        Map<String, Object> parameters = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            if (!entry.getKey().equals("type")) {
                JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
                parameters.put(entry.getKey(), value.isNumber() ? (Object) value.getAsDouble() : value.getAsString());
            }
        }
        return parameters;
    }

    private void requireValidParameters(JsonFields condition, String type, Map<String, Object> parameters) {
        switch (type) {
            case "holding", "wearing" -> requireId(condition, parameters, "tag", "an item tag id");
            case "in_biome_tag" -> requireId(condition, parameters, "tag", "a biome tag id");
            case "in_dimension" -> requireId(condition, parameters, "dimension", "a dimension id");
            case "sky_event" -> {
                if (!(parameters.get("event") instanceof String event) || !Condition.SKY_EVENTS.contains(event)) {
                    condition.error("event", "must be one of " + new TreeSet<>(Condition.SKY_EVENTS));
                }
            }
            case "attribute_at_least" -> {
                if (!(parameters.get("attribute") instanceof String attribute) || !known.isAttribute(attribute)) {
                    condition.error("attribute", "unknown attribute '" + parameters.get("attribute") + "'");
                }
                requireNumber(condition, parameters, "value", -1_000_000, 1_000_000);
            }
            case "below_y" -> requireNumber(condition, parameters, "y", -2048, 2048);
            case "health_below", "target_health_above" -> requireNumber(condition, parameters, "fraction", 0.01, 1.0);
            case "shot_distance_over", "shot_distance_under" -> requireNumber(condition, parameters, "blocks", 0, 512);
            default -> {
            }
        }
    }

    private static void requireId(JsonFields condition, Map<String, Object> parameters, String key, String expected) {
        if (!(parameters.get(key) instanceof String id) || !Ids.isValid(id)) {
            condition.error(key, "needs " + expected);
        }
    }

    private static void requireNumber(JsonFields condition, Map<String, Object> parameters, String key, double min,
                                      double max) {
        if (!(parameters.get(key) instanceof Double value) || !Double.isFinite(value) || value < min || value > max) {
            condition.error(key, "must be a number within " + min + ".." + max);
        }
    }
}
