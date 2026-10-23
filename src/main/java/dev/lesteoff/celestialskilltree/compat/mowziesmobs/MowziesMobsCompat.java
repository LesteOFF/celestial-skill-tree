package dev.lesteoff.celestialskilltree.compat.mowziesmobs;

import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import dev.lesteoff.celestialskilltree.effects.Triggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class MowziesMobsCompat {
    public static final String MOD_ID = "mowziesmobs";
    private static final String SUNSTRIKE = "com.bobmowzie.mowziesmobs.server.entity.effects.EntitySunstrike";
    private static final ResourceLocation SUNSTRIKE_TYPE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "sunstrike");

    private MowziesMobsCompat() {
    }

    public static void register() {
        Reflection.requireTypes(SUNSTRIKE);
        Triggers.registerAction("sunstrike", (player, action) ->
                OptionalEvents.guard(MOD_ID, () -> callSunstrike(player, action.amount())));
    }

    private static void callSunstrike(ServerPlayer player, double range) {
        if (!(player.pick(range, 1f, false) instanceof BlockHitResult aimed) || aimed.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos ground = aimed.getBlockPos();
        Entity strike = (Entity) Reflection.construct(SUNSTRIKE, BuiltInRegistries.ENTITY_TYPE.get(SUNSTRIKE_TYPE), player.level(),
                player, ground.getX(), ground.getY(), ground.getZ());
        Reflection.call(strike, "onSummon");
        player.level().addFreshEntity(strike);
    }
}
