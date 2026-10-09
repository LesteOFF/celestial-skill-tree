package dev.nostalgiacraft.skills.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.render.FallingStarRenderer;
import dev.nostalgiacraft.skills.client.screen.SkillTreeScreen;
import dev.nostalgiacraft.skills.compat.enhancedbossbars.EnhancedBossBarsCompat;
import dev.nostalgiacraft.skills.compat.supplementaries.CelestialGlobe;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.nostalgiaskills.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.nostalgiaskills");

    public static final KeyMapping BOON_KEY = new KeyMapping("key.nostalgiaskills.boon", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.nostalgiaskills");

    private ClientSetup() {
    }

    @EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN_KEY);
            e.register(BOON_KEY);
        }

        @SubscribeEvent
        public static void loaded(FMLLoadCompleteEvent e) {
            e.enqueueWork(EnhancedBossBarsCompat::registerStyle);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ModEntities.FALLING_STAR.get(), FallingStarRenderer::new);
        }
    }

    @EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private static final int INTEGRATION_CHECK_TICKS = 100;
        private static boolean viewingTree;
        private static String viewedSectorKey = "";

        private GameEvents() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft minecraft = Minecraft.getInstance();
            announceTreeView(minecraft.screen instanceof SkillTreeScreen, minecraft.screen instanceof SkillTreeScreen tree ? tree.viewedSectorKey() : "");
            if (minecraft.level != null && minecraft.level.getGameTime() % INTEGRATION_CHECK_TICKS == 0) {
                CelestialGlobe.keepRegistered();
            }
            while (OPEN_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.screen == null) {
                    ClientScreens.open(false);
                }
            }
            while (BOON_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.screen == null) {
                    ClientRequests.send(new Payloads.UseBoon());
                }
            }
        }

        private static void announceTreeView(boolean open, String sectorKey) {
            if (open != viewingTree || !sectorKey.equals(viewedSectorKey)) {
                viewingTree = open;
                viewedSectorKey = sectorKey;
                ClientRequests.send(new Payloads.TreeViewing(open, sectorKey));
            }
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
            viewingTree = false;
            viewedSectorKey = "";
            ClientViewers.clear();
            ClientSkillState.reset();
        }
    }
}
