package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.render.GraphRenderer;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import dev.lesteoff.celestialskilltree.graph.CommunityTier;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress.StarStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

final class CommunitySidebar {
    private static final int PANEL_WIDTH = 130;
    private static final int CARD_PADDING = 7;
    private static final int GLASS_PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int HEADER_ICON = 18;
    private static final int HEADER_HEIGHT = 19;
    private static final int HEADER_TEXT_LEFT = HEADER_ICON + 5;
    private static final int SECTION_GAP = 4;
    private static final int DIVIDER_HEIGHT = 3;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_ROW = 8;
    private static final int GLASS_RULE = 0x663F7480;
    private static final int RULE_SPACE = 2 * SECTION_GAP + 1;
    private static final int RANKING_ICON = 8;
    private static final int RANKING_LINES = 5;
    private static final String OPEN_MARK = "▼";
    private static final String CLOSED_MARK = "▶";

    private static boolean starOpen = true;
    private static boolean rankingOpen = true;

    private sealed interface Row permits Line, Bar {
        int height();
    }

    private record Line(FormattedCharSequence text, Component right) implements Row {
        public int height() {
            return LINE_HEIGHT;
        }
    }

    private record Bar(double fraction) implements Row {
        public int height() {
            return BAR_ROW;
        }
    }

    private record Box(int x, int y, int width, int height) {
        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }

