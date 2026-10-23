package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.registry.WornItems;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

public final class ComponentXpWallet implements XpWallets.Wallet {
    private final String integration;
    private final Set<ResourceLocation> items;
    private final ResourceLocation componentId;
    private final LongFunction<Number> boxing;

    public ComponentXpWallet(String integration, Set<ResourceLocation> items, ResourceLocation componentId, LongFunction<Number> boxing) {
        this.integration = integration;
        this.items = items;
        this.componentId = componentId;
        this.boxing = boxing;
    }

    @Override
    public long stored(ServerPlayer player) {
        return OptionalEvents.guarded(integration, 0L, () -> holders(player).stream().mapToLong(this::storedIn).sum());
    }

    @Override
    public long take(ServerPlayer player, long amount) {
        return OptionalEvents.guarded(integration, 0L, () -> takeFrom(holders(player), amount));
    }

    private long takeFrom(List<ItemStack> holders, long amount) {
        DataComponentType<Number> component = component();
        long missing = amount;
        for (ItemStack holder : holders) {
            long stored = storedIn(holder, component);
            long paid = Math.min(missing, stored);
            holder.set(component, boxing.apply(stored - paid));
            missing -= paid;
        }
        return amount - missing;
    }

    private List<ItemStack> holders(ServerPlayer player) {
        return WornItems.carriedBy(player).stream()
                .filter(stack -> items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))).toList();
    }

    private long storedIn(ItemStack holder) {
        return storedIn(holder, component());
    }

    private static long storedIn(ItemStack holder, DataComponentType<Number> component) {
        Number stored = holder.get(component);
        return stored == null ? 0 : Math.max(0, stored.longValue());
    }

    @SuppressWarnings("unchecked")
    private DataComponentType<Number> component() {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
        if (type == null) {
            throw new IllegalStateException("missing data component " + componentId);
        }
        return (DataComponentType<Number>) type;
    }
}
