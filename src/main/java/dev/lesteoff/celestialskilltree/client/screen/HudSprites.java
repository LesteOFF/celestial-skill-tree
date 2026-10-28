package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import net.minecraft.resources.ResourceLocation;

public final class HudSprites {
    public static final int ICON_SIZE = 9;
    public static final int TOOL_ICON_SIZE = 16;
    public static final ResourceLocation PANEL = hud("glass_panel");
    public static final ResourceLocation PANEL_HIGHLIGHTED = hud("glass_panel_highlighted");
    public static final ResourceLocation BUTTON = hud("glass_button");
    public static final ResourceLocation BUTTON_HIGHLIGHTED = hud("glass_button_highlighted");
    public static final ResourceLocation BUTTON_DISABLED = hud("glass_button_disabled");
    public static final ResourceLocation BUTTON_PRIMARY = hud("glass_primary");
    public static final ResourceLocation BUTTON_PRIMARY_HIGHLIGHTED = hud("glass_primary_highlighted");
    public static final ResourceLocation BUTTON_BUY = hud("glass_buy");
    public static final ResourceLocation BUTTON_BUY_DISABLED = hud("glass_buy_disabled");
    public static final ResourceLocation BUTTON_BUY_HIGHLIGHTED = hud("glass_buy_highlighted");
    public static final ResourceLocation XP_TRACK = hud("xp_track");
    public static final ResourceLocation XP_FILL = hud("xp_fill");
    public static final ResourceLocation HOME = icon("home");
    public static final ResourceLocation FIT = icon("fit");
    public static final ResourceLocation STATS = icon("stats");
    public static final ResourceLocation COMMUNITY = icon("community");
    public static final ResourceLocation SIGN = icon("sign");
    public static final ResourceLocation HIDDEN = icon("hidden");
    public static final ResourceLocation HELP = icon("help");
    public static final ResourceLocation SEARCH = icon("search");
    public static final ResourceLocation POINT = icon("point");

    private HudSprites() {
    }

    static ResourceLocation icon(String name) {
        return hud("icon/" + name);
    }

    private static ResourceLocation hud(String path) {
        return ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, "hud/" + path);
    }
}
