package dev.nostalgiacraft.skills.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Presentation-only settings. Nothing here can change costs, rules or bonuses. */
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
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        UI_SCALE = b.comment("Scale of node frames relative to the default").defineInRange("uiScale", 1.0, 0.5, 2.0);
        ZOOM_MIN = b.comment("Lowest interactive zoom; Fit Tree may go lower down to 0.08")
                .defineInRange("zoomMin", 0.2, 0.08, 1.0);
        ZOOM_MAX = b.defineInRange("zoomMax", 3.0, 1.0, 6.0);
        GLOW_INTENSITY = b.defineInRange("glowIntensity", 1.0, 0.0, 2.0);
        BACKGROUND_DECORATIONS = b.comment("Draw the Astral Observatory scenery (off = plain dark backdrop)")
                .define("backgroundDecorations", true);
        REDUCED_MOTION = b.comment("Disable parallax, drifting motes and pulsing highlights").define("reducedMotion", false);
        DETAILED_TOOLTIPS = b.define("detailedTooltips", true);
        HIGH_CONTRAST = b.comment("Stronger outlines and brighter inactive connections").define("highContrast", false);
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
