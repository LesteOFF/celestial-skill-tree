package dev.nostalgiacraft.skills.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue UI_SCALE;
    public static final ModConfigSpec.DoubleValue ZOOM_MIN;
    public static final ModConfigSpec.DoubleValue ZOOM_MAX;
    public static final ModConfigSpec.DoubleValue ZOOM_STEP;
    public static final ModConfigSpec.DoubleValue PAN_SPEED;
    public static final ModConfigSpec.DoubleValue GLOW_INTENSITY;
    public static final ModConfigSpec.DoubleValue UI_SOUND_VOLUME;
    public static final ModConfigSpec.BooleanValue BACKGROUND_DECORATIONS;
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ModConfigSpec.BooleanValue DETAILED_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue HIGH_CONTRAST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("tree");
        UI_SCALE = builder.comment("Scale of node frames relative to the default").defineInRange("uiScale", 1.0, 0.5, 2.0);
        ZOOM_MIN = builder.comment("Lowest interactive zoom; Fit Tree may go lower down to 0.03")
                .defineInRange("zoomMin", 0.08, 0.03, 1.0);
        ZOOM_MAX = builder.comment("Highest interactive zoom").defineInRange("zoomMax", 3.0, 1.0, 6.0);
        ZOOM_STEP = builder.comment("Zoom factor applied by one scroll notch or one press of the zoom keys")
                .defineInRange("zoomStep", 1.15, 1.02, 1.6);
        PAN_SPEED = builder.comment("Screen pixels per second the tree scrolls while a pan key (WASD) is held")
                .defineInRange("panSpeed", 320.0, 40.0, 2000.0);
        DETAILED_TOOLTIPS = builder.comment("Always list every effect in node tooltips; when off, hold Shift to see them")
                .define("detailedTooltips", false);
        builder.pop();
        builder.push("look");
        GLOW_INTENSITY = builder.comment("Brightness of the glow around learned and affordable nodes; 0 turns every glow off")
                .defineInRange("glowIntensity", 1.0, 0.0, 2.0);
        BACKGROUND_DECORATIONS = builder.comment("Draw the Astral Observatory scenery (off = plain dark backdrop)")
                .define("backgroundDecorations", true);
        REDUCED_MOTION = builder.comment("Disable parallax, drifting motes and pulsing highlights").define("reducedMotion", false);
        HIGH_CONTRAST = builder.comment("Brighter locked nodes, inactive connections and secondary text").define("highContrast", false);
        builder.pop();
        builder.push("feedback");
        UI_SOUND_VOLUME = builder.comment("Volume of the skill tree interface sounds; 0 mutes them")
                .defineInRange("uiSoundVolume", 0.5, 0.0, 1.0);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
