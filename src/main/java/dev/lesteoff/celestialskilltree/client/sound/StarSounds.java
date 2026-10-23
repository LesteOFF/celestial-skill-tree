package dev.lesteoff.celestialskilltree.client.sound;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.entity.FallingStar;
import dev.lesteoff.celestialskilltree.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = CelestialSkillTree.MODID, value = Dist.CLIENT)
public final class StarSounds {
    private static final int START_TICK = 2;
    private static final float FALLING_VOLUME = 4f;

    private StarSounds() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() && entity.tickCount == START_TICK && entity instanceof FallingStar) {
            Minecraft.getInstance().getSoundManager().play(new Loop(entity, ModSounds.STAR_FALLING.get(), FALLING_VOLUME));
        }
    }

    private static final class Loop extends EntityBoundSoundInstance {
        private Loop(Entity entity, SoundEvent sound, float volume) {
            super(sound, SoundSource.AMBIENT, volume, 1f, entity, entity.getId());
            looping = true;
            delay = 0;
        }
    }
}
