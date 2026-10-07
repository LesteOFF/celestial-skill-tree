package dev.nostalgiacraft.skills.entity;

import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.registry.ModEntities;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class FallingStar extends Projectile {
    private static final double LANDING_SPREAD = 24.0;
    private static final int MIN_HEIGHT = 50;
    private static final int MAX_HEIGHT = 80;
    private static final double MIN_DRIFT = 0.3;
    private static final double MAX_DRIFT = 0.6;
    private static final double MIN_FALL = 0.7;
    private static final double MAX_FALL = 0.85;
    private static final int MAX_LIFETIME_TICKS = 600;
    private static final int SPAWN_ATTEMPTS = 8;
    private static final double CLOSEST_HEIGHT = 24.0;
    private static final int TRAIL_PARTICLES = 3;
    private static final double TRAIL_DRAG = 0.3;
    private static final double TRAIL_JITTER = 0.05;
    private static final float IMPACT_VOLUME = 4f;
    private static final int IMPACT_SPARKS = 60;
    private static final int IMPACT_GLINTS = 20;

    private Vec3 trailedFrom = Vec3.ZERO;

    public FallingStar(EntityType<? extends FallingStar> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public static Optional<FallingStar> spawnNear(ServerPlayer player) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            Optional<FallingStar> star = spawnWithin(player, 1.0 - (double) attempt / SPAWN_ATTEMPTS);
            if (star.isPresent()) {
                return star;
            }
        }
        return Optional.empty();
    }

    private static Optional<FallingStar> spawnWithin(ServerPlayer player, double reach) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        Vec3 trajectory = new Vec3(drift(random), -Mth.nextDouble(random, MIN_FALL, MAX_FALL), drift(random));
        double height = Math.max(CLOSEST_HEIGHT, Mth.nextInt(random, MIN_HEIGHT, MAX_HEIGHT) * reach);
        double flightTicks = height / -trajectory.y;
        int landingX = Mth.floor(player.getX() + random.nextGaussian() * LANDING_SPREAD * reach);
        int landingZ = Mth.floor(player.getZ() + random.nextGaussian() * LANDING_SPREAD * reach);
        Vec3 start = new Vec3(landingX + 0.5 - trajectory.x * flightTicks,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, landingX, landingZ) + height, landingZ + 0.5 - trajectory.z * flightTicks);
        if (!entitiesTickAt(level, landingX, landingZ) || !entitiesTickAt(level, Mth.floor(start.x), Mth.floor(start.z))) {
            return Optional.empty();
        }
        return Optional.of(launch(level, start, trajectory));
    }

    public static FallingStar launch(ServerLevel level, Vec3 start, Vec3 trajectory) {
        FallingStar star = new FallingStar(ModEntities.FALLING_STAR.get(), level);
        star.setPos(start);
        star.setDeltaMovement(trajectory);
        level.addFreshEntity(star);
        return star;
    }

    private static boolean entitiesTickAt(ServerLevel level, int blockX, int blockZ) {
        return level.isPositionEntityTicking(new BlockPos(blockX, level.getSeaLevel(), blockZ));
    }

    private static double drift(RandomSource random) {
        double speed = Mth.nextDouble(random, MIN_DRIFT, MAX_DRIFT);
        return random.nextBoolean() ? speed : -speed;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            leaveTrail();
            return;
        }
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision || verticalCollision || isInWater() || isInLava() || tickCount > MAX_LIFETIME_TICKS) {
            land();
        }
    }

    public ItemEntity land() {
        ServerLevel level = (ServerLevel) level();
        level.playSound(null, getX(), getY(), getZ(), ModSounds.STAR_IMPACT.get(), SoundSource.AMBIENT, IMPACT_VOLUME, 1f);
        burst(level, ParticleTypes.FIREWORK, IMPACT_SPARKS, 0.15);
        burst(level, ParticleTypes.END_ROD, IMPACT_GLINTS, 0.05);
        ItemEntity fragment = new ItemEntity(level, getX(), getY() + 0.25, getZ(), new ItemStack(ModItems.STAR_FRAGMENT.get()), 0, 0.2, 0);
        level.addFreshEntity(fragment);
        SkillEvents.post(new SkillEvents.StarLanded(level, fragment));
        discard();
        return fragment;
    }

    private void burst(ServerLevel level, ParticleOptions particle, int count, double speed) {
        for (ServerPlayer viewer : level.players()) {
            level.sendParticles(viewer, particle, true, getX(), getY() + 0.3, getZ(), count, 0.4, 0.4, 0.4, speed);
        }
    }

    private void leaveTrail() {
        boolean moved = !position().equals(trailedFrom);
        trailedFrom = position();
        if (!moved) {
            return;
        }
        Vec3 motion = getDeltaMovement();
        for (int i = 0; i < TRAIL_PARTICLES; i++) {
            level().addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, getX(), getY(), getZ(),
                    trailSpeed(motion.x), trailSpeed(motion.y), trailSpeed(motion.z));
        }
    }

    private double trailSpeed(double motion) {
        return -motion * TRAIL_DRAG + random.nextGaussian() * TRAIL_JITTER;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }
}
