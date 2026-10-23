package dev.lesteoff.celestialskilltree.client.sound;

import dev.lesteoff.celestialskilltree.config.ClientConfig;
import dev.lesteoff.celestialskilltree.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

public final class TreeSounds {
    private static final float PITCH = 1f;

    private TreeSounds() {
    }

    public static void opened() {
        play(ModSounds.TREE_OPEN);
    }

    public static void nodeMarked() {
        play(ModSounds.NODE_MARK);
    }

    public static void pointBought() {
        play(ModSounds.POINT_BUY);
    }

    public static void confirmed(boolean keystoneLearned) {
        play(ModSounds.CONFIRM);
        if (keystoneLearned) {
            play(ModSounds.KEYSTONE);
        }
    }

    private static void play(Supplier<SoundEvent> sound) {
        float volume = ClientConfig.UI_SOUND_VOLUME.get().floatValue();
        if (volume > 0) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), PITCH, volume));
        }
    }
}
