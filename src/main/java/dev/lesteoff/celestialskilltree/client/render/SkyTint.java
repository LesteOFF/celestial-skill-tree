package dev.lesteoff.celestialskilltree.client.render;

import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class SkyTint {
    private static final float FADE_PER_TICK = 1f / 100f;
    private static final Vec3 ECLIPSE_SKY = rgb(0x0B0714);
    private static final Vec3 ECLIPSE_CLOUDS = rgb(0x1A1326);
    private static final double ECLIPSE_SKY_WEIGHT = 0.92;
    private static final double ECLIPSE_CLOUD_WEIGHT = 0.9;
    private static final float ECLIPSE_SKY_LIGHT = 0.25f;
    private static final float ECLIPSE_STARS = 0.5f;
    private static final Vec3 SHOWER_SKY = rgb(0x14234A);
    private static final Vec3 SHOWER_CLOUDS = rgb(0xDEC25F);
    private static final double SHOWER_SKY_WEIGHT = 0.45;
    private static final double SHOWER_CLOUD_WEIGHT = 0.2;

    private static SkyEvent shown = SkyEvent.NONE;
    private static float strength;
    private static float previousStrength;

    private SkyTint() {
    }

    public static void tick(SkyEvent active) {
        previousStrength = strength;
        boolean rising = active != SkyEvent.NONE && (active == shown || strength == 0f);
        if (rising) {
            shown = active;
        }
        strength = Mth.clamp(strength + (rising ? FADE_PER_TICK : -FADE_PER_TICK), 0f, 1f);
    }

    public static boolean active() {
        return strength > 0f || previousStrength > 0f;
    }

    public static float eclipseStrength(float partialTick) {
        return shown == SkyEvent.ECLIPSE ? strength(partialTick) : 0f;
    }

    public static Vec3 sky(Vec3 vanilla, float partialTick) {
        return switch (shown) {
            case ECLIPSE -> vanilla.lerp(ECLIPSE_SKY, strength(partialTick) * ECLIPSE_SKY_WEIGHT);
            case STAR_SHOWER -> vanilla.lerp(SHOWER_SKY, strength(partialTick) * SHOWER_SKY_WEIGHT);
            case NONE -> vanilla;
        };
    }

    public static Vec3 clouds(Vec3 vanilla, float partialTick) {
        return switch (shown) {
            case ECLIPSE -> vanilla.lerp(ECLIPSE_CLOUDS, strength(partialTick) * ECLIPSE_CLOUD_WEIGHT);
            case STAR_SHOWER -> vanilla.lerp(SHOWER_CLOUDS, strength(partialTick) * SHOWER_CLOUD_WEIGHT);
            case NONE -> vanilla;
        };
    }

    public static float skyLight(float vanilla, float partialTick) {
        return Mth.lerp(eclipseStrength(partialTick), vanilla, Math.min(vanilla, ECLIPSE_SKY_LIGHT));
    }

    public static float starBrightness(float vanilla, float partialTick) {
        return switch (shown) {
            case ECLIPSE -> Math.max(vanilla, ECLIPSE_STARS * strength(partialTick));
            case STAR_SHOWER -> Math.min(1f, vanilla * (1f + strength(partialTick)));
            case NONE -> vanilla;
        };
    }

    public static void clear() {
        shown = SkyEvent.NONE;
        strength = 0f;
        previousStrength = 0f;
    }

    private static float strength(float partialTick) {
        return Mth.lerp(partialTick, previousStrength, strength);
    }

    private static Vec3 rgb(int color) {
        return new Vec3((color >> 16 & 0xFF) / 255.0, (color >> 8 & 0xFF) / 255.0, (color & 0xFF) / 255.0);
    }
}
