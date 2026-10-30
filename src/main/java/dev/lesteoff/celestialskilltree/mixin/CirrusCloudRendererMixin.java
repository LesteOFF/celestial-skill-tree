package dev.lesteoff.celestialskilltree.mixin;

import dev.lesteoff.celestialskilltree.client.render.SkyTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.jvn.cirrus.client.CirrusCloudRenderer", remap = false)
public abstract class CirrusCloudRendererMixin {
    @Inject(method = "celestialSunWeight", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void celestialskilltree$hideSunDuringEclipse(float sunHeight, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(SkyTint.sunlight(callback.getReturnValue()));
    }
}
