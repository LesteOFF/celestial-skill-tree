package dev.nostalgiacraft.skills;

import com.mojang.logging.LogUtils;
import dev.nostalgiacraft.skills.compat.Integrations;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.network.NetworkSetup;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import dev.nostalgiacraft.skills.registry.ModEntities;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(NostalgiaSkills.MODID)
public final class NostalgiaSkills {
    public static final String MODID = "nostalgiaskills";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NostalgiaSkills(IEventBus modBus, ModContainer container) {
        ModAttributes.register(modBus);
        ModAttachments.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(NetworkSetup::register);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        Integrations.register();
    }
}
