package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.config.ServerConfig;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AttributeAliases {
    public static final String APOTHIC_ATTRIBUTES = "apothic_attributes";
    public static final String L2_DAMAGE_TRACKER = "l2damagetracker";
    public static final String PUFFISH_ATTRIBUTES = "puffish_attributes";
    private static final double FRACTION_BASE = 0;
    private static final double MULTIPLIER_BASE = 1;
    private static final double L2_CRIT_DAMAGE_BASE = 0.5;
    private static final Map<String, Alias> APOTHIC = Map.of(
            "nostalgiaskills:crit_chance", Alias.direct("apothic_attributes:crit_chance", FRACTION_BASE),
            "nostalgiaskills:crit_damage", Alias.direct("apothic_attributes:crit_damage", MULTIPLIER_BASE),
            "nostalgiaskills:dodge_chance", Alias.direct("apothic_attributes:dodge_chance", FRACTION_BASE),
            "nostalgiaskills:lifesteal", Alias.direct("apothic_attributes:life_steal", FRACTION_BASE),
            "nostalgiaskills:mining_speed", Alias.direct("apothic_attributes:mining_speed", MULTIPLIER_BASE),
            "nostalgiaskills:healing_received", Alias.direct("apothic_attributes:healing_received", MULTIPLIER_BASE));
    private static final Map<String, Alias> L2 = Map.of(
            "nostalgiaskills:crit_chance", Alias.direct("l2damagetracker:crit_rate", FRACTION_BASE),
            "nostalgiaskills:crit_damage", Alias.direct("l2damagetracker:crit_damage", L2_CRIT_DAMAGE_BASE));
    private static final Map<String, Alias> PUFFISH = Map.of(
            "nostalgiaskills:lifesteal", Alias.scaling("puffish_attributes:life_steal"),
            "nostalgiaskills:mining_speed", Alias.scaling("puffish_attributes:mining_speed"),
            "nostalgiaskills:healing_received", Alias.scaling("puffish_attributes:healing"),
            "nostalgiaskills:projectile_damage", Alias.scaling("puffish_attributes:ranged_damage"));
    private static final List<Source> BY_PRIORITY = List.of(new Source(L2_DAMAGE_TRACKER, L2),
            new Source(APOTHIC_ATTRIBUTES, APOTHIC), new Source(PUFFISH_ATTRIBUTES, PUFFISH));

    private record Source(String mod, Map<String, Alias> aliases) {
    }

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
        return BY_PRIORITY.stream().filter(source -> source.aliases().containsKey(attribute) && isModEnabled.test(source.mod()))
                .map(source -> source.aliases().get(attribute)).findFirst();
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
        return isWanted(mod).get() && ModList.get().isLoaded(mod);
    }

    private static Supplier<Boolean> isWanted(String mod) {
        return switch (mod) {
            case APOTHIC_ATTRIBUTES -> ServerConfig.REDIRECT_TO_APOTHIC_ATTRIBUTES;
            case L2_DAMAGE_TRACKER -> ServerConfig.REDIRECT_TO_L2_DAMAGE_TRACKER;
            default -> ServerConfig.REDIRECT_TO_PUFFISH_ATTRIBUTES;
        };
    }
}
