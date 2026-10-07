package dev.nostalgiacraft.skills.compat.l2hostility;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import net.minecraft.world.entity.monster.Monster;

import java.util.Map;
import java.util.Optional;

public final class L2HostilityCompat {
    public static final String MOD_ID = "l2hostility";
    private static final String CAPABILITIES = "dev.xkmc.l2hostility.init.registrate.LHMiscs";

    private L2HostilityCompat() {
    }

    public static void register() {
        EliteMobs.registerRival(monster -> OptionalEvents.guarded(MOD_ID, false, () -> hasTraits(monster)));
    }

    private static boolean hasTraits(Monster monster) {
        Object mobCapability = Reflection.call(Reflection.staticField(CAPABILITIES, "MOB"), "type");
        Optional<?> existing = (Optional<?>) Reflection.call(mobCapability, "getExisting", monster);
        return existing.map(capability -> !((Map<?, ?>) Reflection.field(capability, "traits")).isEmpty()).orElse(false);
    }
}
