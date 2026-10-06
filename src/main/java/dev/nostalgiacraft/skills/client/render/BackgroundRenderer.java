package dev.nostalgiacraft.skills.client.render;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Astral Observatory backdrop, composited from bundled layers (see ART_DIRECTION.md):
 * full-frame layers use "cover" scaling with overscan; peripheral objects (moon, fragments, astrolabe, platform)
 * are positioned by normalised anchors and scaled by window height so they never stretch on ultrawide or tall
 * windows. Parallax follows the camera pan only (never the graph zoom) and is clamped inside the overscan.
 */
public final class BackgroundRenderer {
    private static final String DIR = "textures/gui/skill_tree/background/";
    public static final ResourceLocation SPACE = tex("space_base");
    public static final ResourceLocation STARS = tex("stars_far");
    public static final ResourceLocation NEBULA = tex("nebula_far");
    public static final ResourceLocation MOON = tex("moon_crescent");
    public static final ResourceLocation ASTROLABE = tex("astrolabe_ghost");
    public static final ResourceLocation RUINS = tex("observatory_ruins");
    public static final ResourceLocation PLATFORM = tex("observatory_platform");
    public static final ResourceLocation MOTES = tex("astral_motes");

    static final int BASE_W = 2048;
    static final int BASE_H = 1152;
    private static final double OVERSCAN = 0.06;

    private BackgroundRenderer() {
    }

    private static ResourceLocation tex(String n) {
        return ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, DIR + n + ".png");
    }

    /**
     * @param panX,panY camera centre in tree units (drives parallax)
     * @param timeSeconds frame-rate independent animation clock
     */
    public static void render(GuiGraphics g, int w, int h, double panX, double panY, double timeSeconds) {
        g.fill(0, 0, w, h, Palette.DEEP_SPACE);
        if (!ClientConfig.BACKGROUND_DECORATIONS.get()) {
            return;
        }
        boolean still = ClientConfig.REDUCED_MOTION.get();
        double px = still ? 0 : panX;
        double py = still ? 0 : panY;
        cover(g, SPACE, w, h, 0, 0);
        cover(g, STARS, w, h, px * 0.02, py * 0.02);
        cover(g, NEBULA, w, h, px * 0.04, py * 0.04);
        double s = h / (double) BASE_H;
        // distant astrolabe near the centre (0.50, 0.52), very faint
        sprite(g, ASTROLABE, w * 0.50 - offset(px, 0.06, w), h * 0.52 - offset(py, 0.06, h), 760 * s, 760 * s, 0, 0, 1024, 1024, 1024, 1024);
        // fractured moon near the lower-left perimeter, centre (0.10, 0.91); ~21% of the 16:9 width
        double moon = 440 * s;
        sprite(g, MOON, w * 0.10 - offset(px, 0.06, w), h * 0.91 - offset(py, 0.06, h), moon, moon, 0, 0, 512, 512, 512, 512);
        // floating arch fragments near the outer corners (atlas of four 256x256 cells)
        double f = 190 * s;
        double fx = offset(px, 0.06, w), fy = offset(py, 0.06, h);
        sprite(g, RUINS, w * 0.06 - fx, h * 0.12 - fy, f, f, 0, 0, 256, 256, 512, 512);
        sprite(g, RUINS, w * 0.93 - fx, h * 0.10 - fy, f, f, 256, 0, 256, 256, 512, 512);
        sprite(g, RUINS, w * 0.95 - fx, h * 0.62 - fy, f * 0.8, f * 0.8, 0, 256, 256, 256, 512, 512);
        sprite(g, RUINS, w * 0.30 - fx, h * 0.97 - fy, f * 0.7, f * 0.7, 256, 256, 256, 256, 512, 512);
        // weathered platform edge along the bottom, tiled horizontally (8% parallax)
        double ph = 150 * s;
        double pw = 1024 * (ph / 192.0);
        double off = offset(px, 0.08, w) % pw;
        int y0 = (int) Math.round(h - ph + offset(py, 0.08, h) * 0.3);
        for (double x = -pw - off; x < w + pw; x += pw) {
            sprite(g, PLATFORM, x + pw / 2, y0 + ph / 2, pw, ph, 0, 0, 1024, 192, 1024, 192);
        }
        motes(g, w, h, still ? 0 : timeSeconds);
    }

    private static double offset(double pan, double factor, int size) {
        double max = size * OVERSCAN * 0.5;
        return Mth.clamp(pan * factor * 0.5, -max, max);
    }

    /** Full-frame layer: aspect-preserving cover with overscan so parallax never reveals an edge. */
    private static void cover(GuiGraphics g, ResourceLocation tex, int w, int h, double ox, double oy) {
        double scale = Math.max(w / (double) BASE_W, h / (double) BASE_H) * (1 + OVERSCAN);
        double dw = BASE_W * scale;
        double dh = BASE_H * scale;
        double maxX = (dw - w) / 2;
        double maxY = (dh - h) / 2;
        double x = (w - dw) / 2 - Mth.clamp(ox, -maxX, maxX);
        double y = (h - dh) / 2 - Mth.clamp(oy, -maxY, maxY);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) scale, (float) scale, 1f);
        g.blit(tex, 0, 0, 0, 0, BASE_W, BASE_H, BASE_W, BASE_H);
        g.pose().popPose();
    }

    /** Draws a texture region centred at (cx, cy) with the given size (uniform scale preserved by callers). */
    static void sprite(GuiGraphics g, ResourceLocation tex, double cx, double cy, double dw, double dh,
                       int u, int v, int uw, int vh, int tw, int th) {
        g.pose().pushPose();
        g.pose().translate(cx - dw / 2, cy - dh / 2, 0);
        g.pose().scale((float) (dw / uw), (float) (dh / vh), 1f);
        g.blit(tex, 0, 0, u, v, uw, vh, tw, th);
        g.pose().popPose();
    }

    /** Sparse, dim motes drifting slowly along the outer edges (deterministic seed, frame-rate independent). */
    private static void motes(GuiGraphics g, int w, int h, double t) {
        long seed = 0x5EEDA57L;
        for (int i = 0; i < 14; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double r1 = ((seed >>> 11) & 0xFFFF) / 65535.0;
            double r2 = ((seed >>> 27) & 0xFFFF) / 65535.0;
            double edge = r1 < 0.5 ? r1 * 0.24 : 0.76 + (r1 - 0.5) * 0.48; // keep to the outer quarter
            double x = (edge + Math.sin(t * 0.05 + i) * 0.01) * w;
            double y = ((r2 + t * 0.004 * (0.5 + r1)) % 1.0) * h;
            int cell = i % 4;
            double size = (6 + 4 * r2) * h / 540.0;
            sprite(g, MOTES, x, y, size, size, cell * 16, 0, 16, 16, 64, 16);
        }
    }
}
