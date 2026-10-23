package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

import java.util.List;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class LivingFragmentItem extends RedeemableItem {
    public static final int MAX_LEVEL = 5;
    private static final String GROWTH_KEY = "growth";
    private static final float CHIME_PITCH = 0.8f;
    private static final int FULL_BAR = 13;
    private static final int BAR_COLOR = 0xA977DC;

    public LivingFragmentItem(Properties properties) {
        super(properties);
    }

    @SubscribeEvent
    public static void onExperiencePickup(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            feed(player, event.getOrb().getValue());
        }
    }

    public static void feed(ServerPlayer player, int experience) {
        if (experience <= 0) {
            return;
        }
        WornItems.carriedBy(player).stream().filter(stack -> stack.getItem() instanceof LivingFragmentItem && !isGrown(stack))
                .findFirst().ifPresent(stack -> grow(player, stack, experience));
    }

    private static void grow(ServerPlayer player, ItemStack stack, int experience) {
        int levelBefore = level(stack);
        int growth = Math.min(fullGrowth(), growth(stack) + experience);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(GROWTH_KEY, growth));
        if (level(stack) > levelBefore) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 1f, 1.2f);
            player.displayClientMessage(Component.translatable("message.nostalgiaskills.living_fragment.level", level(stack), MAX_LEVEL)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    private static int growth(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(GROWTH_KEY);
    }

    private static int fullGrowth() {
        return MAX_LEVEL * ServerConfig.LIVING_FRAGMENT_XP_PER_LEVEL.get();
    }

    public static int level(ItemStack stack) {
        return Math.min(MAX_LEVEL, growth(stack) / ServerConfig.LIVING_FRAGMENT_XP_PER_LEVEL.get());
    }

    public static boolean isGrown(ItemStack stack) {
        return level(stack) >= MAX_LEVEL;
    }

    @Override
    protected boolean redeemBy(ServerPlayer player, ItemStack stack) {
        return redeem(player, stack);
    }

    static boolean redeem(ServerPlayer player, ItemStack stack) {
        if (!isGrown(stack)) {
            player.displayClientMessage(Component.translatable("message.nostalgiaskills.living_fragment.growing", growth(stack), fullGrowth())
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        int points = ServerConfig.LIVING_FRAGMENT_POINTS.get();
        return grantPoints(player, stack, points, CHIME_PITCH,
                available -> Component.translatable("message.nostalgiaskills.living_fragment.used", points, available));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.nostalgiaskills.living_fragment.tooltip", ServerConfig.LIVING_FRAGMENT_POINTS.get()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.nostalgiaskills.living_fragment.level", level(stack), MAX_LEVEL, growth(stack), fullGrowth())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isGrown(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !isGrown(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(FULL_BAR * growth(stack) / (float) fullGrowth()), 0, FULL_BAR);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }
}
