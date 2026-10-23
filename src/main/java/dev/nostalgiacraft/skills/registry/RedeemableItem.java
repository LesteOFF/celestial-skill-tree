package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.IntFunction;

abstract class RedeemableItem extends Item {
    RedeemableItem(Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && !redeemBy(serverPlayer, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    protected abstract boolean redeemBy(ServerPlayer player, ItemStack stack);

    static boolean grantPoints(ServerPlayer player, ItemStack stack, int points, float pitch, IntFunction<MutableComponent> message) {
        LedgerService.Result result = LedgerService.grant(ModAttachments.state(player), points, ServerConfig.POINT_LIMIT.get(), false);
        if (!result.ok()) {
            player.displayClientMessage(Component.translatable(result.reason().orElseThrow().translationKey())
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        stack.consume(1, player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, pitch);
        player.displayClientMessage(message.apply(result.available()).withStyle(ChatFormatting.AQUA), true);
        SkillService.sendState(player);
        return true;
    }
}
