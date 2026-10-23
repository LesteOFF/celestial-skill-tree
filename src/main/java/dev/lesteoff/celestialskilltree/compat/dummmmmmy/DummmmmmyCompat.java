package dev.lesteoff.celestialskilltree.compat.dummmmmmy;

import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import net.neoforged.neoforge.common.NeoForge;

public final class DummmmmmyCompat {
    public static final String MOD_ID = "dummmmmmy";
    private static final String CRIT_TRACKER = "net.mehvahdjukaar.dummmmmmy.common.ModEvents";

    private DummmmmmyCompat() {
    }

    public static void register() {
        Reflection.requireTypes(CRIT_TRACKER);
        NeoForge.EVENT_BUS.addListener(SkillEvents.TreeCriticalHit.class,
                event -> OptionalEvents.guard(MOD_ID, () -> showAsCritical(event)));
    }

    private static void showAsCritical(SkillEvents.TreeCriticalHit event) {
        Reflection.callStatic(CRIT_TRACKER, "onEntityCriticalHit", event.getPlayer(), event.getVictim(), event.getMultiplier());
    }
}
