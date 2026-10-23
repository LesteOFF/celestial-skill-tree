package dev.lesteoff.celestialskilltree.client;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@EventBusSubscriber(modid = CelestialSkillTree.MODID, value = Dist.CLIENT)
public final class FoodTooltip {
    private static final int PERCENT = 100;

    private FoodTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        boolean hasBeneficialEffect = grantedEffects(event.getItemStack()).anyMatch(FoodTooltip::isExtended);
        boolean tracksDuration = event.getEntity() != null && event.getEntity().getAttributes().hasAttribute(ModAttributes.POTION_DURATION);
        if (!hasBeneficialEffect || !tracksDuration) {
            return;
        }
        long bonus = Math.round(event.getEntity().getAttributeValue(ModAttributes.POTION_DURATION) * PERCENT);
        if (bonus != 0) {
            event.getToolTip().add(Component.translatable("gui.celestialskilltree.food.effect_bonus", String.format("%+d%%", bonus))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static Stream<MobEffectInstance> grantedEffects(ItemStack stack) {
        if (stack.getItem() instanceof PotionItem) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            return contents == null ? Stream.empty() : StreamSupport.stream(contents.getAllEffects().spliterator(), false);
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food == null ? Stream.empty() : food.effects().stream().map(FoodProperties.PossibleEffect::effect);
    }

    private static boolean isExtended(MobEffectInstance effect) {
        return effect.getEffect().value().isBeneficial() && !effect.getEffect().value().isInstantenous();
    }
}
