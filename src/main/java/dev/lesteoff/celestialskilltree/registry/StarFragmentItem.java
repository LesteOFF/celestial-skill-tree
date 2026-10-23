package dev.lesteoff.celestialskilltree.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class StarFragmentItem extends RedeemableItem {
    private static final float CHIME_PITCH = 1.4f;

    public StarFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean redeemBy(ServerPlayer player, ItemStack stack) {
        return redeem(player, stack);
    }

    public static boolean redeem(ServerPlayer player, ItemStack stack) {
        return grantPoints(player, stack, 1, CHIME_PITCH,
                available -> Component.translatable("message.celestialskilltree.star_fragment_used", available));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.celestialskilltree.star_fragment.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
