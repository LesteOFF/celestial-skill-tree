package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class GuiText {
    private GuiText() {
    }

    static MutableComponent of(String key, Object... arguments) {
        return Component.translatable("gui." + NostalgiaSkills.MODID + "." + key, arguments);
    }
}
