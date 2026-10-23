package dev.lesteoff.celestialskilltree.compat.reliquary;

import dev.lesteoff.celestialskilltree.server.ComponentXpWallet;
import dev.lesteoff.celestialskilltree.server.XpWallets;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class ReliquaryCompat {
    public static final String MOD_ID = "reliquary";
    private static final ResourceLocation HERO_MEDALLION = ResourceLocation.fromNamespaceAndPath(MOD_ID, "hero_medallion");
    private static final ResourceLocation EXPERIENCE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "experience");

    private ReliquaryCompat() {
    }

    public static void register() {
        XpWallets.register(new ComponentXpWallet(MOD_ID, Set.of(HERO_MEDALLION), EXPERIENCE, Math::toIntExact));
    }
}
