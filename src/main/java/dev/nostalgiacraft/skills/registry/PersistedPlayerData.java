package dev.nostalgiacraft.skills.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

public final class PersistedPlayerData {
    private PersistedPlayerData() {
    }

    public static CompoundTag read(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static void update(Player player, Consumer<CompoundTag> change) {
        CompoundTag persisted = read(player);
        change.accept(persisted);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
