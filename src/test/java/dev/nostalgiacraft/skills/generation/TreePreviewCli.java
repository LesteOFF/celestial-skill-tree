package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.generation.TreeShape.Link;
import dev.nostalgiacraft.skills.generation.TreeShape.LinkKind;
import dev.nostalgiacraft.skills.graph.Vec2;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TreePreviewCli {
    private static final int IMAGE_SIZE = 2400;
    private static final int MARGIN = 120;
    private static final Color BACKGROUND = new Color(0x0d1020);
    private static final Color PATH = new Color(0x5b6690);
    private static final Color BRIDGE = new Color(0xe0b54a);
    private static final Color PORTAL_LINK = new Color(0x56c8d8);
    private static final Color LABEL = new Color(0xe8ecff);
    private static final Map<SlotKind, Color> FILL = Map.of(
            SlotKind.HUB, new Color(0xf2f2f2),
            SlotKind.TRUNK, new Color(0x8f9bc4),
            SlotKind.RIB, new Color(0xc98a4b),
            SlotKind.RIB_END, new Color(0xb074e8),
            SlotKind.KEYSTONE, new Color(0xf5c542),
            SlotKind.PORTAL, new Color(0x56c8d8));
    private static final Map<SlotKind, Double> IN_GAME_RADIUS = Map.of(
            SlotKind.HUB, 30.0, SlotKind.TRUNK, 16.0, SlotKind.RIB, 16.0, SlotKind.RIB_END, 17.0,
            SlotKind.KEYSTONE, 24.0, SlotKind.PORTAL, 45.0);
    private static final Map<SlotKind, String> LEGEND = new LinkedHashMap<>();
    private static final double MIN_RADIUS_PIXELS = 4;
    private static final int LEGEND_ROW_HEIGHT = 44;

    static {
        LEGEND.put(SlotKind.HUB, "class hub");
        LEGEND.put(SlotKind.TRUNK, "trunk: the class's own stat");
        LEGEND.put(SlotKind.RIB, "branch: minor skills of one theme");
        LEGEND.put(SlotKind.RIB_END, "notable that ends a branch");
        LEGEND.put(SlotKind.KEYSTONE, "keystone");
        LEGEND.put(SlotKind.PORTAL, "portal to the opposite class");
    }

    private TreePreviewCli() {
    }

    public static void main(String[] args) throws IOException {
        int classCount = Integer.parseInt(args[0]);
        int skillsPerClass = Integer.parseInt(args[1]);
        Path output = Path.of(args[2]);
        TreeShape shape = TreeShapeGenerator.generate(
                ShapeRequest.uniform(classCount, skillsPerClass, 3, true));
        Files.createDirectories(output.toAbsolutePath().getParent());
        ImageIO.write(render(shape, classCount + " classes, up to " + skillsPerClass + " skills each"), "png", output.toFile());
        System.out.println("wrote " + output.toAbsolutePath() + " (" + shape.slots().size() + " slots)");
    }

    private static BufferedImage render(TreeShape shape, String title) {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        double extent = shape.slots().stream()
                .mapToDouble(slot -> Math.max(Math.abs(slot.position().x()), Math.abs(slot.position().y()))).max().orElse(1);
        double scale = (IMAGE_SIZE / 2.0 - MARGIN) / extent;
        Map<String, Slot> byKey = new HashMap<>();
        shape.slots().forEach(slot -> byKey.put(slot.key(), slot));
        shape.links().forEach(link -> drawLink(graphics, link, byKey, scale));
        shape.slots().forEach(slot -> drawSlot(graphics, slot, scale));
        shape.classes().forEach(found -> drawLabel(graphics, found, scale));
        drawLegend(graphics);
        graphics.setColor(LABEL);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        graphics.drawString(title, 40, 60);
        graphics.dispose();
        return image;
    }

    private static double pixel(double coordinate, double scale) {
        return IMAGE_SIZE / 2.0 + coordinate * scale;
    }

    private static void drawLink(Graphics2D graphics, Link link, Map<String, Slot> byKey, double scale) {
        Vec2 a = byKey.get(link.a()).position();
        Vec2 b = byKey.get(link.b()).position();
        boolean portal = link.kind() == LinkKind.PORTAL;
        graphics.setColor(portal ? PORTAL_LINK : link.kind() == LinkKind.BRIDGE ? BRIDGE : PATH);
        graphics.setStroke(portal ? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{14f, 12f}, 0f)
                : new BasicStroke(link.kind() == LinkKind.BRIDGE ? 5f : 4f));
        graphics.draw(new Line2D.Double(pixel(a.x(), scale), pixel(a.y(), scale), pixel(b.x(), scale), pixel(b.y(), scale)));
    }

    private static void drawSlot(Graphics2D graphics, Slot slot, double scale) {
        double radius = Math.max(MIN_RADIUS_PIXELS, IN_GAME_RADIUS.get(slot.kind()) * scale);
        drawDot(graphics, slot.kind(), pixel(slot.position().x(), scale), pixel(slot.position().y(), scale), radius);
    }

    private static void drawDot(Graphics2D graphics, SlotKind kind, double x, double y, double radius) {
        Ellipse2D circle = new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius);
        graphics.setColor(FILL.get(kind));
        graphics.fill(circle);
        graphics.setColor(BACKGROUND);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.draw(circle);
    }

    private static void drawLegend(Graphics2D graphics) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
        int row = IMAGE_SIZE - LEGEND_ROW_HEIGHT * (LEGEND.size() + 1);
        for (Map.Entry<SlotKind, String> entry : LEGEND.entrySet()) {
            drawDot(graphics, entry.getKey(), 60, row - 9, 13);
            graphics.setColor(LABEL);
            graphics.drawString(entry.getValue(), 90, row);
            row += LEGEND_ROW_HEIGHT;
        }
        graphics.setColor(BRIDGE);
        graphics.setStroke(new BasicStroke(5f));
        graphics.draw(new Line2D.Double(44, row - 9, 76, row - 9));
        graphics.setColor(LABEL);
        graphics.drawString("bridge between two classes", 90, row);
    }

    private static void drawLabel(Graphics2D graphics, ClassShape shape, double scale) {
        Vec2 hub = shape.hub().position();
        double length = Math.hypot(hub.x(), hub.y());
        double inward = Math.max(0.35, 1 - 170 / length);
        graphics.setColor(LABEL);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        String text = "Class " + (shape.index() + 1);
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, (float) (pixel(hub.x() * inward, scale) - width / 2.0), (float) pixel(hub.y() * inward, scale) + 10);
    }
}
