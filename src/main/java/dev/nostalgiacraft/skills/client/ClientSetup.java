package dev.nostalgiacraft.skills.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.nostalgiaskills.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.nostalgiaskills");

    private ClientSetup() {
    }

    @EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN_KEY);
        }
    }

    @EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.screen == null) {
                    ClientScreens.open(false);
                }
            }
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientSkillState.reset();
        }
    }
}
