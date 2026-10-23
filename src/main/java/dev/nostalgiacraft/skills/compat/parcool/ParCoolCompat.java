package dev.nostalgiacraft.skills.compat.parcool;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.Triggers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.Map;

public final class ParCoolCompat {
    public static final String MOD_ID = "parcool";
    private static final String ACTION_STARTED = "com.alrex.parcool.api.action.ParCoolActionEvent$Start$Post";
    private static final Map<String, String> TRIGGER_BY_ACTION = Map.of(
            "WallJump", Triggers.ON_WALL_JUMP,
            "Breakfall", Triggers.ON_ROLL,
            "Dodge", "on_dodge");

    private ParCoolCompat() {
    }

    public static void register() {
        Reflection.requireTypes(ACTION_STARTED);
        OptionalEvents.listen(MOD_ID, ACTION_STARTED, ParCoolCompat::onActionStarted);
    }

    private static void onActionStarted(Event event) {
        String trigger = TRIGGER_BY_ACTION.get(Reflection.call(event, "getAction").getClass().getSimpleName());
        if (trigger != null && Reflection.call(event, "getPlayer") instanceof ServerPlayer player) {
            Triggers.fire(player, trigger, null);
        }
    }
}
