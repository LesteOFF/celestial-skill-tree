package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.client.ClientRequests;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.client.render.BackgroundRenderer;
import dev.nostalgiacraft.skills.client.render.LineBatch;
import dev.nostalgiacraft.skills.client.render.Shapes;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.graph.CommunityTier;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.CommunityProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommunityScreen extends Screen {
    private static final int EMBER_TINT = 0xA61C0E04;
    private static final int UNLIT_PATH = 0xFF4A3A28;
    private static final int LIT_GLOW = 0x55F3D15C;
    private static final int LIT_PATH = 0xFFF3D15C;
    private static final int LIT_CORE = 0xFFFFF6D6;
    private static final int UNLIT_STAR = 0xFF8A7A66;
    private static final int NEXT_STAR = 0xFF7FD6E8;
    private static final int TEXT_COLOR = 0xFFE9E3D8;
    private static final int PATH_SAMPLES = 64;
    private static final int MOTES = 9;
    private static final double MOTE_SPEED = 0.12;
    private static final double LEFT_FRACTION = 0.13;
    private static final double RIGHT_FRACTION = 0.87;
    private static final double BOTTOM_FRACTION = 0.6;
    private static final double TOP_FRACTION = 0.22;
    private static final double STAR_SIZE = 7;
    private static final double HOVER_REACH = 14;
    private static final int BUTTON_WIDTH = 86;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int SMALL_DONATION = 100;
    private static final int LARGE_DONATION = 1000;
    private static final long MESSAGE_MILLIS = 4000;
    private static final float TITLE_SCALE = 1.5f;
    private static final int TOOLTIP_WIDTH = 200;

    private record Point(double x, double y) {
    }

    private final Screen parent;
    private final long openedAt = Util.getMillis();
    private final List<Button> donationButtons = new ArrayList<>();
    private Component message;
    private long messageUntil;

    public CommunityScreen(Screen parent) {
        super(GuiText.of("community.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSkillState.setListeners(this::onResult, () -> { });
        donationButtons.clear();
        int rowWidth = 4 * BUTTON_WIDTH + 3 * BUTTON_GAP;
        int left = (width - rowWidth) / 2;
        int y = height - BUTTON_HEIGHT - 8;
        addDonationButton(left, y, GuiText.of("community.donate", SMALL_DONATION), SMALL_DONATION);
        addDonationButton(left + BUTTON_WIDTH + BUTTON_GAP, y, GuiText.of("community.donate", LARGE_DONATION), LARGE_DONATION);
        addDonationButton(left + 2 * (BUTTON_WIDTH + BUTTON_GAP), y, GuiText.of("community.donate_all"), Integer.MAX_VALUE);
        addRenderableWidget(new SkillButton(left + 3 * (BUTTON_WIDTH + BUTTON_GAP), y, BUTTON_WIDTH, BUTTON_HEIGHT,
                GuiText.of("back"), button -> onClose()));
    }

    private void addDonationButton(int x, int y, Component label, int amount) {
        donationButtons.add(addRenderableWidget(new SkillButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label, button -> donate(amount))));
    }

    @Override
    public void removed() {
        ClientSkillState.clearListeners();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void qaDonate(int amount) {
        donate(amount);
    }

    private void donate(int amount) {
        if (ClientSkillState.requestInFlight()) {
            return;
        }
        ClientSkillState.state().ifPresent(state -> ClientRequests.send(
                new Payloads.DonateXp(ClientRequests.newRequest(), state.stateRevision(), amount)));
    }

    private void onResult(Payloads.TransactionResult result) {
        Object[] details = result.detail().split("/");
        message = Component.translatable(result.messageKey(), details).withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
        messageUntil = Util.getMillis() + MESSAGE_MILLIS;
    }

    @Override
    public void tick() {
        super.tick();
        Payloads.WorldRules rules = ClientSkillState.worldRules();
        boolean canDonate = !ClientSkillState.requestInFlight() && ClientSkillState.xpBalance() > 0
                && rules.communityXp() < CommunityProgress.goal(rules.thresholds());
        donationButtons.forEach(button -> button.active = canDonate);
    }

    private double seconds() {
        return ClientConfig.REDUCED_MOTION.get() ? 0 : (Util.getMillis() - openedAt) / 1000.0;
    }

    private Point pathPoint(double fraction) {
        double x = width * (LEFT_FRACTION + (RIGHT_FRACTION - LEFT_FRACTION) * fraction);
        double rise = 1 - (1 - fraction) * (1 - fraction);
        return new Point(x, height * (BOTTOM_FRACTION - (BOTTOM_FRACTION - TOP_FRACTION) * rise));
    }

    private Point tierPoint(int index, int tierCount) {
        return pathPoint((index + 1) / (double) tierCount);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BackgroundRenderer.render(graphics, width, height, 0, 0, seconds());
        graphics.fill(0, 0, width, height, EMBER_TINT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Payloads.WorldRules rules = ClientSkillState.worldRules();
        List<Payloads.TierInfo> tiers = rules.communityTiers();
        double lit = CommunityProgress.pathFraction(rules.thresholds(), rules.communityXp());
        drawTitle(graphics);
        if (tiers.isEmpty()) {
            graphics.drawCenteredString(font, GuiText.of("community.none"), width / 2, height / 2, TEXT_COLOR);
            return;
        }
        drawPath(graphics, tiers, rules.communityXp(), lit);
        drawLabels(graphics, tiers, rules.communityXp());
        drawSummary(graphics, rules);
        drawTierTooltip(graphics, tiers, mouseX, mouseY);
    }

    private void drawTitle(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, 10, 0);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE, 1f);
        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), 0, 0, TEXT_COLOR);
        graphics.pose().popPose();
    }

    private void drawPath(GuiGraphics graphics, List<Payloads.TierInfo> tiers, long communityXp, double lit) {
        LineBatch batch = new LineBatch(graphics);
        for (int i = 0; i < PATH_SAMPLES; i++) {
            double from = i / (double) PATH_SAMPLES;
            double to = (i + 1) / (double) PATH_SAMPLES;
            Point start = pathPoint(from);
            Point end = pathPoint(to);
            batch.segment(start.x(), start.y(), end.x(), end.y(), 2f, UNLIT_PATH);
            if (from < lit) {
                Point litEnd = pathPoint(Math.min(to, lit));
                batch.segment(start.x(), start.y(), litEnd.x(), litEnd.y(), 7f, LIT_GLOW);
                batch.segment(start.x(), start.y(), litEnd.x(), litEnd.y(), 3f, LIT_PATH);
            }
        }
        drawMotes(batch, lit);
        Point origin = pathPoint(0);
        Shapes.sparkle(batch, origin.x(), origin.y(), STAR_SIZE * 0.6, LIT_PATH);
        for (int i = 0; i < tiers.size(); i++) {
            drawTierStar(batch, tierPoint(i, tiers.size()), communityXp >= tiers.get(i).xpRequired(),
                    i == CommunityProgress.unlockedCount(ClientSkillState.worldRules().thresholds(), communityXp));
        }
        batch.draw();
    }

    private void drawMotes(LineBatch batch, double lit) {
        if (lit <= 0 || ClientConfig.REDUCED_MOTION.get()) {
            return;
        }
        for (int i = 0; i < MOTES; i++) {
            double fraction = ((seconds() * MOTE_SPEED + i / (double) MOTES) % 1.0) * lit;
            Point mote = pathPoint(fraction);
            double bob = Math.sin(seconds() * 3 + i * 2.1) * 3;
            Shapes.sparkle(batch, mote.x(), mote.y() - 5 + bob, 1.8, LIT_CORE);
        }
    }

    private void drawTierStar(LineBatch batch, Point point, boolean reached, boolean next) {
        double pulse = 0.5 + 0.5 * Math.sin(seconds() * 2.4);
        if (reached) {
            Shapes.ring(batch, point.x(), point.y(), STAR_SIZE * (1.7 + 0.25 * pulse), 2f, Shapes.withAlpha(LIT_PATH, 0.35 + 0.3 * pulse));
            Shapes.sparkle(batch, point.x(), point.y(), STAR_SIZE * 1.5, LIT_PATH);
            Shapes.sparkle(batch, point.x(), point.y(), STAR_SIZE * 0.7, LIT_CORE);
        } else {
            int color = next ? Shapes.withAlpha(NEXT_STAR, 0.6 + 0.4 * pulse) : UNLIT_STAR;
            Shapes.ring(batch, point.x(), point.y(), STAR_SIZE, 1.5f, color);
            Shapes.sparkle(batch, point.x(), point.y(), STAR_SIZE * 0.45, color);
        }
    }

    private void drawLabels(GuiGraphics graphics, List<Payloads.TierInfo> tiers, long communityXp) {
        for (int i = 0; i < tiers.size(); i++) {
            Payloads.TierInfo tier = tiers.get(i);
            Point point = tierPoint(i, tiers.size());
            boolean reached = communityXp >= tier.xpRequired();
            graphics.drawCenteredString(font, Component.translatable(CommunityTier.nameKey(tier.id()))
                    .withStyle(reached ? ChatFormatting.GOLD : ChatFormatting.GRAY), (int) point.x(), (int) point.y() + 16, TEXT_COLOR);
            graphics.drawCenteredString(font, Component.literal(grouped(tier.xpRequired()) + " XP")
                    .withStyle(reached ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), (int) point.x(), (int) point.y() + 27, TEXT_COLOR);
        }
    }

    private void drawSummary(GuiGraphics graphics, Payloads.WorldRules rules) {
        List<Long> thresholds = rules.thresholds();
        int top = (int) (height * BOTTOM_FRACTION) + 46;
        graphics.drawCenteredString(font, GuiText.of("community.progress", grouped(rules.communityXp()),
                grouped(CommunityProgress.goal(thresholds)), CommunityProgress.unlockedCount(thresholds, rules.communityXp()),
                thresholds.size()), width / 2, top, TEXT_COLOR);
        graphics.drawCenteredString(font, GuiText.of("community.balance", grouped(ClientSkillState.xpBalance()))
                .withStyle(ChatFormatting.YELLOW), width / 2, top + 11, TEXT_COLOR);
        boolean showMessage = message != null && Util.getMillis() < messageUntil;
        graphics.drawCenteredString(font, showMessage ? message : GuiText.of("community.irreversible").withStyle(ChatFormatting.RED),
                width / 2, top + 22, TEXT_COLOR);
    }

    private void drawTierTooltip(GuiGraphics graphics, List<Payloads.TierInfo> tiers, int mouseX, int mouseY) {
        for (int i = 0; i < tiers.size(); i++) {
            Point point = tierPoint(i, tiers.size());
            if (Math.hypot(mouseX - point.x(), mouseY - point.y()) <= HOVER_REACH) {
                Payloads.TierInfo tier = tiers.get(i);
                Component tooltip = Component.empty()
                        .append(Component.translatable(CommunityTier.nameKey(tier.id())).withStyle(ChatFormatting.GOLD))
                        .append("\n").append(Component.translatable(CommunityTier.descriptionKey(tier.id())).withStyle(ChatFormatting.GREEN))
                        .append("\n").append(GuiText.of("community.tier_at", grouped(tier.xpRequired())).withStyle(ChatFormatting.GRAY));
                TooltipPanel.render(graphics, font, font.split(tooltip, TOOLTIP_WIDTH), mouseX, mouseY);
                return;
            }
        }
    }

    private static String grouped(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
