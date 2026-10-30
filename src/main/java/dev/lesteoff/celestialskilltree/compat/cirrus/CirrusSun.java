package dev.lesteoff.celestialskilltree.compat.cirrus;

import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;

import java.util.Optional;

public final class CirrusSun {
    public static final String MOD_ID = "cirrus";
    private static final String RENDER_STATE = "com.jvn.cirrus.client.util.CirrusCelestialRenderState";

    private CirrusSun() {
    }

    public static void register() {
        Reflection.requireTypes(RENDER_STATE);
    }

    public static Optional<Matrix4f> modelView() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return Optional.empty();
        }
        return Optional.ofNullable(OptionalEvents.guarded(MOD_ID, null, () -> (Matrix4f) Reflection.callStatic(RENDER_STATE, "sunModelView")));
    }
}
