package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.text.EffectPhrases;
import dev.nostalgiacraft.skills.text.PhraseRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.PercentageAttribute;

import java.util.List;
import java.util.Optional;

public final class SkillTexts {
    public record EffectLine(EffectPhrases.Tone tone, MutableComponent text) {
    }

    private static final PhraseRenderer RENDERER = new PhraseRenderer(new PhraseRenderer.Lookup() {
        @Override
        public String translate(String key, String fallback) {
            return Language.getInstance().getOrDefault(key, fallback);
        }

        @Override
        public String attributeName(String id) {
            return BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.parse(id))
                    .map(attribute -> translate(attribute.getDescriptionId(), id))
                    .orElseGet(() -> translate("attribute.name." + id.replace(':', '.'), id));
        }

        @Override
        public String mobEffectName(String id) {
            return BuiltInRegistries.MOB_EFFECT.getOptional(ResourceLocation.parse(id))
                    .map(effect -> translate(effect.getDescriptionId(), id)).orElse(id);
        }
    });

    private SkillTexts() {
    }

    public static MutableComponent name(SkillNodeDefinition node) {
        return Component.translatable(node.nameKey());
    }

    public static MutableComponent description(SkillNodeDefinition node) {
        return node.descriptionKey().isEmpty() ? exactEffects(node) : Component.translatable(node.descriptionKey());
    }

    public static Optional<MutableComponent> numbersBehindPlainText(SkillNodeDefinition node) {
        boolean plainTextHidesNumbers = !node.descriptionKey().isEmpty() && !node.effects().isEmpty();
        return plainTextHidesNumbers ? Optional.of(exactEffects(node)) : Optional.empty();
    }

    public static List<EffectLine> effectLines(SkillNodeDefinition node) {
        return node.effects().stream().map(effect -> new EffectLine(EffectPhrases.tone(effect),
                Component.literal(RENDERER.line(EffectPhrases.describe(effect, SkillTexts::isFraction))))).toList();
    }

    private static MutableComponent exactEffects(SkillNodeDefinition node) {
        return Component.literal(RENDERER.sentence(EffectPhrases.describe(node.effects(), SkillTexts::isFraction)));
    }

    private static boolean isFraction(String attributeId) {
        boolean ownAttribute = Ids.namespace(attributeId).equals(NostalgiaSkills.MODID);
        return ownAttribute || BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.parse(attributeId))
                .filter(PercentageAttribute.class::isInstance).isPresent();
    }
}
