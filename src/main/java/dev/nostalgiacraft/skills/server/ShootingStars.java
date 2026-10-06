package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class ShootingStars {
    private static final int TICKS_PER_SECOND = 20;
    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 48;
    private static final int FALL_HEIGHT = 70;
    private static final double FALL_SPEED = -1.2;
    private static final List<ItemEntity> FALLING = new ArrayList<>();

    private ShootingStars() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        trailFallingStars();
        ServerLevel overworld = event.getServer().overworld();
        boolean rollNow = event.getServer().getTickCount() % TICKS_PER_SECOND == 0;
        if (!rollNow || !ServerConfig.SHOOTING_STARS_ENABLED.get() || !overworld.isNight()) {
            return;
        }
        double chancePerSecond = 1.0 / (ServerConfig.SHOOTING_STAR_AVERAGE_MINUTES.get() * SECONDS_PER_MINUTE);
        for (ServerPlayer player : overworld.players()) {
            if (overworld.getRandom().nextDouble() < chancePerSecond) {
                spawnNear(player);
            }
        }
    }

    public static Optional<ItemEntity> spawnNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * 2 * Math.PI;
        int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
        if (!level.isLoaded(column)) {
            return Optional.empty();
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        ItemEntity star = new ItemEntity(level, column.getX() + 0.5, surface + FALL_HEIGHT, column.getZ() + 0.5,
                new ItemStack(ModItems.STAR_FRAGMENT.get()), 0, FALL_SPEED, 0);
        star.setGlowingTag(true);
        level.addFreshEntity(star);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.AMBIENT, 2f, 0.6f);
        FALLING.add(star);
        return Optional.of(star);
    }

    private static void trailFallingStars() {
        FALLING.removeIf(star -> !star.isAlive() || land(star));
        for (ItemEntity star : FALLING) {
            if (star.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.END_ROD, star.getX(), star.getY(), star.getZ(), 4, 0.1, 0.4, 0.1, 0.01);
            }
        }
    }

    private static boolean land(ItemEntity star) {
        if (!star.onGround()) {
            return false;
        }
        if (star.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FIREWORK, star.getX(), star.getY() + 0.3, star.getZ(), 30, 0.3, 0.3, 0.3, 0.08);
            level.playSound(null, star.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.AMBIENT, 2f, 0.8f);
        }
        return true;
    }
}
