package dev.nostalgiacraft.skills.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue UI_SCALE;
    public static final ModConfigSpec.DoubleValue ZOOM_MIN;
    public static final ModConfigSpec.DoubleValue ZOOM_MAX;
    public static final ModConfigSpec.DoubleValue GLOW_INTENSITY;
    public static final ModConfigSpec.BooleanValue BACKGROUND_DECORATIONS;
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ModConfigSpec.BooleanValue DETAILED_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue HIGH_CONTRAST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        UI_SCALE = builder.comment("Scale of node frames relative to the default").defineInRange("uiScale", 1.0, 0.5, 2.0);
        ZOOM_MIN = builder.comment("Lowest interactive zoom; Fit Tree may go lower down to 0.03")
                .defineInRange("zoomMin", 0.08, 0.03, 1.0);
        ZOOM_MAX = builder.defineInRange("zoomMax", 3.0, 1.0, 6.0);
        GLOW_INTENSITY = builder.defineInRange("glowIntensity", 1.0, 0.0, 2.0);
        BACKGROUND_DECORATIONS = builder.comment("Draw the Astral Observatory scenery (off = plain dark backdrop)")
                .define("backgroundDecorations", true);
        REDUCED_MOTION = builder.comment("Disable parallax, drifting motes and pulsing highlights").define("reducedMotion", false);
        DETAILED_TOOLTIPS = builder.define("detailedTooltips", true);
        HIGH_CONTRAST = builder.comment("Stronger outlines and brighter inactive connections").define("highContrast", false);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
