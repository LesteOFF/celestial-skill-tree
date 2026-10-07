package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, NostalgiaSkills.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_FALLING = sound("entity.falling_star.falling");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_IMPACT = sound("entity.falling_star.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECLIPSE_START = sound("event.eclipse.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_SHOWER_START = sound("event.star_shower.start");

    private ModSounds() {
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
