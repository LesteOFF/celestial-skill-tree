package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.registry.ModAttributes;
import dev.lesteoff.celestialskilltree.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class HarvestBonus {
    private HarvestBonus() {
    }

    @SubscribeEvent
    public static void onHarvest(BlockDropsEvent event) {
        boolean ripeCrop = event.getState().getBlock() instanceof CropBlock crop && crop.isMaxAge(event.getState());
        boolean lucky = ripeCrop && BossRewards.isRealPlayer(event.getBreaker())
                && ((ServerPlayer) event.getBreaker()).getRandom().nextDouble()
                < ((ServerPlayer) event.getBreaker()).getAttributeValue(ModAttributes.HARVEST_BONUS);
        if (lucky) {
            event.getDrops().stream().filter(drop -> drop.getItem().is(ModTags.BONUS_HARVEST)).findFirst()
                    .ifPresent(drop -> event.getDrops().add(extraOf(event, drop)));
        }
    }

    private static ItemEntity extraOf(BlockDropsEvent event, ItemEntity drop) {
        BlockPos pos = event.getPos();
        return new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop.getItem().copyWithCount(1));
    }
}
