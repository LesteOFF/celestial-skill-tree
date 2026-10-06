package dev.nostalgiacraft.skills.client.render;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.screen.Camera;
import dev.nostalgiacraft.skills.client.screen.NodeState;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillConnection;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.graph.Vec2;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Draws edges and nodes from the same validated snapshot the server uses for eligibility. Nodes and edge
 * segments outside the viewport are culled; segments crossing the boundary are kept and clipped by scissor.
 */
public final class GraphRenderer {
    private static final String FRAMES = "textures/gui/skill_tree/frames/";
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private GraphRenderer() {
    }

    public static ResourceLocation frameTex(String name) {
        return CACHE.computeIfAbsent(FRAMES + name, p -> ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, p + ".png"));
    }

    public static ResourceLocation iconTex(String id) {
        return CACHE.computeIfAbsent(id, ResourceLocation::parse);
    }

    /** Logical frame diameter in tree units at zoom 1 (design section 5.7). */
    public static double diameter(NodeType t) {
        return switch (t) {
            case MINOR -> 13;
            case NOTABLE -> 17;
            case KEYSTONE -> 24;
            case ROOT -> 30;
        };
    }

    public interface StateLookup {
        NodeState state(String id);
    }

    public static void edges(GuiGraphics g, SkillGraphSnapshot graph, Camera cam, StateLookup states, Set<String> routePreview,
                             double time) {
        boolean hc = ClientConfig.HIGH_CONTRAST.get();
        float base = (float) Math.max(1.0, Math.min(3.0, 1.5 * Math.sqrt(cam.zoomValue())));
        LineBatch inactive = new LineBatch(g);
        for (SkillConnection c : graph.connections()) {
            NodeState a = states.state(c.a());
            NodeState b = states.state(c.b());
            boolean lit = (a == NodeState.ALLOCATED || a == NodeState.PENDING) && (b == NodeState.ALLOCATED || b == NodeState.PENDING);
            if (!lit) {
                polyline(inactive, graph, c, cam, base, hc ? Palette.EDGE_INACTIVE_HC : Palette.EDGE_INACTIVE);
            }
        }
        inactive.draw();
        LineBatch active = new LineBatch(g);
        for (SkillConnection c : graph.connections()) {
            NodeState a = states.state(c.a());
            NodeState b = states.state(c.b());
            boolean bothAllocated = a == NodeState.ALLOCATED && b == NodeState.ALLOCATED;
            boolean pending = !bothAllocated && (a == NodeState.ALLOCATED || a == NodeState.PENDING)
                    && (b == NodeState.ALLOCATED || b == NodeState.PENDING);
            if (bothAllocated) {
                polyline(active, graph, c, cam, base + 1.0f, Palette.ALLOCATED);
            } else if (pending) {
                polyline(active, graph, c, cam, base + 0.5f, Palette.PENDING);
            } else if (routePreview.contains(c.a()) && routePreview.contains(c.b())) {
                polyline(active, graph, c, cam, base, 0x99F3D15C);
            }
        }
        active.draw();
    }

    private static void polyline(LineBatch batch, SkillGraphSnapshot graph, SkillConnection c, Camera cam, float width, int argb) {
        Vec2 p0 = graph.nodes().get(c.a()).position();
        double x0 = cam.toScreenX(p0.x()), y0 = cam.toScreenY(p0.y());
        int n = c.points().size();
        for (int i = 0; i <= n; i++) {
            Vec2 p1 = i < n ? c.points().get(i) : graph.nodes().get(c.b()).position();
            double x1 = cam.toScreenX(p1.x()), y1 = cam.toScreenY(p1.y());
            if (segmentVisible(cam, x0, y0, x1, y1)) {
                batch.segment(x0, y0, x1, y1, width, argb);
            }
            x0 = x1;
            y0 = y1;
        }
    }

    private static boolean segmentVisible(Camera cam, double x0, double y0, double x1, double y1) {
        return Math.max(x0, x1) >= cam.left() && Math.min(x0, x1) <= cam.right()
                && Math.max(y0, y1) >= cam.top() && Math.min(y0, y1) <= cam.bottom();
    }

    /** Screen radius of a node frame. */
    public static double screenRadius(SkillNodeDefinition n, Camera cam) {
        return diameter(n.type()) * cam.zoomValue() * ClientConfig.UI_SCALE.get() / 2;
    }

    public static void node(GuiGraphics g, SkillNodeDefinition n, Camera cam, NodeState state, boolean affordable,
                            boolean focused, boolean searchHit, double time) {
        double sx = cam.toScreenX(n.position().x());
        double sy = cam.toScreenY(n.position().y());
        double r = screenRadius(n, cam);
        if (sx + r * 1.6 < cam.left() || sx - r * 1.6 > cam.right() || sy + r * 1.6 < cam.top() || sy - r * 1.6 > cam.bottom()) {
            return;
        }
        boolean still = ClientConfig.REDUCED_MOTION.get();
        float glow = (float) (double) ClientConfig.GLOW_INTENSITY.get();
        // state halo behind the frame
        if (state == NodeState.ALLOCATED && glow > 0) {
            g.setColor(1f, 1f, 1f, Math.min(1f, 0.85f * glow));
            draw(g, frameTex("ring_allocated"), sx, sy, r * 1.55);
        } else if (state == NodeState.AVAILABLE && affordable) {
            float pulse = still ? 0.8f : (float) (0.65 + 0.25 * Math.sin(time * 2.4));
            g.setColor(1f, 1f, 1f, pulse * Math.max(0.4f, glow));
            draw(g, frameTex("ring_available"), sx, sy, r * 1.4);
        }
        // frame, dimmed when locked or conflicting
        float shade = switch (state) {
            case LOCKED -> 0.42f;
            case CONFLICTING -> 0.5f;
            case AVAILABLE -> affordable ? 1f : 0.7f;
            default -> 1f;
        };
        g.setColor(shade, shade, shade, 1f);
        draw(g, frameTex(n.frame()), sx, sy, r);
        double iconR = r * (n.type() == NodeType.MINOR ? 0.62 : 0.58);
        draw(g, iconTex(n.icon()), sx, sy, iconR);
        g.setColor(1f, 1f, 1f, 1f);
        // state marks drawn over the frame (shape-coded, not colour-only)
        switch (state) {
            case PENDING -> {
                g.pose().pushPose();
                g.pose().translate(sx, sy, 0);
                if (!still) {
                    g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) (time * 0.8)));
                }
                draw(g, frameTex("ring_pending"), 0, 0, r * 1.35);
                g.pose().popPose();
            }
            case CONFLICTING -> draw(g, frameTex("mark_conflict"), sx, sy, r * 0.9);
            case LOCKED -> {
                if (r > 5) {
                    draw(g, frameTex("mark_locked"), sx + r * 0.62, sy + r * 0.62, Math.max(3, r * 0.42));
                }
            }
            default -> {
            }
        }
        if (searchHit) {
            draw(g, frameTex("ring_search"), sx, sy, r * 1.75);
        }
        if (focused) {
            draw(g, frameTex("focus"), sx, sy, r * 1.5 + 2);
        }
    }

    /** Blits a square 32x32-authored texture centred at (cx, cy) with screen radius r. */
    public static void draw(GuiGraphics g, ResourceLocation tex, double cx, double cy, double r) {
        g.pose().pushPose();
        g.pose().translate(cx - r, cy - r, 0);
        float s = (float) (2 * r / 32.0);
        g.pose().scale(s, s, 1f);
        g.blit(tex, 0, 0, 0, 0, 32, 32, 32, 32);
        g.pose().popPose();
    }
}
