package dev.lesteoff.celestialskilltree.client;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.fml.ModList;

import java.util.UUID;

public final class PlayerGestures {
    private static final String ANIMATOR = "playeranimator";
    private static final String ACCESS = "dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess";
    private static final String REGISTRY = "dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry";
    private static final int LAYER_PRIORITY = 4_200;

    private PlayerGestures() {
    }

    public static void play(UUID playerId, String animation) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation animationId = ResourceLocation.tryBuild(CelestialSkillTree.MODID, animation);
        boolean playable = animationId != null && minecraft.level != null;
        if (playable && minecraft.level.getPlayerByUUID(playerId) instanceof AbstractClientPlayer player) {
            if (ModList.get().isLoaded(ANIMATOR)) {
                OptionalEvents.guard(ANIMATOR, () -> animate(player, animationId));
            } else {
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private static void animate(AbstractClientPlayer player, ResourceLocation animation) {
        Object playable = Reflection.callStatic(REGISTRY, "getAnimation", animation);
        if (playable == null) {
            return;
        }
        Object layers = Reflection.callStatic(ACCESS, "getPlayerAnimLayer", player);
        Reflection.call(layers, "removeLayer", LAYER_PRIORITY);
        Reflection.call(layers, "addAnimLayer", LAYER_PRIORITY, Reflection.call(playable, "playAnimation"));
    }
}