        int bottom() {
            return y + height;
        }
    }

    private Font font;
    private SkillButton starButton;
    private SkillButton rankingButton;
    private SkillButton seeAllButton;
    private Box dock = new Box(0, 0, 0, 0);
    private Box card = new Box(0, 0, 0, 0);
    private Box ranking = new Box(0, 0, 0, 0);
    private int panelsX;
    private int bottomLimit;
    private boolean rankingBodyShown;

    static boolean onLeft() {
        return ClientConfig.COMMUNITY_PANEL_SIDE.get() == ClientConfig.PanelSide.LEFT;
    }

    static int innerEdge(int screenWidth) {
        int column = TreeHud.TOP_HEIGHT + TreeHud.ISLAND_GAP + PANEL_WIDTH;
        return onLeft() ? TreeHud.EDGE + column : screenWidth - TreeHud.EDGE - column;
    }

    void layout(Font font, int screenWidth, Consumer<AbstractWidget> add, Runnable openHelp, Runnable openPatrons) {
        this.font = font;
        int dockX = onLeft() ? TreeHud.EDGE : screenWidth - TreeHud.EDGE - TreeHud.TOP_HEIGHT;
        panelsX = onLeft() ? dockX + TreeHud.TOP_HEIGHT + TreeHud.ISLAND_GAP : dockX - TreeHud.ISLAND_GAP - PANEL_WIDTH;
        starButton = dockButton(HudSprites.POINT, "community.star_panel", CommunitySidebar::toggleStar, add);
        rankingButton = dockButton(HudSprites.RANKING, "community.ranking_panel", CommunitySidebar::toggleRanking, add);
        List<SkillButton> tools = List.of(starButton, rankingButton, dockButton(HudSprites.HELP, "community.help", openHelp, add));
        int y = TreeHud.DOCK_TOP + TreeHud.PAD;
        for (SkillButton tool : tools) {
            tool.setPosition(dockX + TreeHud.PAD, y);
            y += TreeHud.TOOL + TreeHud.GAP;
        }
        dock = new Box(dockX, TreeHud.DOCK_TOP, TreeHud.TOP_HEIGHT, y - TreeHud.GAP + TreeHud.PAD - TreeHud.DOCK_TOP);
        seeAllButton = new SkillButton(0, 0, PANEL_WIDTH - 2 * GLASS_PADDING, TreeHud.CONTROL, Component.empty(), pressed -> openPatrons.run());
        add.accept(seeAllButton);
    }

    private static SkillButton dockButton(ResourceLocation icon, String key, Runnable action, Consumer<AbstractWidget> add) {
        SkillButton button = SkillButton.icon(0, 0, TreeHud.TOOL, icon, GuiText.of(key), pressed -> action.run());
        button.setTooltip(Tooltip.create(GuiText.of(key + "_tooltip")));
        add.accept(button);
        return button;
    }

    void limitBottom(int bottomLimit) {
        this.bottomLimit = bottomLimit;
    }

    static void toggleStar() {
        starOpen = !starOpen;
    }

    static void toggleRanking() {
        rankingOpen = !rankingOpen;
    }

    static boolean starOpen() {
        return starOpen;
    }

    static boolean rankingOpen() {
        return rankingOpen;
    }

    boolean contains(double mouseX, double mouseY) {
        return dock.contains(mouseX, mouseY) || card.contains(mouseX, mouseY) || ranking.contains(mouseX, mouseY);
    }

    boolean click(double mouseX, double mouseY) {
        if (header(card, CARD_PADDING, HEADER_HEIGHT).contains(mouseX, mouseY)) {
            toggleStar();
            return true;
        }
        if (header(ranking, GLASS_PADDING, LINE_HEIGHT).contains(mouseX, mouseY)) {
            toggleRanking();
            return true;
        }
        return contains(mouseX, mouseY);
    }

    private static Box header(Box panel, int padding, int height) {
        return new Box(panel.x(), panel.y(), panel.width(), padding + height);
    }

    void render(GuiGraphics graphics, Optional<Payloads.TierInfo> star) {
        RenderSystem.enableBlend();
        graphics.blitSprite(HudSprites.PANEL, dock.x(), dock.y(), dock.width(), dock.height());
        RenderSystem.disableBlend();
        starButton.setSprites(starOpen ? SkillButton.PRIMARY : SkillButton.PLAIN);
        rankingButton.setSprites(rankingOpen ? SkillButton.PRIMARY : SkillButton.PLAIN);
        List<List<Row>> sections = star.filter(shown -> starOpen).map(this::cardSections).orElse(List.of());
        card = star.isEmpty() ? new Box(panelsX, TreeHud.DOCK_TOP, 0, 0)
                : new Box(panelsX, TreeHud.DOCK_TOP, PANEL_WIDTH, 2 * CARD_PADDING + HEADER_HEIGHT + sectionsHeight(sections));
        star.ifPresent(shown -> drawCard(graphics, shown, sections));
        drawRanking(graphics, card.height() == 0 ? TreeHud.DOCK_TOP : card.bottom() + TreeHud.ISLAND_GAP);
    }

    private List<List<Row>> cardSections(Payloads.TierInfo star) {
        int width = PANEL_WIDTH - 2 * CARD_PADDING;
        List<Row> effect = new ArrayList<>();
        font.split(Component.translatable(CommunityTier.descriptionKey(star.id())), width)
                .forEach(line -> effect.add(new Line(line, Component.empty())));
        return List.of(effect, progressRows(star, width), patronRows(star));
    }

    private List<Row> progressRows(Payloads.TierInfo star, int width) {
        List<Row> rows = new ArrayList<>();
        StarStatus status = CommunityConstellation.status(star);
        switch (status) {
            case OPEN, LIT -> {
                double fraction = CommunityConstellation.fraction(star);
                rows.add(new Line(GuiText.of("community.star_progress", GuiText.number(CommunityConstellation.donated(star)),
                        GuiText.number(star.xpRequired())).getVisualOrderText(),
                        Component.literal((int) Math.floor(fraction * 100) + "%").withStyle(ChatFormatting.GRAY)));
                rows.add(new Bar(fraction));
            }
            case LOCKED -> wrapped(rows, GuiText.of("community.locked", CommunityConstellation.name(star.parent()),
                    GuiText.number(star.xpRequired())).withStyle(ChatFormatting.GRAY), width);
            case EXCLUDED -> wrapped(rows, GuiText.of("community.excluded", litRival(star).map(CommunityConstellation::name)
                    .orElse(Component.empty())).withStyle(ChatFormatting.RED), width);
        }
        if (status == StarStatus.OPEN || status == StarStatus.LOCKED) {
            rivalsLine(star).ifPresent(rivals -> wrapped(rows, rivals, width));
        }
        return rows;
    }

    private void wrapped(List<Row> rows, Component text, int width) {
        font.split(text, width).forEach(line -> rows.add(new Line(line, Component.empty())));
    }

    private static Optional<String> litRival(Payloads.TierInfo star) {
        return rivals(star).stream().filter(rival -> CommunityConstellation.status(rival) == StarStatus.LIT)
                .map(Payloads.TierInfo::id).findFirst();
    }

    private static List<Payloads.TierInfo> rivals(Payloads.TierInfo star) {
        return CommunityConstellation.stars().stream().filter(star::rivals).toList();
    }

    private static Optional<Component> rivalsLine(Payloads.TierInfo star) {
        List<Payloads.TierInfo> rivals = rivals(star);
        if (rivals.isEmpty()) {
            return Optional.empty();
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < rivals.size(); i++) {
            names.append(i == 0 ? Component.empty() : Component.literal(", ")).append(CommunityConstellation.name(rivals.get(i).id()));
        }
        return Optional.of(GuiText.of("community.rivals", names).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private List<Row> patronRows(Payloads.TierInfo star) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Line(GuiText.of("community.star_patrons").withStyle(ChatFormatting.GOLD).getVisualOrderText(), Component.empty()));
        List<Payloads.Patron> patrons = CommunityConstellation.community().progress(star.id())
                .map(Payloads.StarProgress::patrons).orElse(List.of());
        if (patrons.isEmpty()) {
            rows.add(new Line(GuiText.of("community.no_patrons").withStyle(ChatFormatting.GRAY).getVisualOrderText(), Component.empty()));
        }
        for (int i = 0; i < patrons.size(); i++) {
            Payloads.Patron patron = patrons.get(i);
            Component name = Component.literal((i + 1) + ". ").append(patronName(patron));
            rows.add(new Line(name.getVisualOrderText(), Component.literal(GuiText.number(patron.xp())).withStyle(ChatFormatting.GRAY)));
        }
        return rows;
    }

    static Component patronName(Payloads.Patron patron) {
        return patron.anonymous() ? GuiText.of("community.anonymous") : Component.literal(patron.name());
    }

    private static int sectionsHeight(List<List<Row>> sections) {
        return sections.stream().mapToInt(rows -> 2 * SECTION_GAP + DIVIDER_HEIGHT + rows.stream().mapToInt(Row::height).sum()).sum();
    }

    private void drawCard(GuiGraphics graphics, Payloads.TierInfo star, List<List<Row>> sections) {
        RenderSystem.enableBlend();
        graphics.blitSprite(TooltipPanel.BACKGROUND, card.x(), card.y(), card.width(), card.height());
        int left = card.x() + CARD_PADDING;
        int top = card.y() + CARD_PADDING;
        int innerWidth = card.width() - 2 * CARD_PADDING;
        drawCardIcon(graphics, star, left + HEADER_ICON / 2.0, top + HEADER_ICON / 2.0);
        StarStatus status = CommunityConstellation.status(star);
        graphics.drawString(font, CommunityConstellation.name(star.id()).withStyle(ChatFormatting.GOLD), left + HEADER_TEXT_LEFT, top,
                Palette.TEXT, true);
        graphics.drawString(font, GuiText.of("community.status." + status.name().toLowerCase(Locale.ROOT))
                .withStyle(CommunityConstellation.nameStyle(status)), left + HEADER_TEXT_LEFT, top + LINE_HEIGHT, Palette.TEXT, true);
        drawMark(graphics, starOpen, left + innerWidth, top);
        int y = top + HEADER_HEIGHT;
        for (List<Row> rows : sections) {
            y += SECTION_GAP;
            graphics.blitSprite(TooltipPanel.DIVIDER, left, y, innerWidth, DIVIDER_HEIGHT);
            y += DIVIDER_HEIGHT + SECTION_GAP;
            for (Row row : rows) {
                drawRow(graphics, row, left, y, innerWidth);
                y += row.height();
            }
        }
        RenderSystem.disableBlend();
    }

    private static void drawCardIcon(GuiGraphics graphics, Payloads.TierInfo star, double centerX, double centerY) {
        double radius = HEADER_ICON / 2.0;
        GraphRenderer.drawNodeFrame(graphics, GraphRenderer.frameTex("notable"), centerX, centerY, radius,
                CommunityConstellation.status(star) == StarStatus.LIT);
        GraphRenderer.draw(graphics, GraphRenderer.iconTex(star.icon()), centerX, centerY, radius * 0.6);
    }

    private void drawMark(GuiGraphics graphics, boolean open, int right, int top) {
        String mark = open ? OPEN_MARK : CLOSED_MARK;
        graphics.drawString(font, mark, right - font.width(mark), top, Palette.textDim(), true);
    }

    private void drawRow(GuiGraphics graphics, Row row, int left, int top, int width) {
        switch (row) {
            case Line line -> {
                graphics.drawString(font, line.text(), left, top, Palette.TEXT, true);
                graphics.drawString(font, line.right(), left + width - font.width(line.right()), top, Palette.TEXT, true);
            }
            case Bar bar -> ProgressBar.drawTrack(graphics, left, left + width, top + (BAR_ROW - BAR_HEIGHT) / 2 - 1, bar.fraction());
        }
    }

    private void drawRanking(GuiGraphics graphics, int top) {
        Payloads.Community community = CommunityConstellation.community();
        int closedHeight = 2 * GLASS_PADDING + LINE_HEIGHT;
        int bodyHeight = rankingBodyHeight(community);
        rankingBodyShown = rankingOpen && top + closedHeight + bodyHeight <= bottomLimit;
        ranking = new Box(panelsX, top, PANEL_WIDTH, closedHeight + (rankingBodyShown ? bodyHeight : 0));
        RenderSystem.enableBlend();
        graphics.blitSprite(HudSprites.PANEL, ranking.x(), ranking.y(), ranking.width(), ranking.height());
        int left = ranking.x() + GLASS_PADDING;
        int innerWidth = ranking.width() - 2 * GLASS_PADDING;
        int y = top + GLASS_PADDING;
        graphics.blitSprite(HudSprites.RANKING, left, y, RANKING_ICON, RANKING_ICON);
        RenderSystem.disableBlend();
        graphics.drawString(font, GuiText.of("community.ranking").withStyle(ChatFormatting.GOLD), left + RANKING_ICON + 4, y,
                Palette.TEXT, true);
        Component own = community.ownRank() > 0 ? Component.literal("#" + community.ownRank()).withStyle(ChatFormatting.GREEN)
                : Component.literal("—").withStyle(ChatFormatting.GRAY);
        int markWidth = font.width(OPEN_MARK) + 3;
        graphics.drawString(font, own, left + innerWidth - markWidth - font.width(own), y, Palette.TEXT, true);
        drawMark(graphics, rankingOpen, left + innerWidth, y);
        seeAllButton.visible = rankingBodyShown;
        if (rankingBodyShown) {
            drawRankingBody(graphics, community, left, y + LINE_HEIGHT, innerWidth);
        }
    }

    private int rankingBodyHeight(Payloads.Community community) {
        int lines = Math.max(1, Math.min(RANKING_LINES, community.ranking().size()));
        return 2 * RULE_SPACE + (lines + 1) * LINE_HEIGHT + SECTION_GAP + TreeHud.CONTROL;
    }

    private void drawRule(GuiGraphics graphics, int left, int top, int width) {
        graphics.fill(left, top + SECTION_GAP, left + width, top + SECTION_GAP + 1, GLASS_RULE);
    }

    private void drawRankingBody(GuiGraphics graphics, Payloads.Community community, int left, int top, int width) {
        drawRule(graphics, left, top, width);
        int y = top + RULE_SPACE;
        List<Payloads.Patron> ranking = community.ranking();
        if (ranking.isEmpty()) {
            graphics.drawString(font, GuiText.of("community.no_patrons").withStyle(ChatFormatting.GRAY), left, y, Palette.TEXT, true);
            y += LINE_HEIGHT;
        }
        for (int i = 0; i < Math.min(RANKING_LINES, ranking.size()); i++) {
            Payloads.Patron patron = ranking.get(i);
            MutableComponent name = Component.literal((i + 1) + ". " + (i == 0 ? "✦ " : "") + patron.name());
            graphics.drawString(font, name.withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE), left, y, Palette.TEXT, true);
            String xp = GuiText.number(patron.xp());
            graphics.drawString(font, xp, left + width - font.width(xp), y, Palette.textDim(), true);
            y += LINE_HEIGHT;
        }
        drawRule(graphics, left, y, width);
        y += RULE_SPACE;
        Component own = community.ownRank() > 0
                ? GuiText.of("community.own_rank", community.ownRank(), GuiText.number(community.ownTotal())).withStyle(ChatFormatting.GREEN)
                : GuiText.of("community.no_rank").withStyle(ChatFormatting.GRAY);
        graphics.drawString(font, own, left, y, Palette.TEXT, true);
        y += LINE_HEIGHT + SECTION_GAP;
        seeAllButton.setMessage(GuiText.of("community.see_all", community.patronCount()));
        seeAllButton.active = community.patronCount() > 0 || community.anonymousXp() > 0;
        seeAllButton.setPosition(left, y);
    }
}
