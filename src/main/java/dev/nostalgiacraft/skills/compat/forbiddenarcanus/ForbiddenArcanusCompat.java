package dev.nostalgiacraft.skills.compat.forbiddenarcanus;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.registry.WornItems;
import dev.nostalgiacraft.skills.server.XpWallets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ForbiddenArcanusCompat {
    public static final String MOD_ID = "forbidden_arcanus";
    private static final ResourceLocation XPETRIFIED_ORB = ResourceLocation.fromNamespaceAndPath(MOD_ID, "xpetrified_orb");
    private static final String ESSENCE_HELPER = "com.stal111.forbidden_arcanus.common.essence.EssenceHelper";
    private static final String ESSENCE_TYPE = "com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType";

    private ForbiddenArcanusCompat() {
    }

    public static void register() {
        Reflection.requireTypes(ESSENCE_HELPER, ESSENCE_TYPE);
        XpWallets.register(new OrbWallet());
    }

    private static final class OrbWallet implements XpWallets.Wallet {
        @Override
        public long stored(ServerPlayer player) {
            return OptionalEvents.guarded(MOD_ID, 0L, () -> orbs(player).stream().mapToLong(orb -> xpIn(orb) * orb.getCount()).sum());
        }

        @Override
        public long take(ServerPlayer player, long amount) {
            return OptionalEvents.guarded(MOD_ID, 0L, () -> breakOrbs(player, amount));
        }

        private long breakOrbs(ServerPlayer player, long amount) {
            long released = 0;
            for (ItemStack orb : orbs(player)) {
                while (released < amount && !orb.isEmpty() && xpIn(orb) > 0) {
                    released += xpIn(orb);
                    orb.shrink(1);
                }
            }
            return released;
        }

        private List<ItemStack> orbs(ServerPlayer player) {
            return WornItems.carriedBy(player).stream()
                    .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(XPETRIFIED_ORB)).toList();
        }

        private long xpIn(ItemStack orb) {
            return (int) Reflection.callStatic(ESSENCE_HELPER, "getEssenceAmount", orb, Reflection.staticField(ESSENCE_TYPE, "EXPERIENCE"));
        }
    }
}
