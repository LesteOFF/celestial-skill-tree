package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.graph.Vec2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HiddenSection {
    public static final double DISTANCE_BELOW_TREE = 900;
    public static final double COLUMN_SPACING = 150;
    public static final double ROW_SPACING = 190;

    record Placement(KeystoneDefinition keystone, ClassShape owner, Slot slot) {
    }

    private HiddenSection() {
    }

    static List<Placement> place(List<ClassPool> pools, TreeShape shape) {
        double top = shape.slots().stream().mapToDouble(slot -> slot.position().y()).max().orElse(0) + DISTANCE_BELOW_TREE;
        List<Placement> placements = new ArrayList<>();
        int row = 0;
        for (ClassShape owner : shape.classes()) {
            List<KeystoneDefinition> hidden = pools.get(owner.index()).hidden().stream()
                    .sorted(Comparator.comparing(KeystoneDefinition::id)).toList();
            for (int column = 0; column < hidden.size(); column++) {
                Vec2 position = new Vec2((column - (hidden.size() - 1) / 2.0) * COLUMN_SPACING, top + row * ROW_SPACING);
                Slot slot = new Slot("hidden/" + row + "_" + column, SlotKind.KEYSTONE, position);
                placements.add(new Placement(hidden.get(column), owner, slot));
            }
            row += hidden.isEmpty() ? 0 : 1;
        }
        return placements;
    }
}
