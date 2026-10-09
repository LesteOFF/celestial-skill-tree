package dev.nostalgiacraft.skills.compat.jade;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.block.XpBasinBlockEntity;
import dev.nostalgiacraft.skills.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

final class BasinProgress implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "xp_basin");
    private static final String ABSORBED = "absorbed_points";
    private static final String NEEDED = "needed_points";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof XpBasinBlockEntity basin) {
            int perPoint = Math.max(1, ServerConfig.LIQUID_XP_MB_PER_POINT.get());
            data.putLong(ABSORBED, basin.absorbedMb() / perPoint);
            if (!basin.donates()) {
                data.putInt(NEEDED, ServerConfig.CONDENSER_XP_PER_FRAGMENT.get());
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(ABSORBED)) {
            return;
        }
        Component progress = data.contains(NEEDED)
                ? Component.translatable("jade.nostalgiaskills.condenser_progress", data.getLong(ABSORBED), data.getInt(NEEDED))
                : Component.translatable("jade.nostalgiaskills.basin_waiting", data.getLong(ABSORBED));
        tooltip.add(progress.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
