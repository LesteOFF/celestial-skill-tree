package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.GeneratedTree;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class ServerConfigReload {
    private ServerConfigReload() {
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() == ServerConfig.SPEC && server != null) {
            server.execute(() -> apply(server));
        }
    }

    private static void apply(MinecraftServer server) {
        GeneratedTree.generateFor(server);
        server.getPlayerList().getPlayers().forEach(SkillService::resync);
        CelestialSkillTree.LOGGER.info("Server config reloaded: skill prices, caps and tuning re-applied to {} player(s)",
                server.getPlayerList().getPlayerCount());
    }
}
