package dev.lesteoff.celestialskilltree.registry;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CelestialSkillTree.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_FALLING = sound("entity.falling_star.falling");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_IMPACT = sound("entity.falling_star.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECLIPSE_START = sound("event.eclipse.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_SHOWER_START = sound("event.star_shower.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_OPEN = sound("ui.tree_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> NODE_MARK = sound("ui.node_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> POINT_BUY = sound("ui.point_buy");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONFIRM = sound("ui.confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEYSTONE = sound("ui.keystone");

    private ModSounds() {
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
