package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.registry.ModAttributes;
import dev.lesteoff.celestialskilltree.registry.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class MiningEvents {
    private static final double MIN_MINING_MULTIPLIER = 0.1;
    private static final Map<UUID, PendingWear> PENDING_WEAR = new HashMap<>();

    private MiningEvents() {
    }

    private record PendingWear(ItemStack stack, int damageBefore, double roll) {
    }

    private static boolean isEligibleMiningTool(ItemStack tool, BlockState state) {
        return tool.is(ModTags.itemTag(ServerConfig.MINING_TOOL_TAG.get())) && tool.isCorrectToolForDrops(state);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (isEligibleMiningTool(event.getEntity().getMainHandItem(), event.getState())) {
            double bonus = EffectEngine.attributeValue(event.getEntity(), ModAttributes.MINING_SPEED);
            if (bonus != 0) {
                event.setNewSpeed((float) (event.getNewSpeed() * Math.max(MIN_MINING_MULTIPLIER, 1.0 + bonus)));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!BossRewards.isRealPlayer(event.getPlayer())) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        ItemStack tool = player.getMainHandItem();
        if (!isEligibleMiningTool(tool, event.getState())) {
            return;
        }
        Triggers.fire(player, "on_block_break", null);
        PendingWear previousBlock = PENDING_WEAR.remove(player.getUUID());
        if (previousBlock != null && !previousBlock.stack().isEmpty() && previousBlock.stack().getDamageValue() > previousBlock.damageBefore()) {
            settleWear(player, previousBlock);
        }
        if (EffectEngine.attributeValue(player, ModAttributes.TOOL_PRESERVATION) != 0 && tool.isDamageableItem()) {
            PENDING_WEAR.put(player.getUUID(), new PendingWear(tool, tool.getDamageValue(), player.getRandom().nextDouble()));
        }
    }

    private static void settleWear(ServerPlayer player, PendingWear wear) {
        double preservation = EffectEngine.attributeValue(player, ModAttributes.TOOL_PRESERVATION);
        if (preservation > 0 && wear.roll() < preservation) {
            wear.stack().setDamageValue(wear.damageBefore());
        } else if (preservation < 0 && wear.roll() < -preservation) {
            wear.stack().hurtAndBreak(1, player.serverLevel(), player, item -> { });
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PendingWear wear = PENDING_WEAR.remove(player.getUUID());
            if (wear != null && !wear.stack().isEmpty() && wear.stack().getDamageValue() > wear.damageBefore()) {
                settleWear(player, wear);
            }
        }
    }

    public static void forget(UUID id) {
        PENDING_WEAR.remove(id);
    }
}
