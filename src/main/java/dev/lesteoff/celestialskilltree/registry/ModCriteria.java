package dev.lesteoff.celestialskilltree.registry;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.server.MilestoneTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCriteria {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, CelestialSkillTree.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, MilestoneTrigger> MILESTONE = TRIGGERS.register("milestone", MilestoneTrigger::new);

    private ModCriteria() {
    }

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }
}
