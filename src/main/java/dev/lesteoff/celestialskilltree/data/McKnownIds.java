package dev.lesteoff.celestialskilltree.data;

import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.Ids;
import dev.lesteoff.celestialskilltree.graph.KnownIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class McKnownIds implements KnownIds {
    public static final McKnownIds INSTANCE = new McKnownIds();

    @Override
    public boolean isAttribute(String id) {
        return Ids.isValid(id) && (BuiltInRegistries.ATTRIBUTE.containsKey(ResourceLocation.parse(id)) || EffectEngine.hasAttributeTarget(id));
    }

    @Override
    public boolean isMobEffect(String id) {
        return Ids.isValid(id) && BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(id));
    }
}
