package dev.nostalgiacraft.skills.compat.bettercombat;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.Triggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class BetterCombatCompat {
    public static final String MOD_ID = "bettercombat";

    private BetterCombatCompat() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class,
                event -> OptionalEvents.guard(MOD_ID, () -> onDamageDealt(event)));
    }

    private static void onDamageDealt(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        boolean meleeHit = source.is(DamageTypes.PLAYER_ATTACK) && event.getNewDamage() > 0;
        if (meleeHit && source.getDirectEntity() instanceof ServerPlayer player && finishesCombo(player)) {
            Triggers.fire(player, Triggers.ON_COMBO_FINISHER, event.getEntity());
        }
    }

    private static boolean finishesCombo(ServerPlayer player) {
        Object attack = Reflection.call(player, "getCurrentAttack");
        if (attack == null) {
            return false;
        }
        Object combo = Reflection.call(attack, "combo");
        int total = (int) Reflection.call(combo, "total");
        return total > 1 && (int) Reflection.call(combo, "current") == total;
    }
}
