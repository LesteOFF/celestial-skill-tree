package dev.lesteoff.celestialskilltree.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.graph.Condition;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
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
        return renderer.sentence(EffectPhrases.describe(List.of(effects), id -> id.startsWith("celestialskilltree:")));
    }

    private static JsonObject translations(String language) {
        Path file = Path.of("src/main/resources/assets/celestialskilltree/lang/" + language + ".json");
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

    @Test
    void everyConditionHasAPhraseInBothLanguages() {
        List<Condition> conditions = List.of(
                condition("holding", Map.of("tag", "celestialskilltree:bows")), condition("wearing", Map.of("tag", "othermod:robes")),
                condition("has_curio", Map.of("tag", "curios:ring")), condition("dual_wielding", Map.of()),
                condition("sprinting", Map.of()), condition("sneaking", Map.of()), condition("blocking", Map.of()),
                condition("below_y", Map.of("y", 0.0)), condition("health_below", Map.of("fraction", 0.5)),
                condition("target_health_above", Map.of("fraction", 0.8)), condition("shot_distance_over", Map.of("blocks", 12.0)),
                condition("shot_distance_under", Map.of("blocks", 6.0)), condition("in_dimension", Map.of("dimension", "aether:the_aether")),
                condition("in_biome_tag", Map.of("tag", "c:is_cold")), condition("sky_event", Map.of("event", "eclipse")),
                condition("sky_event", Map.of("event", "star_shower")),
                condition("attribute_at_least", Map.of("attribute", ARMOR, "value", 10.0)),
                condition("target_type", Map.of("tag", "celestialskilltree:dragons")), condition("wet", Map.of()),
                condition("astral_attuned", Map.of("constellation", "astralsorcery:discidia")),
                condition("astral_discovered", Map.of("constellation", "astralsorcery:armara")),
                condition("astral_attuned", Map.of()), condition("astral_tier", Map.of("tier", "luminance")));
        assertEquals(Condition.TYPES.size() + 2, conditions.size());
        for (Condition condition : conditions) {
            SkillEffect effect = new SkillEffect.ConditionalAttribute(ARMOR, "add_value", 1, condition);
            assertFalse(english(effect).contains("effect."), () -> "en_us lacks " + condition.type());
            assertFalse(portuguese(effect).contains("effect."), () -> "pt_br lacks " + condition.type());
        }
        SkillEffect dragonSlaying = new SkillEffect.DamageModifier(true, "all", 0.15, Optional.of(conditions.get(17)));
        assertEquals("+15% dano causado contra dragões.", portuguese(dragonSlaying));
        assertEquals("+1 Armor while below 50% health.", english(new SkillEffect.ConditionalAttribute(ARMOR, "add_value", 1, conditions.get(8))));
        assertEquals("+1 Armor with astralsorcery:armara discovered.",
                english(new SkillEffect.ConditionalAttribute(ARMOR, "add_value", 1, conditions.get(20))));
        assertEquals("+1 Armor while attuned to a constellation.",
                english(new SkillEffect.ConditionalAttribute(ARMOR, "add_value", 1, conditions.get(21))));
        assertEquals("+1 Armadura após alcançar luminance no Astral Sorcery.",
                portuguese(new SkillEffect.ConditionalAttribute(ARMOR, "add_value", 1, conditions.get(22))));
    }
}
