package dev.lesteoff.celestialskilltree.compat.nomansland;

import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

public final class NoMansLandCompat {
    public static final String MOD_ID = "nomansland";
    private static final String SUN_DOG = "com.farcr.nomansland.common.worldevent.SunDog";

    private NoMansLandCompat() {
    }

    public static void register() {
        Reflection.requireTypes(SUN_DOG);
    }

    public static void endSunDogs(ServerLevel overworld) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        OptionalEvents.guard(MOD_ID, "ending sun dogs", () -> {
            Object sunDog = Reflection.callStatic(SUN_DOG, "getOrDefault", overworld);
            if ((Boolean) Reflection.call(sunDog, "isActive")) {
                Reflection.call(sunDog, "end");
            }
        });
    }
}
