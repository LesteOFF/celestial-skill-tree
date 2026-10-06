package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Recomputes attribute values the way vanilla does, to show the tree's share and before/after previews. */
public final class AttributeMath {
    private AttributeMath() {
    }

    public static double compute(Holder<Attribute> attr, double base, Collection<AttributeModifier> mods) {
        double v = base;
        for (AttributeModifier m : mods) {
            if (m.operation() == AttributeModifier.Operation.ADD_VALUE) {
                v += m.amount();
            }
        }
        double r = v;
        for (AttributeModifier m : mods) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                r += v * m.amount();
            }
        }
        for (AttributeModifier m : mods) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                r *= 1.0 + m.amount();
            }
        }
        return attr.value().sanitizeValue(r);
    }

    /** Final value without this mod's modifiers (equipment, effects and other mods kept). */
    public static double withoutTree(AttributeInstance inst) {
        List<AttributeModifier> keep = new ArrayList<>();
        for (AttributeModifier m : inst.getModifiers()) {
            if (!m.id().getNamespace().equals(NostalgiaSkills.MODID)) {
                keep.add(m);
            }
        }
        return compute(inst.getAttribute(), inst.getBaseValue(), keep);
    }

    /** Final value after adding hypothetical extra modifiers. */
    public static double withExtra(AttributeInstance inst, Collection<AttributeModifier> extra) {
        List<AttributeModifier> all = new ArrayList<>(inst.getModifiers());
        all.addAll(extra);
        return compute(inst.getAttribute(), inst.getBaseValue(), all);
    }
}
