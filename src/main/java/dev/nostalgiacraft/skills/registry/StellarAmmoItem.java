package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class StellarAmmoItem extends Item {
    private static final String SHOTS_KEY = "nostalgiaskills_stellar_shots";
    private static final int MAX_LOADED_SHOTS = 10_000;

    public StellarAmmoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer shooter) {
            int loaded = Math.min(MAX_LOADED_SHOTS, shots(shooter) + ServerConfig.STELLAR_AMMO_SHOTS.get());
            setShots(shooter, loaded);
            stack.consume(1, shooter);
            level.playSound(null, shooter.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.6f);
            shooter.displayClientMessage(Component.translatable("message.nostalgiaskills.stellar_ammo.loaded", loaded)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @SubscribeEvent
    public static void onGunshotLands(LivingIncomingDamageEvent event) {
        boolean stellarShot = event.getSource().is(ModTags.COUNTS_AS_PROJECTILE) && event.getAmount() > 0
                && event.getSource().getEntity() instanceof ServerPlayer shooter && shots(shooter) > 0;
        if (stellarShot) {
            ServerPlayer shooter = (ServerPlayer) event.getSource().getEntity();
            event.setAmount((float) (event.getAmount() * (1 + ServerConfig.STELLAR_AMMO_BONUS.get())));
            spendShot(shooter);
        }
    }

    private static void spendShot(ServerPlayer shooter) {
        int left = shots(shooter) - 1;
        setShots(shooter, left);
        if (left == 0) {
            shooter.displayClientMessage(Component.translatable("message.nostalgiaskills.stellar_ammo.spent")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static int shots(ServerPlayer shooter) {
        return PersistedPlayerData.read(shooter).getInt(SHOTS_KEY);
    }

    private static void setShots(ServerPlayer shooter, int shots) {
        PersistedPlayerData.update(shooter, persisted -> persisted.putInt(SHOTS_KEY, shots));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.nostalgiaskills.stellar_ammo.tooltip", ServerConfig.STELLAR_AMMO_SHOTS.get(),
                Math.round(ServerConfig.STELLAR_AMMO_BONUS.get() * 100)).withStyle(ChatFormatting.GRAY));
    }
}
