package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class StarFragmentItem extends Item {
    public StarFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && !redeem(serverPlayer, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static boolean redeem(ServerPlayer player, ItemStack stack) {
        LedgerService.Result result = LedgerService.grant(ModAttachments.state(player), 1, ServerConfig.POINT_LIMIT.get(), false);
        if (!result.ok()) {
            player.displayClientMessage(Component.translatable(result.reason().orElseThrow().translationKey())
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        stack.consume(1, player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
        player.displayClientMessage(Component.translatable("message.nostalgiaskills.star_fragment_used", result.available())
                .withStyle(ChatFormatting.AQUA), true);
        SkillService.sendState(player);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.nostalgiaskills.star_fragment.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
