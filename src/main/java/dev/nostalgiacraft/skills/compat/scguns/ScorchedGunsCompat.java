package dev.nostalgiacraft.skills.compat.scguns;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.GameplayEvents;
import dev.nostalgiacraft.skills.effects.Triggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ScorchedGunsCompat {
    public static final String MOD_ID = "scguns";
    private static final String RELOADED = "top.ribs.scguns.event.GunReloadEvent$Post";
    private static final String PROJECTILE_HIT = "top.ribs.scguns.event.GunProjectileHitEvent";
    private static final String BULLET = "top.ribs.scguns.entity.projectile.ProjectileEntity";
    private static final String DETAILED_HIT = "top.ribs.scguns.util.math.ExtendedEntityRayTraceResult";

    private ScorchedGunsCompat() {
    }

    public static void register() {
        OptionalEvents.listen(MOD_ID, RELOADED, ScorchedGunsCompat::onReloaded);
        OptionalEvents.listen(MOD_ID, PROJECTILE_HIT, ScorchedGunsCompat::onProjectileHit);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,
                event -> OptionalEvents.guard(MOD_ID, () -> rememberBulletOrigin(event.getEntity())));
    }

    private static void onReloaded(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_RELOAD, null);
        }
    }

    private static void onProjectileHit(Event event) {
        Object hit = Reflection.call(event, "getRayTrace");
        boolean headshot = Reflection.isInstance(DETAILED_HIT, hit) && (boolean) Reflection.call(hit, "isHeadshot");
        if (headshot && shooter(Reflection.call(event, "getProjectile")) instanceof ServerPlayer player) {
            Triggers.fire(player, Triggers.ON_HEADSHOT, ((EntityHitResult) hit).getEntity() instanceof LivingEntity victim ? victim : null);
        }
    }

    private static void rememberBulletOrigin(Entity entity) {
        boolean playerBullet = !entity.level().isClientSide() && Reflection.isInstance(BULLET, entity)
                && shooter(entity) instanceof ServerPlayer;
        if (playerBullet) {
            GameplayEvents.rememberOrigin(entity);
        }
    }

    private static Object shooter(Object bullet) {
        return Reflection.call(bullet, "getShooter");
    }
}
