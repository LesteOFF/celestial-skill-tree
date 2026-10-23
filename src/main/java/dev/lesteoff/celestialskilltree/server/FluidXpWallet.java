package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public final class FluidXpWallet implements XpWallets.Wallet {
    public static final TagKey<Fluid> EXPERIENCE = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "experience"));

    @Override
    public long stored(ServerPlayer player) {
        int perPoint = ServerConfig.LIQUID_XP_MB_PER_POINT.get();
        long stored = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; perPoint > 0 && slot < inventory.getContainerSize(); slot++) {
            IFluidHandlerItem tanks = tanksOf(inventory.getItem(slot));
            for (int tank = 0; tanks != null && tank < tanks.getTanks(); tank++) {
                stored += drainable(tanks, tanks.getFluidInTank(tank), Integer.MAX_VALUE) / perPoint;
            }
        }
        return stored;
    }

    @Override
    public long take(ServerPlayer player, long amount) {
        int perPoint = ServerConfig.LIQUID_XP_MB_PER_POINT.get();
        long missing = amount;
        Inventory inventory = player.getInventory();
        for (int slot = 0; perPoint > 0 && missing > 0 && slot < inventory.getContainerSize(); slot++) {
            IFluidHandlerItem tanks = tanksOf(inventory.getItem(slot));
            if (tanks != null) {
                missing -= drain(tanks, missing, perPoint);
                inventory.setItem(slot, tanks.getContainer());
            }
        }
        return amount - missing;
    }

    private static long drain(IFluidHandlerItem tanks, long wanted, int perPoint) {
        long paid = 0;
        for (int tank = 0; tank < tanks.getTanks() && paid < wanted; tank++) {
            FluidStack held = tanks.getFluidInTank(tank);
            int exact = (int) Math.min(Integer.MAX_VALUE, (wanted - paid) * perPoint);
            int whole = drainable(tanks, held, exact) / perPoint * perPoint;
            boolean drainsWhole = whole >= perPoint && drainable(tanks, held, whole) == whole;
            int available = drainsWhole ? whole : drainable(tanks, held, Integer.MAX_VALUE);
            if (available >= perPoint) {
                paid += tanks.drain(held.copyWithAmount(available), FluidAction.EXECUTE).getAmount() / perPoint;
            }
        }
        return paid;
    }

    private static int drainable(IFluidHandlerItem tanks, FluidStack held, int limit) {
        if (held.isEmpty() || !held.is(EXPERIENCE)) {
            return 0;
        }
        return tanks.drain(held.copyWithAmount(Math.min(held.getAmount(), limit)), FluidAction.SIMULATE).getAmount();
    }

    private static IFluidHandlerItem tanksOf(ItemStack stack) {
        return stack.getCount() == 1 ? stack.getCapability(Capabilities.FluidHandler.ITEM) : null;
    }
}
