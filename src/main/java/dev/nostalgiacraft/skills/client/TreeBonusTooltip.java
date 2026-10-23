package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.screen.GuiText;
import dev.nostalgiacraft.skills.client.screen.StatsPanel;
import dev.nostalgiacraft.skills.effects.WeaponClasses;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class TreeBonusTooltip {
    private TreeBonusTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null || !ClientSkillState.ready()) {
            return;
        }
        for (Holder<Attribute> attribute : bonusesFor(event.getItemStack())) {
            double amount = StatsPanel.value(player, attribute);
            if (amount != 0) {
                event.getToolTip().add(GuiText.of("item_bonus", StatsPanel.signedPercent(amount),
                        Component.translatable(attribute.value().getDescriptionId())).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }

    private static List<Holder<Attribute>> bonusesFor(ItemStack stack) {
        List<Holder<Attribute>> bonuses = new ArrayList<>();
        if (WeaponClasses.isLight(stack)) {
            bonuses.add(ModAttributes.LIGHT_WEAPON_DAMAGE);
        }
        if (WeaponClasses.isHeavy(stack)) {
            bonuses.add(ModAttributes.HEAVY_WEAPON_DAMAGE);
        }
        if (stack.getItem() instanceof ProjectileWeaponItem) {
            bonuses.add(ModAttributes.PROJECTILE_DAMAGE);
        }
        if (stack.getItem() instanceof CrossbowItem) {
            bonuses.add(ModAttributes.CROSSBOW_DAMAGE);
        }
        if (stack.getItem() instanceof DiggerItem) {
            bonuses.add(ModAttributes.MINING_SPEED);
            bonuses.add(ModAttributes.TOOL_PRESERVATION);
        }
        return bonuses;
    }
}
