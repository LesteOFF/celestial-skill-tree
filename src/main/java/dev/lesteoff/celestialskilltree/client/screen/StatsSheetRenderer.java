package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.render.ConstellationGlyph;
import dev.lesteoff.celestialskilltree.client.render.GraphRenderer;
import dev.lesteoff.celestialskilltree.client.render.LineBatch;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import dev.lesteoff.celestialskilltree.graph.Vec2;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

final class StatsSheetRenderer {
    private static final int WIDTH = 200;
    private static final int PADDING = 7;
    private static final int INNER_WIDTH = WIDTH - 2 * PADDING;
    private static final int MARGIN = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_HEIGHT = 8;
    private static final int ICON_SIZE = 8;
    private static final int LABEL_LEFT = ICON_SIZE + 3;
    private static final int TREE_COLUMN = 34;
    private static final int COLUMN_GAP = 4;
    private static final int HEADER_ICON = 16;
    private static final int HEADER_TEXT_LEFT = HEADER_ICON + 4;
    private static final double GLYPH_FILL = 0.8;
    private static final int DIVIDER_HEIGHT = 3;
    private static final int DIVIDER_GAP = 3;
    private static final int MARKER_SIZE = 7;
    private static final int MARKER_SPACE = MARKER_SIZE + 3;
    private static final int STRIPE = 0x0EFFFFFF;
    private static final int TREE_UNDERLINE = 0x907EDC6E;
    private static final int UNDERLINE_PADDING = 3;
    private static final int SCROLL_STEP = 2 * LINE_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_LEFT = WIDTH - PADDING + 1;
    private static final int SCROLLBAR_REACH = 3;
    private static final int SCROLLBAR_TRACK = 0x50000000;
    private static final int MIN_THUMB = 12;
    private static final double NOT_GRABBED = -1;
    private static final int COUNT_GAP = 3;
    private static final float ABOVE_WIDGETS = 400;
    private static final String ARROW = " → ";
    private static final String ELLIPSIS = "…";
    private static final String TIMES = "×";

    private interface Line {
        int height();

        void draw(GuiGraphics graphics, Font font, int left, int top);
    }

    private record Header(Component player, Optional<StatsSheet.Sign> sign, Component badge) implements Line {
        public int height() {
            return HEADER_ICON + 2;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            sign.ifPresentOrElse(own -> drawGlyph(graphics, own.id(), left, top),
                    () -> graphics.blitSprite(HudSprites.SIGN, left, top, HEADER_ICON, HEADER_ICON));
            graphics.drawString(font, player.copy().withStyle(ChatFormatting.BOLD), left + HEADER_TEXT_LEFT, top, Palette.GOLD, true);
            graphics.drawString(font, badge, left + INNER_WIDTH - font.width(badge), top, Palette.textDim(), true);
            sign.ifPresent(own -> graphics.drawString(font, own.name(), left + HEADER_TEXT_LEFT, top + LINE_HEIGHT - 1, Palette.VIOLET, true));
        }

        private static void drawGlyph(GuiGraphics graphics, String signId, int left, int top) {
            double radius = HEADER_ICON / 2.0;
            LineBatch batch = new LineBatch(graphics);
            ConstellationGlyph.of(signId).draw(batch, left + radius, top + radius, radius * GLYPH_FILL, Palette.ALLOCATED, Palette.GOLD,
                    Util.getMillis() / 1000.0);
            batch.draw();
        }
    }

    private record Text(FormattedCharSequence text, int indent, int color) implements Line {
        public int height() {
            return LINE_HEIGHT;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            graphics.drawString(font, text, left + indent, top, color, true);
        }
    }

    private record Divider() implements Line {
        public int height() {
            return DIVIDER_HEIGHT + 2 * DIVIDER_GAP;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            graphics.blitSprite(TooltipPanel.DIVIDER, left, top + DIVIDER_GAP, INNER_WIDTH, DIVIDER_HEIGHT);
        }
    }

    private record Points(StatsSheet.Points points) implements Line {
        public int height() {
            return 2 * LINE_HEIGHT;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            int cell = INNER_WIDTH / 4;
            cell(graphics, font, left, top, points.total(), "total", Palette.TEXT);
            cell(graphics, font, left + cell, top, points.spent(), "spent", Palette.TEXT);
            cell(graphics, font, left + 2 * cell, top, points.free(), "free", Palette.GOLD);
            cell(graphics, font, left + 3 * cell, top, points.pending(), "pending", points.pending() > 0 ? Palette.PENDING : Palette.textDim());
        }

