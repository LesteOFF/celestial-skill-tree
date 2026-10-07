package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.entity.FallingStar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final String FALLING_STAR_NAME = "falling_star";
    private static final int FALLING_STAR_TRACKING_CHUNKS = 16;

    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, NostalgiaSkills.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FallingStar>> FALLING_STAR = TYPES.register(FALLING_STAR_NAME,
            () -> EntityType.Builder.<FallingStar>of(FallingStar::new, MobCategory.MISC).sized(0.5f, 0.5f)
                    .clientTrackingRange(FALLING_STAR_TRACKING_CHUNKS).updateInterval(1).fireImmune().build(FALLING_STAR_NAME));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
