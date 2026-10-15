package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AttributeMath {
    private AttributeMath() {
    }

    public static double compute(Holder<Attribute> attr, double base, Collection<AttributeModifier> mods) {
        double v = base;
        for (AttributeModifier modifier : mods) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                v += modifier.amount();
            }
        }
        double r = v;
        for (AttributeModifier modifier : mods) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                r += v * modifier.amount();
            }
        }
        for (AttributeModifier modifier : mods) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                r *= 1.0 + modifier.amount();
            }
        }
        return attr.value().sanitizeValue(r);
    }

    public static double withoutTree(AttributeInstance instance) {
        List<AttributeModifier> keep = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!modifier.id().getNamespace().equals(NostalgiaSkills.MODID)) {
                keep.add(modifier);
            }
        }
        return compute(instance.getAttribute(), instance.getBaseValue(), keep);
    }

    public static double withReplaced(AttributeInstance instance, Collection<AttributeModifier> replacements) {
        List<AttributeModifier> all = new ArrayList<>(replacements);
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (replacements.stream().noneMatch(r -> r.id().equals(modifier.id()))) {
                all.add(modifier);
            }
        }
        return compute(instance.getAttribute(), instance.getBaseValue(), all);
    }
}
