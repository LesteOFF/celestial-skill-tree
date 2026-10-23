package dev.lesteoff.celestialskilltree.compat.l2hostility;

import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.effects.BossRewards;
import dev.lesteoff.celestialskilltree.effects.EliteMobs;
import dev.lesteoff.celestialskilltree.server.Discoveries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class L2HostilityCompat {
    public static final String MOD_ID = "l2hostility";
    private static final int MAX_EXTRA_NODES = 60;
    private static final String CAPABILITIES = "dev.xkmc.l2hostility.init.registrate.LHMiscs";

    private L2HostilityCompat() {
    }

    public static void register() {
        Reflection.requireTypes(CAPABILITIES);
        EliteMobs.registerRival(monster -> OptionalEvents.guarded(MOD_ID, false, () -> hasTraits(monster)));
        EliteMobs.registerNodeBonus(monster -> OptionalEvents.guarded(MOD_ID, 0, () -> nodesForLevel(monster)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class,
                event -> OptionalEvents.guard(MOD_ID, () -> revealSlainTraits(event)));
    }

    private static void revealSlainTraits(LivingDeathEvent event) {
        if (BossRewards.isRealPlayer(event.getSource().getEntity())) {
            ServerPlayer killer = (ServerPlayer) event.getSource().getEntity();
            capability(event.getEntity()).ifPresent(found -> traitsOf(found).forEach(trait -> Discoveries.reveal(killer, killKey(trait))));
        }
    }

    private static Set<?> traitsOf(Object found) {
        return ((Map<?, ?>) Reflection.field(found, "traits")).keySet();
    }

    private static String killKey(Object trait) {
        ResourceLocation id = (ResourceLocation) Reflection.call(trait, "getRegistryName");
        return Discoveries.KILL_PREFIX + id.getNamespace() + "/" + id.getPath();
    }

    private static boolean hasTraits(Monster monster) {
        return capability(monster).map(found -> !traitsOf(found).isEmpty()).orElse(false);
    }

    private static int nodesForLevel(Monster monster) {
        int levelsPerNode = ServerConfig.L2HOSTILITY_LEVELS_PER_NODE.get();
        int level = capability(monster).map(found -> (int) Reflection.call(found, "getLevel")).orElse(0);
        return levelsPerNode == 0 ? 0 : Math.min(MAX_EXTRA_NODES, level / levelsPerNode);
    }

    private static Optional<?> capability(LivingEntity mob) {
        Object mobCapability = Reflection.call(Reflection.staticField(CAPABILITIES, "MOB"), "type");
        return (Optional<?>) Reflection.call(mobCapability, "getExisting", mob);
    }
}
