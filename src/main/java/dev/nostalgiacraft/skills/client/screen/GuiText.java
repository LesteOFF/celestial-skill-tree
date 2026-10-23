package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.text.NumberFormat;
import java.util.Locale;

public final class GuiText {
    private GuiText() {
    }

    public static MutableComponent of(String key, Object... arguments) {
        return Component.translatable("gui." + NostalgiaSkills.MODID + "." + key, arguments);
    }

    public static Locale locale() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return Locale.forLanguageTag(code.replace('_', '-'));
    }

    public static String number(long amount) {
        return NumberFormat.getIntegerInstance(locale()).format(amount);
    }
}
