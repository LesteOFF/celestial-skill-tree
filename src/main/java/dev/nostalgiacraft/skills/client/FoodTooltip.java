package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class FoodTooltip {
    private static final int PERCENT = 100;

    private FoodTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        FoodProperties food = event.getItemStack().get(DataComponents.FOOD);
        boolean hasBeneficialEffect = food != null
                && food.effects().stream().anyMatch(possible -> possible.effect().getEffect().value().isBeneficial());
        boolean tracksDuration = event.getEntity() != null && event.getEntity().getAttributes().hasAttribute(ModAttributes.POTION_DURATION);
        if (!hasBeneficialEffect || !tracksDuration) {
            return;
        }
        long bonus = Math.round(event.getEntity().getAttributeValue(ModAttributes.POTION_DURATION) * PERCENT);
        if (bonus != 0) {
            event.getToolTip().add(Component.translatable("gui.nostalgiaskills.food.effect_bonus", String.format("%+d%%", bonus))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
