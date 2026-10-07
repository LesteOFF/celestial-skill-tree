package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.config.ServerConfig;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class AttributeAliases {
    public static final String APOTHIC_ATTRIBUTES = "apothic_attributes";
    public static final String PUFFISH_ATTRIBUTES = "puffish_attributes";
    private static final double FRACTION_BASE = 0;
    private static final double MULTIPLIER_BASE = 1;
    private static final Map<String, Alias> APOTHIC = Map.of(
            "nostalgiaskills:crit_chance", Alias.direct("apothic_attributes:crit_chance", FRACTION_BASE),
            "nostalgiaskills:crit_damage", Alias.direct("apothic_attributes:crit_damage", MULTIPLIER_BASE),
            "nostalgiaskills:dodge_chance", Alias.direct("apothic_attributes:dodge_chance", FRACTION_BASE),
            "nostalgiaskills:lifesteal", Alias.direct("apothic_attributes:life_steal", FRACTION_BASE),
            "nostalgiaskills:mining_speed", Alias.direct("apothic_attributes:mining_speed", MULTIPLIER_BASE),
            "nostalgiaskills:healing_received", Alias.direct("apothic_attributes:healing_received", MULTIPLIER_BASE));
    private static final Map<String, Alias> PUFFISH = Map.of(
            "nostalgiaskills:lifesteal", Alias.scaling("puffish_attributes:life_steal"),
            "nostalgiaskills:mining_speed", Alias.scaling("puffish_attributes:mining_speed"),
            "nostalgiaskills:healing_received", Alias.scaling("puffish_attributes:healing"),
            "nostalgiaskills:projectile_damage", Alias.scaling("puffish_attributes:ranged_damage"));

    public record Alias(String target, double baseOffset, boolean scalesAnotherValue) {
        static Alias direct(String target, double baseOffset) {
            return new Alias(target, baseOffset, false);
        }

        static Alias scaling(String target) {
            return new Alias(target, FRACTION_BASE, true);
        }

        public String operation(String original) {
            return scalesAnotherValue && original.equals("add_value") ? "add_multiplied_base" : original;
        }
    }

    private AttributeAliases() {
    }

    public static Optional<Alias> of(String attribute, Predicate<String> isModEnabled) {
        if (isModEnabled.test(APOTHIC_ATTRIBUTES) && APOTHIC.containsKey(attribute)) {
            return Optional.of(APOTHIC.get(attribute));
        }
        return isModEnabled.test(PUFFISH_ATTRIBUTES) ? Optional.ofNullable(PUFFISH.get(attribute)) : Optional.empty();
    }

    public static Optional<Alias> active(String attribute) {
        return of(attribute, AttributeAliases::isEnabled);
    }

    public static String resolve(String attribute) {
        return active(attribute).map(Alias::target).orElse(attribute);
    }

    public static String operation(String attribute, String operation) {
        return active(attribute).map(alias -> alias.operation(operation)).orElse(operation);
    }

    private static boolean isEnabled(String mod) {
        boolean wanted = mod.equals(APOTHIC_ATTRIBUTES) ? ServerConfig.REDIRECT_TO_APOTHIC_ATTRIBUTES.get()
                : ServerConfig.REDIRECT_TO_PUFFISH_ATTRIBUTES.get();
        return wanted && ModList.get().isLoaded(mod);
    }
}
