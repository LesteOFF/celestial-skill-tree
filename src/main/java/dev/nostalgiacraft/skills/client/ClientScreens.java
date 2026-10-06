package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.client.screen.SkillTreeScreen;
import net.minecraft.client.Minecraft;

public final class ClientScreens {
    private ClientScreens() {
    }

    public static void open(boolean resetMode) {
        Minecraft minecraft = Minecraft.getInstance();
        SkillTreeScreen screen = new SkillTreeScreen();
        minecraft.setScreen(screen);
        if (resetMode) {
            screen.openResetPanel();
        }
    }
}
