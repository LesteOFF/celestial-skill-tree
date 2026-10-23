package dev.lesteoff.celestialskilltree.registry;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class WornItems {
    private static final List<Function<Player, List<ItemStack>>> EXTRA_SOURCES = new CopyOnWriteArrayList<>();

    private WornItems() {
    }

    public static void registerSource(Function<Player, List<ItemStack>> source) {
        EXTRA_SOURCES.add(source);
    }

    public static List<ItemStack> carriedBy(Player player) {
        List<ItemStack> stacks = new ArrayList<>(inExtraSlots(player));
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            stacks.add(player.getInventory().getItem(slot));
        }
        stacks.removeIf(ItemStack::isEmpty);
        return stacks;
    }

    public static List<ItemStack> inExtraSlots(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        EXTRA_SOURCES.forEach(source -> stacks.addAll(source.apply(player)));
        stacks.removeIf(ItemStack::isEmpty);
        return stacks;
    }
}
