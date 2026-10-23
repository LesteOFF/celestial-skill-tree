package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

import java.util.List;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class LivingFragmentItem extends Item {
    public static final int MAX_LEVEL = 5;
    public static final int POINTS_WHEN_GROWN = 2;
    private static final String GROWTH_KEY = "growth";
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
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getInt(GROWTH_KEY);
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && !redeem(serverPlayer, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static boolean redeem(ServerPlayer player, ItemStack stack) {
        if (!isGrown(stack)) {
            player.displayClientMessage(Component.translatable("message.nostalgiaskills.living_fragment.growing", growth(stack), fullGrowth())
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        LedgerService.Result result = LedgerService.grant(ModAttachments.state(player), POINTS_WHEN_GROWN, ServerConfig.POINT_LIMIT.get(), false);
        if (!result.ok()) {
            player.displayClientMessage(Component.translatable(result.reason().orElseThrow().translationKey())
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        stack.consume(1, player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.nostalgiaskills.living_fragment.used", POINTS_WHEN_GROWN, result.available())
                .withStyle(ChatFormatting.AQUA), true);
        SkillService.sendState(player);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.nostalgiaskills.living_fragment.tooltip", POINTS_WHEN_GROWN).withStyle(ChatFormatting.GRAY));
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
