package dev.lesteoff.celestialskilltree;

import com.mojang.logging.LogUtils;
import dev.lesteoff.celestialskilltree.compat.Integrations;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.network.NetworkSetup;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.registry.ModAttributes;
import dev.lesteoff.celestialskilltree.registry.ModEntities;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import dev.lesteoff.celestialskilltree.registry.ModBlocks;
import dev.lesteoff.celestialskilltree.registry.ModSounds;
import dev.lesteoff.celestialskilltree.server.FluidXpWallet;
import dev.lesteoff.celestialskilltree.server.XpWallets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(CelestialSkillTree.MODID)
public final class CelestialSkillTree {
    public static final String MODID = "celestialskilltree";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CelestialSkillTree(IEventBus modBus, ModContainer container) {
        ModAttributes.register(modBus);
        ModAttachments.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(NetworkSetup::register);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        XpWallets.register(new FluidXpWallet());
        Integrations.register();
    }
}