        private static void cell(GuiGraphics graphics, Font font, int left, int top, int amount, String key, int color) {
            int center = left + INNER_WIDTH / 8;
            graphics.drawCenteredString(font, String.valueOf(amount), center, top, color);
            graphics.drawCenteredString(font, text(key), center, top + LINE_HEIGHT - 1, Palette.textDim());
        }
    }

    private record Heading(Component title, Component aside) implements Line {
        public int height() {
            return LINE_HEIGHT + 1;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            graphics.drawString(font, title, left, top, Palette.HEADING, true);
            graphics.drawString(font, aside, left + INNER_WIDTH - font.width(aside), top, Palette.FAINT, true);
        }
    }

    private record TreeColumnHeading(Component title) implements Line {
        public int height() {
            return LINE_HEIGHT + 1;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            graphics.drawString(font, title, left, top, Palette.HEADING, true);
            Component label = text("tree");
            int right = left + INNER_WIDTH;
            int underlineLeft = right - Math.max(TREE_COLUMN, font.width(label) + 2 * UNDERLINE_PADDING);
            graphics.fill(underlineLeft, top + TEXT_HEIGHT + 1, right, top + TEXT_HEIGHT + 2, TREE_UNDERLINE);
            graphics.drawCenteredString(font, label, (underlineLeft + right) / 2, top, Palette.GAIN);
        }
    }

    private record Stat(StatsSheet.Stat stat, boolean stripe) implements Line {
        public int height() {
            return LINE_HEIGHT;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            if (stripe) {
                graphics.fill(left - 2, top - 1, left + INNER_WIDTH + 2, top + LINE_HEIGHT - 1, STRIPE);
            }
            GraphRenderer.draw(graphics, stat.icon(), left + ICON_SIZE / 2.0, top + TEXT_HEIGHT / 2.0 - 0.5, ICON_SIZE / 2.0);
            drawValue(graphics, font, left + INNER_WIDTH - TREE_COLUMN, top);
            String label = fitted(font, stat.label().getString(), labelSpace(font));
            graphics.drawString(font, label, left + LABEL_LEFT, top, Palette.textDim(), true);
            String bonus = stat.treeBonus().orElse("–");
            graphics.drawString(font, bonus, left + INNER_WIDTH - font.width(bonus), top,
                    stat.treeBonus().isPresent() ? Palette.GAIN : Palette.FAINT, true);
        }

        boolean labelCut(Font font) {
            return font.width(stat.label()) > labelSpace(font);
        }

        private int labelSpace(Font font) {
            int previewWidth = stat.preview().map(preview -> font.width(preview) + font.width(ARROW)).orElse(0);
            return INNER_WIDTH - TREE_COLUMN - previewWidth - font.width(stat.value()) - COLUMN_GAP - LABEL_LEFT;
        }

        private void drawValue(GuiGraphics graphics, Font font, int right, int top) {
            int x = right;
            if (stat.preview().isPresent()) {
                String preview = stat.preview().get();
                x -= font.width(preview);
                graphics.drawString(font, preview, x, top, Palette.PENDING, true);
                x -= font.width(ARROW);
                graphics.drawString(font, ARROW, x, top, Palette.PENDING, true);
            }
            x -= font.width(stat.value());
            graphics.drawString(font, stat.value(), x, top, Palette.TEXT, true);
        }

