package dev.lesteoff.celestialskilltree.mixin;

import dev.lesteoff.celestialskilltree.server.SkyEvents;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Unique
    private static final int CELESTIALSKILLTREE_NIGHT_SKY_DARKEN = 11;

    @Shadow
    private int skyDarken;

    @Inject(method = "updateSkyBrightness", at = @At("TAIL"))
    private void celestialskilltree$darkenDuringEclipse(CallbackInfo callback) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide() && level.dimension() == Level.OVERWORLD && SkyEvents.darkensDay()) {
            skyDarken = CELESTIALSKILLTREE_NIGHT_SKY_DARKEN;
        }
    }
}
