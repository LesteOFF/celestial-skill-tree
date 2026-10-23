package dev.lesteoff.celestialskilltree.block;

import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.registry.ModBlocks;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import dev.lesteoff.celestialskilltree.server.CommunityTree;
import dev.lesteoff.celestialskilltree.server.FluidXpWallet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class XpBasinBlockEntity extends BlockEntity {
    private static final int TANK_CAPACITY_MB = 4_000;
    private static final int DONATION_PERIOD_TICKS = 100;
    private static final String TANK_KEY = "tank";
    private static final String OUTPUT_KEY = "output";
    private static final String ABSORBED_KEY = "absorbed_mb";

    private final FluidTank tank = new FluidTank(TANK_CAPACITY_MB, fluid -> fluid.is(FluidXpWallet.EXPERIENCE)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private long absorbedMb;

    public XpBasinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.XP_BASIN_ENTITY.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    public IItemHandler output() {
        return output;
    }

    public long absorbedMb() {
        return absorbedMb;
    }

    public boolean donates() {
        return getBlockState().is(ModBlocks.COMMUNITY_BASIN.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, XpBasinBlockEntity basin) {
        int perPoint = ServerConfig.LIQUID_XP_MB_PER_POINT.get();
        if (perPoint == 0) {
            return;
        }
        if (basin.donates()) {
            basin.donate(level, perPoint);
        } else {
            basin.condense(perPoint);
        }
    }

    private void condense(int perPoint) {
        long neededMb = (long) ServerConfig.CONDENSER_XP_PER_FRAGMENT.get() * perPoint;
        boolean hasRoom = output.getStackInSlot(0).getCount() < output.getSlotLimit(0);
        if (neededMb == 0 || !hasRoom) {
            return;
        }
        absorbedMb += tank.drain(ServerConfig.BASIN_MB_PER_TICK.get(), FluidAction.EXECUTE).getAmount();
        if (absorbedMb >= neededMb) {
            absorbedMb -= neededMb;
            ItemStack grown = output.getStackInSlot(0).isEmpty() ? new ItemStack(ModItems.STAR_FRAGMENT.get())
                    : output.getStackInSlot(0).copyWithCount(output.getStackInSlot(0).getCount() + 1);
            output.setStackInSlot(0, grown);
        }
    }

    private void donate(Level level, int perPoint) {
        if (CommunityTree.remaining(level.getServer()) * perPoint > absorbedMb) {
            absorbedMb += tank.drain(ServerConfig.BASIN_MB_PER_TICK.get(), FluidAction.EXECUTE).getAmount();
        }
        boolean donatesNow = level.getGameTime() % DONATION_PERIOD_TICKS == 0 && absorbedMb >= perPoint;
        if (donatesNow) {
            absorbedMb -= CommunityTree.contribute(level.getServer(), absorbedMb / perPoint) * perPoint;
            setChanged();
        }
    }

    void collect(Player player) {
        ItemStack fragments = output.extractItem(0, output.getSlotLimit(0), false);
        if (!fragments.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, fragments);
            return;
        }
        int perPoint = Math.max(1, ServerConfig.LIQUID_XP_MB_PER_POINT.get());
        long waiting = (tank.getFluidAmount() + absorbedMb) / perPoint;
        Component status = donates() ? Component.translatable("message.celestialskilltree.community_basin.status", waiting)
                : Component.translatable("message.celestialskilltree.stellar_condenser.status", absorbedMb / perPoint,
                ServerConfig.CONDENSER_XP_PER_FRAGMENT.get(), tank.getFluidAmount() / perPoint);
        player.displayClientMessage(status, true);
    }

    void dropContents() {
        if (level != null) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output.getStackInSlot(0));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TANK_KEY, tank.writeToNBT(registries, new CompoundTag()));
        tag.put(OUTPUT_KEY, output.serializeNBT(registries));
        tag.putLong(ABSORBED_KEY, absorbedMb);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound(TANK_KEY));
        output.deserializeNBT(registries, tag.getCompound(OUTPUT_KEY));
        absorbedMb = tag.getLong(ABSORBED_KEY);
    }
}
