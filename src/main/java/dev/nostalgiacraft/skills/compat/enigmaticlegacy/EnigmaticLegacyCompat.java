package dev.nostalgiacraft.skills.compat.enigmaticlegacy;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.WornItems;
import dev.nostalgiacraft.skills.server.ComponentXpWallet;
import dev.nostalgiacraft.skills.server.SkillService;
import dev.nostalgiacraft.skills.server.XpWallets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

public final class EnigmaticLegacyCompat {
    public static final String MOD_ID = "enigmaticlegacyplus";
    private static final ResourceLocation CURSED_RING = ResourceLocation.fromNamespaceAndPath(MOD_ID, "cursed_ring");
    private static final Set<ResourceLocation> XP_SCROLLS = Set.of(ResourceLocation.fromNamespaceAndPath(MOD_ID, "xp_scroll"),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "cursed_xp_scroll"));
    private static final ResourceLocation XP_STORED = ResourceLocation.fromNamespaceAndPath(MOD_ID, "xp_stored");
    private static final String RING_POINTS_KEY = "nostalgiaskills_cursed_ring_points";
    private static final int CHECK_PERIOD_TICKS = 20;

    private EnigmaticLegacyCompat() {
    }

    public static void register() {
        XpWallets.register(new ComponentXpWallet(MOD_ID, XP_SCROLLS, XP_STORED, Long::valueOf));
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> OptionalEvents.guard(MOD_ID, () -> onPlayerTick(event)));
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_PERIOD_TICKS == 0) {
            settleRingPoints(player);
        }
    }

    private static void settleRingPoints(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int lent = persisted.getInt(RING_POINTS_KEY);
        boolean wearsRing = WornItems.inExtraSlots(player).stream().anyMatch(stack -> isItem(stack, CURSED_RING));
        if (wearsRing == (lent > 0)) {
            return;
        }
        int nowLent = wearsRing ? lend(player) : takeBack(player, lent);
        persisted.putInt(RING_POINTS_KEY, nowLent);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        SkillService.sendState(player);
    }

    private static int lend(ServerPlayer player) {
        int points = ServerConfig.CURSED_RING_POINTS.get();
        boolean lent = points > 0
                && LedgerService.grant(ModAttachments.state(player), points, ServerConfig.POINT_LIMIT.get(), false).ok();
        if (lent) {
            player.sendSystemMessage(Component.translatable("message.nostalgiaskills.cursed_ring.lent", points)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        return lent ? points : 0;
    }

    private static int takeBack(ServerPlayer player, int lent) {
        PlayerSkillState state = ModAttachments.state(player);
        if (state.availablePoints() < lent) {
            RespecService.resetAll(state);
            EffectEngine.refresh(player);
        }
        LedgerService.grant(state, -Math.min(lent, state.availablePoints()), ServerConfig.POINT_LIMIT.get(), true);
        player.sendSystemMessage(Component.translatable("message.nostalgiaskills.cursed_ring.taken", lent)
                .withStyle(ChatFormatting.DARK_PURPLE));
        return 0;
    }

    private static boolean isItem(ItemStack stack, ResourceLocation id) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }
}
