package dev.lesteoff.celestialskilltree.client;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CelestialSkillTree.MODID, dist = Dist.CLIENT)
public final class CelestialSkillTreeClient {
    public CelestialSkillTreeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
