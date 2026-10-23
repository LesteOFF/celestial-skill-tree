package dev.lesteoff.celestialskilltree.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.client.screen.Camera;
import dev.lesteoff.celestialskilltree.client.screen.NodeState;
import dev.lesteoff.celestialskilltree.client.screen.TreeRegion;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import dev.lesteoff.celestialskilltree.graph.NodeType;
import dev.lesteoff.celestialskilltree.graph.SkillConnection;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.graph.Vec2;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class GraphRenderer {
    private static final String FRAMES = "textures/gui/skill_tree/frames/";
    private static final int TEXTURE_SIZE = 32;
    private static final int ROUTE_PREVIEW_COLOR = 0x99F3D15C;
    private static final double CULLING_REACH = 1.6;
    private static final double MIN_LOCK_MARK_RADIUS = 5;
    private static final double MIN_DETAIL_RADIUS = 5;
    private static final double MIN_ICON_RADIUS = 3;
    private static final double SMALL_NODE_RADIUS = 8;
    private static final double SMALL_NODE_ICON_SCALE = 0.8;
    private static final int PORTAL_LINK_TILE_WIDTH = 16;
    private static final int PORTAL_LINK_LANE_HEIGHT = 8;
    private static final int PORTAL_LINK_LANE_GAP = 1;
    private static final double PORTAL_LINK_SPEED = 24;
    private static final float DISABLED_SHADE = 0.42f;
    private static final float DORMANT_DISK_ALPHA = 0.55f;
    private static final double DISK_PER_RADIUS = 1.45;
    private static final int INFALL_LAYERS = 3;
    private static final double INFALL_SECONDS = 2.4;
    private static final double INFALL_START = 2.0;
    private static final double INFALL_END = 0.9;
    private static final double INFALL_TWIST = 2.5;
    private static final double LENS_PER_RADIUS = 1.3;
    private static final double LENS_BREATH = 0.05;
    private static final double PIP_RADIUS_PER_NODE_RADIUS = 0.24;
    private static final double MIN_PIP_RADIUS = 2.5;
    private static final double PIP_STEP_PER_PIP_RADIUS = 1.45;
    private static final double PIP_DROP_PER_RADIUS = 1.3;
    private static final float[] NO_TINT = {1f, 1f, 1f};
    private static final float[] HIDDEN_TINT = {0.72f, 0.5f, 1f};
    private static final float[] SILHOUETTE_TINT = {0.2f, 0.1f, 0.34f};
    private static final float[] PORTAL_TINT = {0.5f, 0.84f, 0.91f};
    private static final Map<String, ResourceLocation> FRAME_CACHE = new HashMap<>();
    private static final Map<String, ResourceLocation> ICON_CACHE = new HashMap<>();

    public interface StateLookup {
        NodeState state(String id);
    }

    public record Ranks(int owned, int pending, int max) {
        public static final Ranks NONE = new Ranks(0, 0, 1);

        boolean shown() {
            return max > 1;
        }
    }

    public interface Fade {
        Fade NONE = (screenX, screenY) -> 1f;

        float at(double screenX, double screenY);
    }

    private GraphRenderer() {
    }

    public static ResourceLocation frameTex(String name) {
        return FRAME_CACHE.computeIfAbsent(name,
                frame -> ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, FRAMES + frame + ".png"));
    }

    public static ResourceLocation iconTex(String id) {
        return ICON_CACHE.computeIfAbsent(id, ResourceLocation::parse);
    }

    public static double diameter(NodeType nodeType) {
        return switch (nodeType) {
            case MINOR -> 32;
            case NOTABLE -> 34;
            case KEYSTONE -> 48;
            case ROOT -> 60;
            case PORTAL -> 90;
        };
    }

    private static boolean held(NodeState state) {
        return state == NodeState.ALLOCATED || state == NodeState.PENDING;
    }

    public static void edges(GuiGraphics graphics, SkillGraphSnapshot graph, Camera camera, StateLookup states,
                             Set<String> routePreview, Fade fade) {
        float baseWidth = (float) Math.max(1.0, Math.min(3.0, 1.5 * Math.sqrt(camera.zoomValue())));
        int inactiveColor = ClientConfig.HIGH_CONTRAST.get() ? Palette.EDGE_INACTIVE_HC : Palette.EDGE_INACTIVE;
        LineBatch inactive = new LineBatch(graphics);
        LineBatch active = new LineBatch(graphics);
        for (SkillConnection connection : graph.connections()) {
            NodeState a = states.state(connection.a());
            NodeState b = states.state(connection.b());
            boolean unseen = a == NodeState.HIDDEN || b == NodeState.HIDDEN;
            if (connection.isPortal() || unseen || entersHiddenRegion(graph, connection) || !lit(graph, connection, camera, fade)) {
                continue;
            }
            if (a == NodeState.ALLOCATED && b == NodeState.ALLOCATED) {
                polyline(active, graph, connection, camera, baseWidth + 1.0f, Palette.ALLOCATED);
            } else if (held(a) && held(b)) {
                polyline(active, graph, connection, camera, baseWidth + 0.5f, Palette.PENDING);
            } else {
                polyline(inactive, graph, connection, camera, baseWidth, inactiveColor);
                if (routePreview.contains(connection.a()) && routePreview.contains(connection.b())) {
                    polyline(active, graph, connection, camera, baseWidth, ROUTE_PREVIEW_COLOR);
                }
            }
        }
        inactive.draw();
        active.draw();
    }

    private static boolean lit(SkillGraphSnapshot graph, SkillConnection connection, Camera camera, Fade fade) {
        return grown(graph.nodes().get(connection.a()), camera, fade) && grown(graph.nodes().get(connection.b()), camera, fade);
    }

    private static boolean grown(SkillNodeDefinition node, Camera camera, Fade fade) {
        return fade.at(camera.toScreenX(node.position().x()), camera.toScreenY(node.position().y())) >= 1f;
    }

    private static boolean entersHiddenRegion(SkillGraphSnapshot graph, SkillConnection connection) {
        return TreeRegion.isHidden(graph.nodes().get(connection.a())) != TreeRegion.isHidden(graph.nodes().get(connection.b()));
    }

    private static void polyline(LineBatch batch, SkillGraphSnapshot graph, SkillConnection connection, Camera camera,
                                 float width, int argb) {
        Vec2 from = graph.nodes().get(connection.a()).position();
        for (Vec2 bend : connection.points()) {
            segment(batch, camera, from, bend, width, argb);
            from = bend;
        }
        segment(batch, camera, from, graph.nodes().get(connection.b()).position(), width, argb);
    }

    private static void segment(LineBatch batch, Camera camera, Vec2 from, Vec2 to, float width, int argb) {
        double fromX = camera.toScreenX(from.x());
        double fromY = camera.toScreenY(from.y());
        double toX = camera.toScreenX(to.x());
        double toY = camera.toScreenY(to.y());
        if (segmentVisible(camera, fromX, fromY, toX, toY)) {
            batch.segment(fromX, fromY, toX, toY, width, argb);
        }
    }

    private static boolean segmentVisible(Camera camera, double x0, double y0, double x1, double y1) {
        return Math.max(x0, x1) >= camera.left() && Math.min(x0, x1) <= camera.right()
                && Math.max(y0, y1) >= camera.top() && Math.min(y0, y1) <= camera.bottom();
    }

    public static void portalLinks(GuiGraphics graphics, SkillGraphSnapshot graph, Camera camera, StateLookup states,
                                   Fade fade, double time) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (SkillConnection connection : graph.connections()) {
            if (connection.isPortal() && held(states.state(connection.a())) && held(states.state(connection.b()))
                    && lit(graph, connection, camera, fade)) {
                portalLink(graphics, graph, connection, camera, time);
            }
        }
        RenderSystem.disableBlend();
    }

    private static void portalLink(GuiGraphics graphics, SkillGraphSnapshot graph, SkillConnection connection, Camera camera,
                                   double time) {
        Vec2 from = graph.nodes().get(connection.a()).position();
        Vec2 to = graph.nodes().get(connection.b()).position();
        double fromX = camera.toScreenX(from.x());
        double fromY = camera.toScreenY(from.y());
        double deltaX = camera.toScreenX(to.x()) - fromX;
        double deltaY = camera.toScreenY(to.y()) - fromY;
        int length = (int) Math.hypot(deltaX, deltaY);
        float scroll = ClientConfig.REDUCED_MOTION.get() ? 0 : (float) ((time * PORTAL_LINK_SPEED) % PORTAL_LINK_TILE_WIDTH);
        ResourceLocation texture = frameTex("portal_link");
        graphics.pose().pushPose();
        graphics.pose().translate(fromX, fromY, 0);
        graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(deltaY, deltaX)));
        graphics.blit(texture, 0, -PORTAL_LINK_LANE_HEIGHT - PORTAL_LINK_LANE_GAP, length, PORTAL_LINK_LANE_HEIGHT, -scroll, 0,
                length, PORTAL_LINK_LANE_HEIGHT, PORTAL_LINK_TILE_WIDTH, 2 * PORTAL_LINK_LANE_HEIGHT);
        graphics.blit(texture, 0, PORTAL_LINK_LANE_GAP, length, PORTAL_LINK_LANE_HEIGHT, scroll, 0,
                length, PORTAL_LINK_LANE_HEIGHT, -PORTAL_LINK_TILE_WIDTH, 2 * PORTAL_LINK_LANE_HEIGHT);
        graphics.pose().popPose();
    }

    public static double screenRadius(SkillNodeDefinition node, Camera camera) {
        double scaled = diameter(node.type()) * camera.zoomValue() * ClientConfig.UI_SCALE.get() / 2;
        double minimum = switch (node.type()) {
            case MINOR -> 3.2;
            case NOTABLE -> 3.4;
            case KEYSTONE -> 4.4;
            case ROOT -> 5.2;
            case PORTAL -> 8.0;
        };
        return Math.max(scaled, minimum);
    }

    public static void node(SpriteBatch batch, SkillNodeDefinition node, Camera camera, NodeState state,
                            boolean affordable, boolean focused, boolean searchHit, boolean portalActive, boolean disabled,
                            Ranks ranks, Fade fade, double time) {
        double x = camera.toScreenX(node.position().x());
        double y = camera.toScreenY(node.position().y());
        double radius = screenRadius(node, camera) * fade.at(x, y);
        if (radius <= 0) {
            return;
        }
        double reach = radius * CULLING_REACH;
        boolean offScreen = x + reach < camera.left() || x - reach > camera.right()
                || y + reach < camera.top() || y - reach > camera.bottom();
        if (offScreen) {
            return;
        }
        boolean inHiddenRegion = TreeRegion.isHidden(node);
        if (state == NodeState.HIDDEN) {
            if (inHiddenRegion) {
                batch.add(SpriteBatch.Layer.FRAME, frameTex(node.frame()), x, y, radius, 0,
                        SILHOUETTE_TINT[0], SILHOUETTE_TINT[1], SILHOUETTE_TINT[2], 1f);
            }
            return;
        }
        boolean detailed = radius >= MIN_DETAIL_RADIUS;
        if (detailed && node.traits().amplifiesNeighbours()) {
            drawGravityWell(batch, x, y, radius, held(state), time);
        }
        if (detailed && node.type() == NodeType.PORTAL && (portalActive || held(state))) {
            drawPortalSwirl(batch, x, y, radius, portalActive, time);
        }
        if (!disabled) {
            drawGlow(batch, state, affordable, x, y, radius, time);
        }
        float shade = disabled ? DISABLED_SHADE : shade(state, affordable);
        float[] tint = inHiddenRegion ? HIDDEN_TINT : NO_TINT;
        float red = shade * tint[0];
        float green = shade * tint[1];
        float blue = shade * tint[2];
        batch.add(SpriteBatch.Layer.FRAME, frameTex(node.frame()), x, y, radius, 0, red, green, blue, 1f);
        if (radius >= MIN_ICON_RADIUS) {
            batch.add(SpriteBatch.Layer.ICON, iconTex(node.icon()), x, y, radius * iconScale(node, radius), 0, red, green, blue, 1f);
        }
        if (detailed && node.traits().amplifiesNeighbours()) {
            drawLens(batch, x, y, radius, held(state), time);
        }
        drawStateMark(batch, state, x, y, radius, time);
        if (detailed && ranks.shown()) {
            drawRankPips(batch, ranks, x, y, radius);
        }
        if (searchHit) {
            batch.add(SpriteBatch.Layer.FRONT, frameTex("ring_search"), x, y, radius * 1.75);
        }
        if (focused) {
            batch.add(SpriteBatch.Layer.FRONT, frameTex("focus"), x, y, radius * 1.5 + 2);
        }
    }

    private static void drawRankPips(SpriteBatch batch, Ranks ranks, double x, double y, double radius) {
        double pipRadius = Math.max(MIN_PIP_RADIUS, radius * PIP_RADIUS_PER_NODE_RADIUS);
        double step = pipRadius * PIP_STEP_PER_PIP_RADIUS;
        double first = x - step * (ranks.max() - 1) / 2;
        double row = y + radius * PIP_DROP_PER_RADIUS;
        for (int index = 0; index < ranks.max(); index++) {
            batch.add(SpriteBatch.Layer.FRONT, frameTex(pipTexture(ranks, index)), first + index * step, row, pipRadius);
        }
    }

    private static String pipTexture(Ranks ranks, int index) {
        if (index < ranks.owned()) {
            return "rank_pip_owned";
        }
        return index < ranks.owned() + ranks.pending() ? "rank_pip_pending" : "rank_pip_empty";
    }

    private static void drawGlow(SpriteBatch batch, NodeState state, boolean affordable, double x, double y,
                                 double radius, double time) {
        float glow = ClientConfig.GLOW_INTENSITY.get().floatValue();
        if (state == NodeState.ALLOCATED && glow > 0) {
            batch.add(SpriteBatch.Layer.GLOW, frameTex("ring_allocated"), x, y, radius * 1.55, 0, 1f, 1f, 1f, Math.min(1f, 0.85f * glow));
        } else if (state == NodeState.AVAILABLE && affordable && glow > 0) {
            float pulse = ClientConfig.REDUCED_MOTION.get() ? 0.8f : (float) (0.65 + 0.25 * Math.sin(time * 2.4));
            batch.add(SpriteBatch.Layer.GLOW, frameTex("ring_available"), x, y, radius * 1.4, 0, 1f, 1f, 1f, Math.min(1f, pulse * glow));
        }
    }

    private static void drawPortalSwirl(SpriteBatch batch, double x, double y, double radius, boolean active, double time) {
        boolean still = ClientConfig.REDUCED_MOTION.get();
        double speed = active ? 2.4 : 0.6;
        float pulse = still ? 0.8f : (float) (0.7 + 0.3 * Math.sin(time * 3));
        float alpha = active ? pulse : 0.45f;
        batch.add(SpriteBatch.Layer.BACK, frameTex("ring_pending"), x, y, radius * 1.3, still ? 0 : time * speed,
                PORTAL_TINT[0], PORTAL_TINT[1], PORTAL_TINT[2], alpha);
        batch.add(SpriteBatch.Layer.BACK, frameTex("ring_pending"), x, y, radius * (active ? 1.6 + 0.08 * pulse : 1.5),
                still ? 0 : -time * speed * 0.6, PORTAL_TINT[0], PORTAL_TINT[1], PORTAL_TINT[2], alpha);
    }

    private static void drawGravityWell(SpriteBatch batch, double x, double y, double radius, boolean held, double time) {
        boolean still = ClientConfig.REDUCED_MOTION.get();
        float strength = held ? 1f : DORMANT_DISK_ALPHA;
        for (int layer = 0; layer < INFALL_LAYERS; layer++) {
            double fall = still ? 0.5 : (time / INFALL_SECONDS + layer / (double) INFALL_LAYERS) % 1.0;
            batch.add(SpriteBatch.Layer.BACK, frameTex("black_hole_infall"), x, y, radius * (INFALL_START - (INFALL_START - INFALL_END) * fall),
                    -fall * INFALL_TWIST - layer * 2.1, 1f, 1f, 1f, strength * (float) Math.sin(Math.PI * fall));
        }
        batch.add(SpriteBatch.Layer.BACK, frameTex("black_hole_disk"), x, y, radius * DISK_PER_RADIUS, still ? 0 : -time * (held ? 0.9 : 0.3),
                1f, 1f, 1f, strength);
    }

    private static void drawLens(SpriteBatch batch, double x, double y, double radius, boolean held, double time) {
        boolean still = ClientConfig.REDUCED_MOTION.get();
        double breath = still ? 0 : Math.sin(time * 1.7);
        batch.add(SpriteBatch.Layer.FRONT, frameTex("black_hole_lens"), x, y, radius * (LENS_PER_RADIUS + LENS_BREATH * breath),
                still ? 0 : time * 0.2, 1f, 1f, 1f, (held ? 0.75f : 0.4f) + 0.2f * (float) breath);
    }

    private static double iconScale(SkillNodeDefinition node, double radius) {
        if (radius < SMALL_NODE_RADIUS) {
            return SMALL_NODE_ICON_SCALE;
        }
        return node.type() == NodeType.MINOR ? 0.62 : 0.58;
    }

    private static float shade(NodeState state, boolean affordable) {
        boolean highContrast = ClientConfig.HIGH_CONTRAST.get();
        return switch (state) {
            case LOCKED -> highContrast ? 0.66f : 0.42f;
            case CONFLICTING, SEALED -> highContrast ? 0.72f : 0.5f;
            case AVAILABLE -> affordable ? 1f : 0.7f;
            default -> 1f;
        };
    }

    private static void drawStateMark(SpriteBatch batch, NodeState state, double x, double y, double radius, double time) {
        switch (state) {
            case PENDING -> batch.add(SpriteBatch.Layer.FRONT, frameTex("ring_pending"), x, y, radius * 1.35,
                    ClientConfig.REDUCED_MOTION.get() ? 0 : time * 0.8, 1f, 1f, 1f, 1f);
            case CONFLICTING -> batch.add(SpriteBatch.Layer.FRONT, frameTex("mark_conflict"), x, y, radius * 0.9);
            case SEALED -> batch.add(SpriteBatch.Layer.FRONT, frameTex("mark_sealed"), x, y, radius * 0.9);
            case LOCKED -> {
                if (radius > MIN_LOCK_MARK_RADIUS) {
                    batch.add(SpriteBatch.Layer.FRONT, frameTex("mark_locked"), x + radius * 0.62, y + radius * 0.62, Math.max(3, radius * 0.42));
                }
            }
            default -> {
            }
        }
    }

    public static void draw(GuiGraphics graphics, ResourceLocation texture, double centerX, double centerY, double radius) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - radius, centerY - radius, 0);
        float scale = (float) (2 * radius / TEXTURE_SIZE);
        graphics.pose().scale(scale, scale, 1f);
        graphics.blit(texture, 0, 0, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.pose().popPose();
    }
}
