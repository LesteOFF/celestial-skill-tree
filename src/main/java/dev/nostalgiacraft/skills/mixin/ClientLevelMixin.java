package dev.nostalgiacraft.skills.mixin;

import dev.nostalgiacraft.skills.client.render.SkyTint;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void nostalgiaskills$tintSky(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(SkyTint.sky(callback.getReturnValue(), partialTick));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void nostalgiaskills$tintClouds(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(SkyTint.clouds(callback.getReturnValue(), partialTick));
    }

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void nostalgiaskills$dimSkyLight(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(SkyTint.skyLight(callback.getReturnValue(), partialTick));
    }

    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void nostalgiaskills$showStars(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(SkyTint.starBrightness(callback.getReturnValue(), partialTick));
    }
}
