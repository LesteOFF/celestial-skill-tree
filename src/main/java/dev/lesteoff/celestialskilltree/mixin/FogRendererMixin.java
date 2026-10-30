package dev.lesteoff.celestialskilltree.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.render.SkyTint;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"), order = 3000)
    private static void celestialskilltree$tintFogAfterOtherSkies(Camera camera, float partialTick, ClientLevel level, int renderDistanceChunks,
                                                                float darkenWorldAmount, CallbackInfo callback) {
        if (!SkyTint.active()) {
            return;
        }
        Vec3 tinted = SkyTint.sky(new Vec3(fogRed, fogGreen, fogBlue), partialTick);
        fogRed = (float) tinted.x;
        fogGreen = (float) tinted.y;
        fogBlue = (float) tinted.z;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
    }
}
