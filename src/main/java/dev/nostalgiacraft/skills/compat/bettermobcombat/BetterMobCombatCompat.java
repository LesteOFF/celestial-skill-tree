package dev.nostalgiacraft.skills.compat.bettermobcombat;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class BetterMobCombatCompat {
    public static final String MOD_ID = "better_mob_combat_reimagined";
    private static final String COMBAT_STATE = "bmc_re.better_mob_combat.api.MobCombatState";
    private static final int HASTE_PERIOD_TICKS = 2;
    private static final int LAST_COOLDOWN_TICK = 1;

    private BetterMobCombatCompat() {
    }

    public static void register() {
        Reflection.requireTypes(COMBAT_STATE);
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class,
                event -> OptionalEvents.guard(MOD_ID, () -> onTick(event)));
    }

    private static void onTick(EntityTickEvent.Post event) {
        boolean hastened = event.getEntity() instanceof Monster monster && !monster.level().isClientSide()
                && monster.tickCount % HASTE_PERIOD_TICKS == 0 && !monster.getMainHandItem().isEmpty()
                && EliteMobs.isElite(monster) && Reflection.isInstance(COMBAT_STATE, monster);
        if (hastened) {
            shortenAttackCooldown((Monster) event.getEntity());
        }
    }

    private static void shortenAttackCooldown(Monster monster) {
        int cooldown = (int) Reflection.call(monster, "bmc$getAttackCooldown");
        if (cooldown > LAST_COOLDOWN_TICK) {
            Reflection.call(monster, "bmc$setAttackCooldown", cooldown - 1);
        }
    }
}
