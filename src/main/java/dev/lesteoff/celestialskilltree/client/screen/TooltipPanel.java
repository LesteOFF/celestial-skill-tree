package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.client.render.GraphRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TooltipPanel {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, "tooltip");
    private static final ResourceLocation DIVIDER = ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, "tooltip/divider");
    private static final int PADDING = 7;
    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_GAP = 3;
    private static final int CURSOR_OFFSET = 12;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final float IN_FRONT = 400f;
    private static final int HEADER_ICON = 18;
    private static final int HEADER_TEXT_LEFT = HEADER_ICON + 5;
    private static final int HEADER_HEIGHT = 19;
    private static final int BADGE_GAP = 10;
    private static final int SECTION_GAP = 4;
    private static final int DIVIDER_HEIGHT = 3;
    private static final int MARKER_SIZE = 7;
    private static final int MARKER_SPACE = MARKER_SIZE + 4;

    private record Line(Optional<TooltipCard.Marker> marker, FormattedCharSequence text) {
    }

    private record Corner(int x, int y) {
    }

    private static TooltipCard wrappedCard;
    private static int wrappedWidth;
    private static List<List<Line>> wrappedSections = List.of();

    private TooltipPanel() {
    }

    public static void render(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        int width = lines.stream().mapToInt(font::width).max().orElse(0) + 2 * PADDING;
        int height = lines.size() * LINE_HEIGHT - 1 + (lines.size() > 1 ? TITLE_GAP : 0) + 2 * PADDING;
        Corner corner = place(graphics, mouseX, mouseY, width, height);
        begin(graphics, corner, width, height);
        int lineY = corner.y() + PADDING;
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), corner.x() + PADDING, lineY, TEXT_COLOR, true);
            lineY += LINE_HEIGHT + (index == 0 ? TITLE_GAP : 0);
        }
        end(graphics);
    }

    public static void render(GuiGraphics graphics, Font font, TooltipCard card, int maxWidth, int mouseX, int mouseY) {
        List<List<Line>> sections = sections(font, card, maxWidth);
        int headerWidth = HEADER_TEXT_LEFT + Math.max(font.width(card.title()) + BADGE_GAP + font.width(card.badge()), font.width(card.subtitle()));
        int bodyWidth = sections.stream().flatMap(List::stream).mapToInt(line -> MARKER_SPACE + font.width(line.text())).max().orElse(0);
        int innerWidth = Math.max(headerWidth, bodyWidth);
        int height = HEADER_HEIGHT + sections.stream().mapToInt(lines -> sectionHeight(lines.size())).sum() + 2 * PADDING;
        int width = innerWidth + 2 * PADDING;
        Corner corner = place(graphics, mouseX, mouseY, width, height);
        begin(graphics, corner, width, height);
        int left = corner.x() + PADDING;
        int top = corner.y() + PADDING;
        drawHeader(graphics, font, card, left, top, innerWidth);
        int lineY = top + HEADER_HEIGHT;
        for (List<Line> lines : sections) {
            lineY += SECTION_GAP;
            graphics.blitSprite(DIVIDER, left, lineY, innerWidth, DIVIDER_HEIGHT);
            lineY += DIVIDER_HEIGHT + SECTION_GAP;
            for (Line line : lines) {
                drawLine(graphics, font, line, left, lineY);
                lineY += LINE_HEIGHT;
            }
        }
        end(graphics);
    }

    private static void drawHeader(GuiGraphics graphics, Font font, TooltipCard card, int left, int top, int innerWidth) {
        double center = HEADER_ICON / 2.0;
        GraphRenderer.draw(graphics, card.frame(), left + center, top + center, center);
        GraphRenderer.draw(graphics, card.icon(), left + center, top + center, center * 0.6);
        graphics.drawString(font, card.title(), left + HEADER_TEXT_LEFT, top, TEXT_COLOR, true);
        graphics.drawString(font, card.subtitle(), left + HEADER_TEXT_LEFT, top + LINE_HEIGHT, TEXT_COLOR, true);
        graphics.drawString(font, card.badge(), left + innerWidth - font.width(card.badge()), top, TEXT_COLOR, true);
    }

    private static void drawLine(GuiGraphics graphics, Font font, Line line, int left, int top) {
        line.marker().ifPresent(marker -> graphics.blitSprite(marker.sprite(), left, top, MARKER_SIZE, MARKER_SIZE));
        graphics.drawString(font, line.text(), left + MARKER_SPACE, top, TEXT_COLOR, true);
    }

    private static List<List<Line>> sections(Font font, TooltipCard card, int maxWidth) {
        if (card != wrappedCard || maxWidth != wrappedWidth) {
            wrappedSections = card.sections().stream().filter(rows -> !rows.isEmpty())
                    .map(rows -> wrap(font, rows, maxWidth - MARKER_SPACE)).toList();
            wrappedCard = card;
            wrappedWidth = maxWidth;
        }
        return wrappedSections;
    }

    private static List<Line> wrap(Font font, List<TooltipCard.Row> rows, int width) {
        List<Line> lines = new ArrayList<>();
        for (TooltipCard.Row row : rows) {
            List<FormattedCharSequence> parts = font.split(row.text(), width);
            for (int index = 0; index < parts.size(); index++) {
                lines.add(new Line(index == 0 ? row.marker() : Optional.empty(), parts.get(index)));
            }
        }
        return lines;
    }

    private static int sectionHeight(int lines) {
        return 2 * SECTION_GAP + DIVIDER_HEIGHT + lines * LINE_HEIGHT - 1;
    }

    private static Corner place(GuiGraphics graphics, int mouseX, int mouseY, int width, int height) {
        int x = mouseX + CURSOR_OFFSET + width <= graphics.guiWidth() ? mouseX + CURSOR_OFFSET : Math.max(0, mouseX - CURSOR_OFFSET - width);
        int y = Mth.clamp(mouseY - CURSOR_OFFSET, 0, Math.max(0, graphics.guiHeight() - height));
        return new Corner(x, y);
    }

    private static void end(GuiGraphics graphics) {
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    private static void begin(GuiGraphics graphics, Corner corner, int width, int height) {
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, IN_FRONT);
        RenderSystem.enableBlend();
        graphics.blitSprite(BACKGROUND, corner.x(), corner.y(), width, height);
    }
}
