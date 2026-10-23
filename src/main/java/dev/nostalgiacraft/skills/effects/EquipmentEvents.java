package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class EquipmentEvents {
    private static final int CONDITIONAL_REFRESH_TICKS = 10;

    private EquipmentEvents() {
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!onlyWearChanged(event.getFrom(), event.getTo()) && event.getEntity() instanceof ServerPlayer player) {
            BorrowedNodes.refreshIfChanged(player);
            EffectEngine.updateConditionals(player);
        }
    }

    private static boolean onlyWearChanged(ItemStack from, ItemStack to) {
        if (from.getItem() != to.getItem()) {
            return false;
        }
        ItemStack worn = from.copy();
        worn.setDamageValue(to.getDamageValue());
        return ItemStack.isSameItemSameComponents(worn, to);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CONDITIONAL_REFRESH_TICKS == 0) {
            EffectEngine.updateConditionals(player);
        }
    }
}