        private static String fitted(Font font, String text, int width) {
            return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width(ELLIPSIS)) + ELLIPSIS;
        }
    }

    private record Marked(TooltipCard.Marker marker, Component name, int count) implements Line {
        public int height() {
            return LINE_HEIGHT;
        }

        public void draw(GuiGraphics graphics, Font font, int left, int top) {
            graphics.blitSprite(marker.sprite(), left, top, MARKER_SIZE, MARKER_SIZE);
            graphics.drawString(font, name, left + MARKER_SPACE, top, Palette.GOLD, true);
            if (count > 1) {
                graphics.drawString(font, TIMES + count, left + MARKER_SPACE + font.width(name) + COUNT_GAP, top, Palette.textDim(), true);
            }
        }
    }

    record Scrollbar(int top, int height, int contentHeight) {
        static final Scrollbar NONE = new Scrollbar(0, 0, 0);

        int maxScroll() {
            return Math.max(0, contentHeight - height);
        }

        boolean shown() {
            return maxScroll() > 0;
        }

        int thumbHeight() {
            return Math.max(MIN_THUMB, height * height / contentHeight);
        }

        int thumbTop(int scroll) {
            return top + (height - thumbHeight()) * scroll / maxScroll();
        }

        int scrollFor(double thumbTop) {
            return (int) Math.round(Math.clamp((thumbTop - top) / (height - thumbHeight()), 0.0, 1.0) * maxScroll());
        }
    }

    private PanelRenderer.Area area = PanelRenderer.Area.NOWHERE;
    private Scrollbar scrollbar = Scrollbar.NONE;
    private float scale = 1f;
    private double grabOffset = NOT_GRABBED;
    private int scrollOffset;
    private StatsSheet laidOutSheet;
    private List<Line> lines = List.of();

    void clear() {
        area = PanelRenderer.Area.NOWHERE;
    }

    void resetScroll() {
        scrollOffset = 0;
    }

    boolean contains(double mouseX, double mouseY) {
        return area.contains(mouseX, mouseY);
    }

    void scroll(double amount) {
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(amount) * SCROLL_STEP);
    }

    boolean grabScrollbar(double mouseX, double mouseY) {
        double x = sheetX(mouseX);
        double y = sheetY(mouseY);
        boolean onTrack = scrollbar.shown() && area.contains(mouseX, mouseY) && x >= SCROLLBAR_LEFT - SCROLLBAR_REACH
                && y >= scrollbar.top() && y <= scrollbar.top() + scrollbar.height();
        if (!onTrack) {
            return false;
        }
        int thumbTop = scrollbar.thumbTop(scrollOffset);
        boolean onThumb = y >= thumbTop && y <= thumbTop + scrollbar.thumbHeight();
        grabOffset = onThumb ? y - thumbTop : scrollbar.thumbHeight() / 2.0;
        dragScrollbar(mouseY);
        return true;
    }

    boolean dragScrollbar(double mouseY) {
        if (grabOffset == NOT_GRABBED) {
            return false;
        }
        scrollOffset = scrollbar.scrollFor(sheetY(mouseY) - grabOffset);
        return true;
    }

    boolean releaseScrollbar() {
        boolean grabbed = grabOffset != NOT_GRABBED;
        grabOffset = NOT_GRABBED;
        return grabbed;
    }

    double scrollbarX() {
        return area.left() + (SCROLLBAR_LEFT + SCROLLBAR_WIDTH / 2.0) * scale;
    }

    double scrollbarY(double fraction) {
        return area.top() + (scrollbar.top() + scrollbar.height() * fraction) * scale;
    }

    boolean scrolledToEnd() {
        return scrollbar.shown() && scrollOffset == scrollbar.maxScroll();
    }

    Optional<Vec2> firstCutLabel(Font font) {
        int top = PADDING - scrollOffset;
        for (Line line : lines) {
            if (line instanceof Stat stat && stat.labelCut(font)) {
                return Optional.of(new Vec2(area.left() + (PADDING + LABEL_LEFT + 2) * scale, area.top() + (top + TEXT_HEIGHT / 2.0) * scale));
            }
            top += line.height();
        }
        return Optional.empty();
    }

    private double sheetX(double mouseX) {
        return (mouseX - area.left()) / scale;
    }

    private double sheetY(double mouseY) {
        return (mouseY - area.top()) / scale;
    }

    void render(GuiGraphics graphics, Font font, StatsSheet sheet, int screenWidth, int screenHeight, IntUnaryOperator controlsBottomAt,
                int mouseX, int mouseY) {
        scale = scale();
        int width = Math.round(WIDTH * scale);
        int left = screenWidth - width - MARGIN;
        int top = controlsBottomAt.applyAsInt(left) + 2;
        List<Line> laidOut = lines(font, sheet);
        int contentHeight = laidOut.stream().mapToInt(Line::height).sum();
        int innerHeight = Math.min(contentHeight, (int) ((screenHeight - top - TreeHud.BOTTOM_CONTROLS_HEIGHT) / scale) - 2 * PADDING);
        scrollbar = new Scrollbar(PADDING, innerHeight, contentHeight);
        scrollOffset = Mth.clamp(scrollOffset, 0, scrollbar.maxScroll());
        int sheetHeight = innerHeight + 2 * PADDING;
        int height = Math.round(sheetHeight * scale);
        area = new PanelRenderer.Area(left, top, left + width, top + height);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, ABOVE_WIDGETS);
        graphics.pose().scale(scale, scale, 1f);
        RenderSystem.enableBlend();
        graphics.blitSprite(TooltipPanel.BACKGROUND, 0, 0, WIDTH, sheetHeight);
        int inset = Math.round(PADDING * scale);
        graphics.enableScissor(left, top + inset, left + width, top + height - inset);
        int y = PADDING - scrollOffset;
        for (Line line : laidOut) {
            if (y + line.height() > 0 && y < sheetHeight) {
                line.draw(graphics, font, PADDING, y);
            }
            y += line.height();
        }
        graphics.disableScissor();
        drawScrollbar(graphics);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
        cutLabelUnder(font, laidOut, mouseX, mouseY, innerHeight).ifPresent(label -> drawAboveSheet(graphics, font, label, mouseX, mouseY));
    }

    private static void drawAboveSheet(GuiGraphics graphics, Font font, Component label, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, ABOVE_WIDGETS);
        graphics.renderTooltip(font, label, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private Optional<Component> cutLabelUnder(Font font, List<Line> laidOut, int mouseX, int mouseY, int innerHeight) {
        double x = sheetX(mouseX);
        double y = sheetY(mouseY);
        boolean overText = area.contains(mouseX, mouseY) && x >= PADDING && x < PADDING + INNER_WIDTH
                && y >= PADDING && y < PADDING + innerHeight;
        if (!overText) {
            return Optional.empty();
        }
        int top = PADDING - scrollOffset;
        for (Line line : laidOut) {
            if (y >= top && y < top + line.height()) {
                return line instanceof Stat stat && stat.labelCut(font) ? Optional.of(stat.stat().label()) : Optional.empty();
            }
            top += line.height();
        }
        return Optional.empty();
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (!scrollbar.shown()) {
            return;
        }
        int right = SCROLLBAR_LEFT + SCROLLBAR_WIDTH;
        graphics.fill(SCROLLBAR_LEFT, scrollbar.top(), right, scrollbar.top() + scrollbar.height(), SCROLLBAR_TRACK);
        int thumbTop = scrollbar.thumbTop(scrollOffset);
        int thumbColor = grabOffset == NOT_GRABBED ? Palette.ALLOCATED : Palette.GOLD;
        graphics.fill(SCROLLBAR_LEFT, thumbTop, right, thumbTop + scrollbar.thumbHeight(), thumbColor);
    }

    private static float scale() {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        return guiScale >= 3 ? (float) ((guiScale - 1) / guiScale) : 1f;
    }

    private List<Line> lines(Font font, StatsSheet sheet) {
        if (sheet != laidOutSheet) {
            lines = layout(font, sheet);
            laidOutSheet = sheet;
        }
        return lines;
    }

    private static List<Line> layout(Font font, StatsSheet sheet) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Header(sheet.player(), sheet.sign(), text("skills", sheet.skills())));
        lines.add(new Divider());
        lines.add(new Points(sheet.points()));
        if (sheet.sections().isEmpty()) {
            lines.add(new Divider());
            addWrapped(lines, font, text("no_world"), 0, Palette.textDim());
        }
        for (StatsSheet.Section section : sheet.sections()) {
            lines.add(new Divider());
            lines.add(section == sheet.sections().getFirst() ? new TreeColumnHeading(section.title()) : new Heading(section.title(), Component.empty()));
            for (int i = 0; i < section.stats().size(); i++) {
                lines.add(new Stat(section.stats().get(i), i % 2 == 0));
            }
        }
        addEffects(lines, font, text("effects"), sheet.effects(), TooltipCard.Marker.EFFECT);
        addEffects(lines, font, text("triggers"), sheet.triggers(), TooltipCard.Marker.EFFECT);
        addEffects(lines, font, text("keystones"), sheet.keystones(), TooltipCard.Marker.RANK);
        return lines;
    }

    private static void addEffects(List<Line> lines, Font font, Component title, List<StatsSheet.Effect> effects, TooltipCard.Marker marker) {
        if (effects.isEmpty()) {
            return;
        }
        lines.add(new Divider());
        lines.add(new Heading(title, Component.literal(String.valueOf(effects.size()))));
        for (StatsSheet.Effect effect : effects) {
            lines.add(new Marked(marker, effect.name(), effect.count()));
            addWrapped(lines, font, effect.description(), MARKER_SPACE, Palette.textDim());
        }
    }

    private static void addWrapped(List<Line> lines, Font font, Component text, int indent, int color) {
        font.split(text, INNER_WIDTH - indent).forEach(part -> lines.add(new Text(part, indent, color)));
    }

    private static MutableComponent text(String key, Object... arguments) {
        return GuiText.of("stats." + key, arguments);
    }
}
