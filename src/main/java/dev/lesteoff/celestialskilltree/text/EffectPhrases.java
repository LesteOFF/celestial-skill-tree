package dev.lesteoff.celestialskilltree.text;

import dev.lesteoff.celestialskilltree.graph.Condition;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.text.Part.Amount;
import dev.lesteoff.celestialskilltree.text.Part.Literal;
import dev.lesteoff.celestialskilltree.text.Part.Named;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class EffectPhrases {
    private static final String MAX_HEALTH = "minecraft:generic.max_health";
    public static final double MANA_PER_HEALTH = 10;
    private static final double HEALTH_PER_HEART = 2;
    private static final double TICKS_PER_SECOND = 20;
    private static final Set<String> ITEM_TAG_CONDITIONS = Set.of("holding", "wearing", "has_curio");
    private static final Set<String> FRACTION_CONDITIONS = Set.of("health_below", "target_health_above");
    private static final Set<String> BLOCK_CONDITIONS = Set.of("shot_distance_over", "shot_distance_under");

    public enum Tone {
        GAIN, COST, NEUTRAL
    }

    public interface AttributeStyle {
        boolean isFraction(String attributeId);
    }

    private EffectPhrases() {
    }

    public static List<Phrase> describe(List<SkillEffect> effects, AttributeStyle style) {
        return effects.stream().map(effect -> describe(effect, style)).toList();
    }

    public static Tone tone(SkillEffect effect) {
        return switch (effect) {
            case SkillEffect.Attribute attribute -> signed(attribute.amount());
            case SkillEffect.ConditionalAttribute conditional -> signed(conditional.amount());
            case SkillEffect.DamageModifier modifier -> signed(modifier.dealt() ? modifier.amount() : -modifier.amount());
            case SkillEffect.Trigger trigger -> Tone.NEUTRAL;
        };
    }

    private static Tone signed(double benefit) {
        return benefit < 0 ? Tone.COST : Tone.GAIN;
    }

    public static Phrase describe(SkillEffect effect, AttributeStyle style) {
        return switch (effect) {
            case SkillEffect.Attribute attribute -> attribute(attribute.attribute(), attribute.operation(), attribute.amount(), style);
            case SkillEffect.ConditionalAttribute conditional -> Phrase.of("effect.conditional",
                    attribute(conditional.attribute(), conditional.operation(), conditional.amount(), style),
                    condition(conditional.condition()));
            case SkillEffect.DamageModifier modifier -> damage(modifier);
            case SkillEffect.Trigger trigger -> trigger(trigger);
        };
    }

    private static Phrase attribute(String attribute, String operation, double amount, AttributeStyle style) {
        Named name = new Named(Named.Kind.ATTRIBUTE, attribute);
        if (operation.equals("add_multiplied_base")) {
            return Phrase.of("effect.attribute.of_base", signedPercent(amount), name);
        }
        if (operation.equals("add_multiplied_total")) {
            return Phrase.of("effect.attribute.of_total", signedPercent(amount), name);
        }
        if (attribute.equals(MAX_HEALTH)) {
            return Phrase.of(countedHearts("effect.attribute.hearts", amount), new Amount(amount, Amount.Style.SIGNED), name,
                    plain(Math.abs(amount) / HEALTH_PER_HEART));
        }
        Amount shown = style.isFraction(attribute) ? signedPercent(amount) : new Amount(amount, Amount.Style.SIGNED);
        return Phrase.of("effect.attribute.plain", shown, name);
    }

    private static Phrase damage(SkillEffect.DamageModifier modifier) {
        Part scope = SkillEffect.isTagScope(modifier.scope()) ? new Literal(modifier.scope())
                : new Named(Named.Kind.DAMAGE_SCOPE, modifier.scope());
        Phrase change = Phrase.of(modifier.dealt() ? "effect.damage.dealt" : "effect.damage.taken", signedPercent(modifier.amount()), scope);
        Optional<Phrase> condition = modifier.condition().map(EffectPhrases::condition);
        return condition.map(when -> Phrase.of("effect.conditional", change, when)).orElse(change);
    }

    private static Phrase trigger(SkillEffect.Trigger trigger) {
        Named name = new Named(Named.Kind.TRIGGER, trigger.trigger());
        Phrase action = action(trigger.action());
        Phrase whole = trigger.cooldownTicks() <= 0 ? Phrase.of("effect.trigger.always", name, action)
                : Phrase.of("effect.trigger", name, action, seconds(trigger.cooldownTicks()));
        return trigger.condition().map(EffectPhrases::condition).map(when -> Phrase.of("effect.conditional", whole, when))
                .orElse(whole);
    }

    private static Phrase action(SkillEffect.Action action) {
        return switch (action.type()) {
            case "heal" -> Phrase.of(countedHearts("effect.action.heal", action.amount()), plain(action.amount()),
                    plain(action.amount() / HEALTH_PER_HEART));
            case "restore_mana" -> Phrase.of("effect.action.restore_mana", plain(action.amount()));
            case "blood_for_mana" -> Phrase.of("effect.action.blood_for_mana", plain(action.amount()),
                    plain(action.amount() / MANA_PER_HEALTH / HEALTH_PER_HEART));
            case "blink_behind_target", "web_target" -> Phrase.of("effect.action." + action.type());
            case "area_effect" -> Phrase.of("effect.action.area_effect", new Named(Named.Kind.MOB_EFFECT, action.effect()),
                    new Literal(Numbers.roman(action.amplifier() + 1)), seconds(action.durationTicks()), plain(action.amount()));
            case "gust", "pull", "sunstrike", "burst", "launch_target", "smite_target", "lunge", "chaos_target" -> Phrase.of("effect.action." + action.type(), plain(action.amount()));
            case "cheat_death" -> Phrase.of(countedHearts("effect.action.cheat_death", action.amount()), plain(action.amount()),
                    plain(action.amount() / HEALTH_PER_HEART));
            case "ignite_target" -> Phrase.of("effect.action.ignite_target", seconds(action.durationTicks()));
            case "raise_ally" -> Phrase.of("effect.action.raise_ally", new Literal(action.effect()), seconds(action.durationTicks()));
            default -> Phrase.of("effect.action." + action.type(), new Named(Named.Kind.MOB_EFFECT, action.effect()),
                    new Literal(Numbers.roman(action.amplifier() + 1)), seconds(action.durationTicks()));
        };
    }

    private static Phrase condition(Condition condition) {
        String key = "effect.condition." + condition.type();
        String type = condition.type();
        if (ITEM_TAG_CONDITIONS.contains(type)) {
            return Phrase.of(key, new Named(Named.Kind.ITEM_TAG, condition.string("tag")));
        }
        if (FRACTION_CONDITIONS.contains(type)) {
            return Phrase.of(key, new Amount(condition.number("fraction"), Amount.Style.PERCENT));
        }
        if (BLOCK_CONDITIONS.contains(type)) {
            return Phrase.of(key, plain(condition.number("blocks")));
        }
        return switch (type) {
            case "below_y" -> Phrase.of(key, plain(condition.number("y")));
            case "target_type" -> Phrase.of(key, new Named(Named.Kind.ENTITY_TAG, condition.string("tag")));
            case "in_dimension" -> Phrase.of(key, new Literal(condition.string("dimension")));
            case "in_biome_tag" -> Phrase.of(key, new Literal(condition.string("tag")));
            case "sky_event" -> Phrase.of(key + "." + condition.string("event"));
            case "astral_attuned" -> condition.params().containsKey("constellation")
                    ? Phrase.of(key, new Named(Named.Kind.CONSTELLATION, condition.string("constellation")))
                    : Phrase.of(key + ".any");
            case "astral_discovered" -> Phrase.of(key, new Named(Named.Kind.CONSTELLATION, condition.string("constellation")));
            case "astral_tier" -> Phrase.of(key, new Named(Named.Kind.ASTRAL_TIER, condition.string("tier")));
            case "attribute_at_least" -> Phrase.of(key, new Named(Named.Kind.ATTRIBUTE, condition.string("attribute")),
                    plain(condition.number("value")));
            default -> Phrase.of(key);
        };
    }

    private static String countedHearts(String key, double health) {
        return Math.abs(health) == HEALTH_PER_HEART ? key + ".one" : key;
    }

    private static Amount signedPercent(double fraction) {
        return new Amount(fraction, Amount.Style.SIGNED_PERCENT);
    }

    private static Amount plain(double value) {
        return new Amount(value, Amount.Style.PLAIN);
    }

    private static Amount seconds(int ticks) {
        return plain(ticks / TICKS_PER_SECOND);
    }
}
