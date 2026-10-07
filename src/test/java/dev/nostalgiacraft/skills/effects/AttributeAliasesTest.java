package dev.nostalgiacraft.skills.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeAliasesTest {
    @Test
    void redirectsDuplicatedStatsOnlyWhenApothicAttributesIsLoaded() {
        assertTrue(AttributeAliases.of("nostalgiaskills:crit_chance", mod -> false).isEmpty());
        assertEquals("apothic_attributes:life_steal",
                AttributeAliases.of("nostalgiaskills:lifesteal", AttributeAliases.APOTHIC_ATTRIBUTES::equals).orElseThrow().target());
    }

    @Test
    void leavesStatsApothicDoesNotHaveUntouched() {
        assertTrue(AttributeAliases.of("nostalgiaskills:potion_duration", mod -> true).isEmpty());
        assertTrue(AttributeAliases.of("minecraft:generic.max_health", mod -> true).isEmpty());
    }

    @Test
    void apothicWinsWherePuffishOverlaps() {
        assertEquals("apothic_attributes:mining_speed",
                AttributeAliases.of("nostalgiaskills:mining_speed", mod -> true).orElseThrow().target());
        assertEquals("puffish_attributes:mining_speed", AttributeAliases
                .of("nostalgiaskills:mining_speed", AttributeAliases.PUFFISH_ATTRIBUTES::equals).orElseThrow().target());
    }

    @Test
    void puffishAttributesScaleAnotherValueSoFlatBonusesBecomeMultipliers() {
        AttributeAliases.Alias alias = AttributeAliases.of("nostalgiaskills:projectile_damage", mod -> true).orElseThrow();
        assertEquals("puffish_attributes:ranged_damage", alias.target());
        assertEquals("add_multiplied_base", alias.operation("add_value"));
        assertEquals("add_multiplied_total", alias.operation("add_multiplied_total"));
        assertEquals("add_value", AttributeAliases.of("nostalgiaskills:crit_chance", mod -> true).orElseThrow().operation("add_value"));
    }

    @Test
    void multiplierAttributesCarryTheirBaseAsOffset() {
        assertEquals(1.0, AttributeAliases.of("nostalgiaskills:crit_damage", mod -> true).orElseThrow().baseOffset());
        assertEquals(0.0, AttributeAliases.of("nostalgiaskills:crit_chance", mod -> true).orElseThrow().baseOffset());
    }
}
