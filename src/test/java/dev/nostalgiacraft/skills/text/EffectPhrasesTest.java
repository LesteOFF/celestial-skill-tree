package dev.nostalgiacraft.skills.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.graph.Condition;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EffectPhrasesTest {
    private static final String ATTACK_SPEED = "minecraft:generic.attack_speed";
    private static final String MAX_HEALTH = "minecraft:generic.max_health";
    private static final String ARMOR = "minecraft:generic.armor";
    private static final Map<String, String> VANILLA_EN = Map.of(ATTACK_SPEED, "Attack Speed", MAX_HEALTH, "Max Health",
            ARMOR, "Armor", "minecraft:strength", "Strength");
    private static final Map<String, String> VANILLA_PT = Map.of(ATTACK_SPEED, "Velocidade de Ataque", MAX_HEALTH, "Vida Máxima",
            ARMOR, "Armadura", "minecraft:strength", "Força");

    private static String describe(String language, Map<String, String> vanilla, SkillEffect... effects) {
        JsonObject translations = translations(language);
        PhraseRenderer renderer = new PhraseRenderer(new PhraseRenderer.Lookup() {
            @Override
            public String translate(String key, String fallback) {
                return translations.has(key) ? translations.get(key).getAsString() : fallback;
            }

            @Override
            public String attributeName(String id) {
                return vanilla.getOrDefault(id, translate("attribute.name." + id.replace(':', '.'), id));
            }

            @Override
            public String mobEffectName(String id) {
                return vanilla.getOrDefault(id, id);
            }
        });
        return renderer.sentence(EffectPhrases.describe(List.of(effects), id -> id.startsWith("nostalgiaskills:")));
    }

    private static JsonObject translations(String language) {
        Path file = Path.of("src/main/resources/assets/nostalgiaskills/lang/" + language + ".json");
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static String english(SkillEffect... effects) {
        return describe("en_us", VANILLA_EN, effects);
    }

    private static String portuguese(SkillEffect... effects) {
        return describe("pt_br", VANILLA_PT, effects);
    }

    private static Condition condition(String type, Map<String, Object> parameters) {
        return new Condition(type, parameters);
    }

    private static SkillEffect whileDualWielding() {
        return new SkillEffect.ConditionalAttribute(ATTACK_SPEED, "add_multiplied_base", 0.05, condition("dual_wielding", Map.of()));
    }

    @Test
    void aConditionalBonusReadsLikeTheHandWrittenOnes() {
        assertEquals("+5% Attack Speed (of base value) while holding a weapon in each hand.", english(whileDualWielding()));
        assertEquals("+5% Velocidade de Ataque (do valor base) com uma arma em cada mão.", portuguese(whileDualWielding()));
    }

    @Test
    void flatBonusesShowTheNumberAndHealthAlsoShowsHearts() {
        assertEquals("+4 Max Health (2 hearts).", english(new SkillEffect.Attribute(MAX_HEALTH, "add_value", 4)));
        assertEquals("-3 Armadura.", portuguese(new SkillEffect.Attribute(ARMOR, "add_value", -3)));
        assertEquals("-2 Vida Máxima (1 coração).", portuguese(new SkillEffect.Attribute(MAX_HEALTH, "add_value", -2)));
        assertEquals("+3 Vida Máxima (1,5 corações).", portuguese(new SkillEffect.Attribute(MAX_HEALTH, "add_value", 3)));
        assertEquals("-10% Armor (of total value).", english(new SkillEffect.Attribute(ARMOR, "add_multiplied_total", -0.1)));
    }

    @Test
    void fractionAttributesShowAPercentageWithTheLanguagesDecimalSeparator() {
        SkillEffect crit = new SkillEffect.Attribute("nostalgiaskills:crit_chance", "add_value", 0.025);
        assertEquals("+2.5% Critical Chance.", english(crit));
        assertEquals("+2,5% Chance de Crítico.", portuguese(crit));
    }

    @Test
    void aTriggerNamesItsCauseActionAndCooldown() {
        SkillEffect onHeadshot = new SkillEffect.Trigger("nostalgiaskills:on_headshot",
                new SkillEffect.Action("self_effect", 0, "minecraft:strength", 60, 0), 100);
        assertEquals("After a headshot, gain Strength I for 3s (cooldown 5s).", english(onHeadshot));
        assertEquals("Após um tiro na cabeça, ganha Força I por 3s (recarga 5s).", portuguese(onHeadshot));
        SkillEffect onPotion = new SkillEffect.Trigger("on_potion", new SkillEffect.Action("heal", 1, "", 0, 0), 0);
        assertEquals("After drinking a beneficial potion, heal 1 health (0.5 hearts).", english(onPotion));
        SkillEffect lowHealth = new SkillEffect.Trigger("on_low_health", new SkillEffect.Action("restore_mana", 50, "", 0, 0), 600);
        assertEquals("Quando sua vida cai abaixo de 30%, restaura 50 de mana (recarga 30s).", portuguese(lowHealth));
    }

    @Test
    void damageModifiersNameTheirScopeAndCondition() {
        SkillEffect melee = new SkillEffect.DamageModifier(true, "melee", 0.1,
                Optional.of(condition("holding", Map.of("tag", "nostalgiaskills:heavy_weapons"))));
        assertEquals("+10% melee damage dealt while holding a heavy weapon.", english(melee));
        assertEquals("+10% dano corpo a corpo causado enquanto segura uma arma pesada.", portuguese(melee));
        SkillEffect spells = new SkillEffect.DamageModifier(false, "#othermod:spells", -0.2, Optional.empty());
        assertEquals("-20% #othermod:spells taken.", english(spells));
    }

    @Test
    void severalEffectsAreJoinedIntoOneSentenceAndUnknownNamesFallBackToTheirIds() {
        String text = english(new SkillEffect.Attribute("othermod:max_mana", "add_value", 25),
                new SkillEffect.Trigger("otherpack:on_dance", new SkillEffect.Action("heal", 2, "", 0, 0), 20));
        assertEquals("+25 othermod:max_mana; otherpack:on_dance, heal 2 health (1 heart) (cooldown 1s).", text);
    }

    @Test
    void everyConditionHasAPhraseInBothLanguages() {
        List<Condition> conditions = List.of(
                condition("holding", Map.of("tag", "nostalgiaskills:bows")), condition("wearing", Map.of("tag", "othermod:robes")),
                condition("has_curio", Map.of("tag", "curios:ring")), condition("dual_wielding", Map.of()),
                condition("sprinting", Map.of()), condition("sneaking", Map.of()), condition("blocking", Map.of()),
                condition("below_y", Map.of("y", 0.0)), condition("health_below", Map.of("fraction", 0.5)),
                condition("target_health_above", Map.of("fraction", 0.8)), condition("shot_distance_over", Map.of("blocks", 12.0)),
                condition("shot_distance_under", Map.of("blocks", 6.0)), condition("in_dimension", Map.of("dimension", "aether:the_aether")),
                condition("in_biome_tag", Map.of("tag", "c:is_cold")), condition("sky_event", Map.of("event", "eclipse")),
                condition("sky_event", Map.of("event", "star_shower")),
                condition("attribute_at_least", Map.of("attribute", ARMOR, "value", 10.0)),
                condition("target_type", Map.of("tag", "nostalgiaskills:dragons")), condition("wet", Map.of()));
        assertEquals(Condition.TYPES.size() + 1, conditions.size());
        for (Condition condition : conditions) {
            SkillEffect effect = new SkillEffect.ConditionalAttribute(ARMOR, "add_value", 1, condition);
            assertFalse(english(effect).contains("effect."), () -> "en_us lacks " + condition.type());
            assertFalse(portuguese(effect).contains("effect."), () -> "pt_br lacks " + condition.type());
        }
        SkillEffect dragonSlaying = new SkillEffect.DamageModifier(true, "all", 0.15, Optional.of(conditions.get(17)));
        assertEquals("+15% dano causado contra dragões.", portuguese(dragonSlaying));
        assertEquals("+1 Armor while below 50% health.", english(new SkillEffect.ConditionalAttribute(ARMOR, "add_value", 1, conditions.get(8))));
    }

    @Test
    void numbersDropUselessDecimalsAndRanksUseRomanNumerals() {
        assertEquals("+0.25", Numbers.format(new Part.Amount(0.25, Part.Amount.Style.SIGNED)));
        assertEquals("3", Numbers.format(new Part.Amount(3.0, Part.Amount.Style.PLAIN)));
        assertEquals("-12.5%", Numbers.format(new Part.Amount(-0.125, Part.Amount.Style.SIGNED_PERCENT)));
        assertEquals("XIX", Numbers.roman(19));
        assertEquals("IV", Numbers.roman(4));
    }
}
