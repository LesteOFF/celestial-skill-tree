package dev.nostalgiacraft.skills.compat.enhancedbossbars;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.EliteBossBars;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.Map;

public final class EnhancedBossBarsCompat {
    public static final String MOD_ID = "enhancedbossbars";
    private static final String MOD_CLASS = "com.nakuring.enhancedbossbars.EnhancedBossBars";
    private static final String BARS = "com.nakuring.enhancedbossbars.BossBars";
    private static final String STYLE_NAME = "Constellated";
    private static final String PROGRESS_COLOR = "purple";
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID,
            "textures/gui/boss_bars/constellated.png");
    private static final int HEALTH_WIDTH = 169;
    private static final int HEALTH_HEIGHT = 5;
    private static final int HEALTH_OFFSET_X = 0;
    private static final int HEALTH_Y = 16;
    private static final int OVERLAY_WIDTH = 189;
    private static final int OVERLAY_HEIGHT = 48;
    private static final int OVERLAY_Y = 1;
    private static final double NO_SECOND_PHASE = 0.0;

    private EnhancedBossBarsCompat() {
    }

    public static void registerStyle() {
        if (ModList.get().isLoaded(MOD_ID)) {
            OptionalEvents.guard(MOD_ID, EnhancedBossBarsCompat::addConstellatedStyle);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addConstellatedStyle() {
        Map<String, Object> styles = (Map<String, Object>) Reflection.staticField(BARS, "BossBarsMap");
        Map<String, ResourceLocation> colors = (Map<String, ResourceLocation>) Reflection.staticField(BARS, "BossBarColors");
        Map<String, String> translationKeys = (Map<String, String>) Reflection.hiddenStaticField(MOD_CLASS, "BossNametoTranslatable");
        styles.put(STYLE_NAME, Reflection.construct(BARS, HEALTH_WIDTH, HEALTH_HEIGHT, HEALTH_OFFSET_X, HEALTH_Y, OVERLAY_WIDTH,
                OVERLAY_HEIGHT, OVERLAY_Y, NO_SECOND_PHASE, colors.get(PROGRESS_COLOR), OVERLAY, null, null));
        translationKeys.put(STYLE_NAME, EliteBossBars.TITLE_KEY);
    }
}
