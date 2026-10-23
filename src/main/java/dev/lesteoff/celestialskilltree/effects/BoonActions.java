package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Comparator;
import java.util.List;

final class BoonActions {
    private static final double GUST_STRENGTH = 1.2;
    private static final double BURST_RADIUS = 4;
    private static final double BLINK_DISTANCE = 1.5;
    private static final double CHAOS_LAUNCH_SPEED = 1.0;
    private static final int CHAOS_BURN_TICKS = 100;
    private static final int CHAOS_OUTCOMES = 4;
    private static final int CHEAT_DEATH_INVULNERABLE_TICKS = 40;
    private static final String RAISED_TAG = "celestialskilltree.raised";
    private static final int MAX_RAISED_ALLIES = 3;
    private static final double RAISED_SEARCH_RADIUS = 64;

    private BoonActions() {
    }

    static void gust(ServerPlayer player, double radius) {
        shove(player, radius, 1);
        player.serverLevel().sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY(0.5), player.getZ(), 1, 0, 0, 0, 0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1f, 1f);
    }

    static void pull(ServerPlayer player, double radius) {
        shove(player, radius, -1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 1f, 1f);
    }

    private static void shove(ServerPlayer player, double radius, int awayFromPlayer) {
        for (LivingEntity near : enemiesAround(player, radius)) {
            near.knockback(GUST_STRENGTH, awayFromPlayer * (player.getX() - near.getX()), awayFromPlayer * (player.getZ() - near.getZ()));
        }
    }

    static void areaEffect(ServerPlayer player, SkillEffect.Action action) {
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(action.effect())).ifPresent(mobEffect ->
                enemiesAround(player, action.amount()).forEach(near ->
                        near.addEffect(new MobEffectInstance(mobEffect, action.durationTicks(), action.amplifier()), player)));
    }

    static void cheatDeath(ServerPlayer player, double health) {
        player.setHealth((float) health);
        player.clearFire();
        player.resetFallDistance();
        player.invulnerableTime = CHEAT_DEATH_INVULNERABLE_TICKS;
    }

    static void launch(LivingEntity target, double speed) {
        target.setDeltaMovement(target.getDeltaMovement().x, speed, target.getDeltaMovement().z);
        target.hurtMarked = true;
    }

    static void smite(ServerPlayer player, LivingEntity target, double damage) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(player.level());
        if (bolt != null) {
            bolt.moveTo(target.position());
            bolt.setVisualOnly(true);
            player.level().addFreshEntity(bolt);
        }
        target.hurt(player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player), (float) damage);
    }

    static void lunge(ServerPlayer player, double speed) {
        player.setDeltaMovement(player.getLookAngle().scale(speed));
        player.hurtMarked = true;
    }

    static void chaos(ServerPlayer player, LivingEntity target, double damage) {
        switch (player.getRandom().nextInt(CHAOS_OUTCOMES)) {
            case 0 -> smite(player, target, damage);
            case 1 -> launch(target, CHAOS_LAUNCH_SPEED);
            case 2 -> target.igniteForTicks(CHAOS_BURN_TICKS);
            default -> burst(player, target, damage);
        }
    }

    static void web(ServerPlayer player, LivingEntity target) {
        Level level = target.level();
        BlockPos feet = target.blockPosition();
        if (level.getBlockState(feet).isAir() && mayPlace(player, level, feet)) {
            level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
        }
    }

    private static boolean mayPlace(ServerPlayer player, Level level, BlockPos pos) {
        if (!player.mayBuild() || !player.mayInteract(level, pos)) {
            return false;
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return !NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player)).isCanceled();
    }

    private static List<LivingEntity> enemiesAround(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                other -> isFoe(other, player));
    }

    static void burst(ServerPlayer player, LivingEntity fallen, double damage) {
        Entity center = fallen != null ? fallen : player;
        DamageSource blast = player.damageSources().explosion(player, player);
        for (LivingEntity near : player.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(BURST_RADIUS),
                other -> other != fallen && isFoe(other, player))) {
            near.hurt(blast, (float) damage);
        }
        player.serverLevel().sendParticles(ParticleTypes.EXPLOSION, center.getX(), center.getY(0.5), center.getZ(), 1, 0, 0, 0, 0);
        player.level().playSound(null, center.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 1f);
    }

    static void blinkBehind(ServerPlayer player, LivingEntity target) {
        Vec3 spot = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(BLINK_DISTANCE));
        if (!player.level().noCollision(player, player.getBoundingBox().move(spot.subtract(player.position())))) {
            return;
        }
        player.teleportTo(spot.x, spot.y, spot.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
    }

    static void stackEffect(ServerPlayer player, SkillEffect.Action action) {
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(action.effect())).ifPresent(mobEffect -> {
            MobEffectInstance current = player.getEffect(mobEffect);
            int amplifier = current == null ? 0 : Math.min(action.amplifier(), current.getAmplifier() + 1);
            player.addEffect(new MobEffectInstance(mobEffect, action.durationTicks(), amplifier));
        });
    }

    static void raiseAlly(ServerPlayer player, SkillEffect.Action action, LivingEntity fallen) {
        Entity origin = fallen != null ? fallen : player;
        Entity raised = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(action.effect()))
                .map(type -> type.create(player.serverLevel())).orElse(null);
        if (!(raised instanceof TamableAnimal ally)) {
            return;
        }
        dismissOldestRaised(player);
        ally.moveTo(origin.position());
        ally.tame(player);
        ally.addTag(RAISED_TAG);
        ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, action.durationTicks(), 0));
        player.serverLevel().addFreshEntity(ally);
    }

    private static void dismissOldestRaised(ServerPlayer player) {
        List<TamableAnimal> raised = player.serverLevel().getEntitiesOfClass(TamableAnimal.class,
                        player.getBoundingBox().inflate(RAISED_SEARCH_RADIUS), ally -> ally.getTags().contains(RAISED_TAG) && ally.isOwnedBy(player))
                .stream().sorted(Comparator.comparingInt((TamableAnimal ally) -> ally.tickCount).reversed()).toList();
        raised.stream().limit(Math.max(0, raised.size() - MAX_RAISED_ALLIES + 1)).forEach(Entity::discard);
    }

    private static boolean isFoe(LivingEntity other, ServerPlayer player) {
        boolean bystander = other == player || !other.isAlive() || other instanceof ArmorStand || other instanceof Npc
                || other instanceof OwnableEntity owned && owned.getOwnerUUID() != null;
        return !bystander && (!(other instanceof Player victim) || player.canHarmPlayer(victim));
    }
}
