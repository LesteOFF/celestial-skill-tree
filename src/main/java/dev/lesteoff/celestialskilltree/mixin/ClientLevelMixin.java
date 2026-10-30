package dev.lesteoff.celestialskilltree.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lesteoff.celestialskilltree.client.render.SkyTint;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ClientLevel.class, priority = 500)
public abstract class ClientLevelMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 celestialskilltree$tintSky(Vec3 color, Vec3 position, float partialTick) {
        return SkyTint.sky(color, partialTick);
    }

    @ModifyReturnValue(method = "getCloudColor", at = @At("RETURN"))
    private Vec3 celestialskilltree$tintClouds(Vec3 color, float partialTick) {
        return SkyTint.clouds(color, partialTick);
    }

    @ModifyReturnValue(method = "getSkyDarken", at = @At("RETURN"))
    private float celestialskilltree$dimSkyLight(float skyLight, float partialTick) {
        return SkyTint.skyLight(skyLight, partialTick);
    }

    @ModifyReturnValue(method = "getStarBrightness", at = @At("RETURN"))
    private float celestialskilltree$showStars(float brightness, float partialTick) {
        return SkyTint.starBrightness(brightness, partialTick);
    }
}
