package dev.lesteoff.celestialskilltree.compat.bettercombat;

import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import dev.lesteoff.celestialskilltree.effects.Triggers;
import dev.lesteoff.celestialskilltree.effects.WeaponClasses;
import dev.lesteoff.celestialskilltree.effects.WeaponClasses.WeaponClass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.Optional;
import java.util.Set;

public final class BetterCombatCompat {
    public static final String MOD_ID = "bettercombat";
    private static final String WEAPON_REGISTRY = "net.bettercombat.logic.WeaponRegistry";
    private static final Set<String> LIGHT_CATEGORIES = Set.of("sword", "cutlass", "dagger", "rapier", "sickle", "claw", "fist",
            "katana", "twin_blade", "soul_knife", "coral_blade");
    private static final Set<String> HEAVY_CATEGORIES = Set.of("claymore", "hammer", "mace", "axe", "double_axe", "heavy_axe",
            "halberd", "glaive", "scythe", "spear", "lance", "trident", "anchor", "battlestaff");

    private BetterCombatCompat() {
    }

    public static void register() {
        Reflection.requireTypes(WEAPON_REGISTRY);
        WeaponClasses.registerClassifier(stack -> OptionalEvents.guarded(MOD_ID, Optional.empty(), () -> classOf(stack)));
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

    private static Optional<WeaponClass> classOf(ItemStack stack) {
        Object attributes = Reflection.callStatic(WEAPON_REGISTRY, "getAttributes", stack);
        String category = attributes == null ? "" : String.valueOf(Reflection.call(attributes, "category"));
        if (LIGHT_CATEGORIES.contains(category)) {
            return Optional.of(WeaponClass.LIGHT);
        }
        return HEAVY_CATEGORIES.contains(category) ? Optional.of(WeaponClass.HEAVY) : Optional.empty();
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
