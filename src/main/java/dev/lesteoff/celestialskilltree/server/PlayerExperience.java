package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.progression.XpMath;
import net.minecraft.server.level.ServerPlayer;


public final class PlayerExperience {
    private PlayerExperience() {
    }

    public static void set(ServerPlayer player, long total) {
        XpMath.LevelState levelState = XpMath.fromTotal(Math.max(0, total));
        player.setExperienceLevels(levelState.level());
        player.setExperiencePoints((int) (total - XpMath.totalForLevel(levelState.level())));
        player.totalExperience = levelState.total();
    }
}
