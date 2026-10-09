package dev.nostalgiacraft.skills.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeAliasesTest {
    @Test
    void criticalHitsGoToL2DamageTrackerBeforeApothic() {
        assertEquals("l2damagetracker:crit_rate", AttributeAliases
                .of("nostalgiaskills:crit_chance", AttributeAliases.L2_DAMAGE_TRACKER::equals).orElseThrow().target());
        assertEquals("l2damagetracker:crit_damage", AttributeAliases
                .of("nostalgiaskills:crit_damage", AttributeAliases.L2_DAMAGE_TRACKER::equals).orElseThrow().target());
        assertEquals("l2damagetracker:crit_rate",
                AttributeAliases.of("nostalgiaskills:crit_chance", mod -> true).orElseThrow().target());
        assertEquals("apothic_attributes:crit_chance", AttributeAliases
                .of("nostalgiaskills:crit_chance", AttributeAliases.APOTHIC_ATTRIBUTES::equals).orElseThrow().target());
        assertTrue(AttributeAliases.of("nostalgiaskills:lifesteal", AttributeAliases.L2_DAMAGE_TRACKER::equals).isEmpty());
    }
}
