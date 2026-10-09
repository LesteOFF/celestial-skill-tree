package dev.nostalgiacraft.skills.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class XpWallets {
    public interface Wallet {
        long stored(ServerPlayer player);

        long take(ServerPlayer player, long amount);
    }

    private static final List<Wallet> WALLETS = new CopyOnWriteArrayList<>();

    private XpWallets() {
    }

    public static void register(Wallet wallet) {
        WALLETS.add(wallet);
    }

    public static long stored(ServerPlayer player) {
        return WALLETS.stream().mapToLong(wallet -> Math.max(0, wallet.stored(player))).sum();
    }

    public static long take(ServerPlayer player, long amount) {
        long missing = amount;
        for (Wallet wallet : WALLETS) {
            if (missing > 0) {
                missing -= Math.max(0, wallet.take(player, missing));
            }
        }
        return amount - missing;
    }
}
