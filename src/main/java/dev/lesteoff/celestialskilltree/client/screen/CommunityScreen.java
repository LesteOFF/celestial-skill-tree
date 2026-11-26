package dev.lesteoff.celestialskilltree.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lesteoff.celestialskilltree.client.ClientRequests;
import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.client.render.BackgroundRenderer;
import dev.lesteoff.celestialskilltree.client.render.Palette;
import dev.lesteoff.celestialskilltree.config.ClientConfig;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress.StarStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public final class CommunityScreen extends Screen {
    private static final long MESSAGE_MILLIS = 4000;
    private static final int MESSAGE_GAP = 3;
    private static final int AREA_MARGIN = 8;
    private static final int ICON_TEXT_GAP = 4;

    enum Modal {
        NONE, HELP, PATRONS
    }

    private record Badge(int x, int y, int width, Component text) {
    }

    private final Screen parent;
    private final long openedAt = Util.getMillis();
    private final SkyTabs tabs = new SkyTabs();
    private final CommunityConstellation constellation = new CommunityConstellation();
    private final CommunitySidebar sidebar = new CommunitySidebar();
    private final CommunityDonationBar donationBar = new CommunityDonationBar();
    private final PanelRenderer modalRenderer = new PanelRenderer();
    private Modal modal = Modal.NONE;
    private List<Component> modalLines = List.of();
    private Component message;
    private long messageUntil;
    private String selected = "";
    private boolean selectedWasOpen;
    private int areaCenter;

    public CommunityScreen(Screen parent) {
        super(GuiText.of("community.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSkillState.setListeners(this::onResult, () -> { });
        keepSelection();
        sidebar.layout(font, width, this::addRenderableWidget, () -> openModal(Modal.HELP), () -> openModal(Modal.PATRONS));
        int inner = CommunitySidebar.innerEdge(width);
        int areaLeft = CommunitySidebar.onLeft() ? inner : TreeHud.EDGE;
        int areaRight = CommunitySidebar.onLeft() ? width - TreeHud.EDGE : inner;
        areaCenter = (areaLeft + areaRight) / 2;
        donationBar.layout(font, areaCenter, width, height, this::addRenderableWidget, this::donate);
        boolean barUnderColumn = CommunitySidebar.onLeft() ? donationBar.left() < inner : donationBar.right() > inner;
        sidebar.limitBottom(barUnderColumn ? donationBar.top() - TreeHud.ISLAND_GAP : height - TreeHud.EDGE);
        constellation.layout(areaLeft + AREA_MARGIN, SkyTabs.BOTTOM, areaRight - AREA_MARGIN, messageTop() - MESSAGE_GAP);
        tabs.layout(width, 0, SkyTabs.Tab.COMMUNITY, new SkyTabs.Actions() {
            @Override
            public void showChart() {
                backTo(SkillTreeScreen::showChart);
            }

            @Override
            public void showHiddenRegion() {
                backTo(SkillTreeScreen::showHiddenRegion);
            }

            @Override
            public void openCommunity() {
            }
        }, this::addRenderableWidget);
        updateDonationBar();
    }

    private int messageTop() {
        return donationBar.top() - MESSAGE_GAP - font.lineHeight;
    }

    @Override
    public void removed() {
        ClientSkillState.clearListeners();
        super.removed();
    }

    private void backTo(Consumer<SkillTreeScreen> view) {
        minecraft.setScreen(parent);
        if (parent instanceof SkillTreeScreen tree) {
            view.accept(tree);
        }
    }

    @Override
    public void onClose() {
        SkillTreeScreen.closeFrom(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void donate(int amount) {
        if (ClientSkillState.requestInFlight() || !isOpen(selected)) {
            return;
        }
        ClientSkillState.state().ifPresent(state -> ClientRequests.send(
                new Payloads.DonateXp(ClientRequests.newRequest(), state.stateRevision(), selected, amount)));
    }

    void select(String starId) {
        selected = starId;
        selectedWasOpen = isOpen(starId);
    }

    void openPatrons() {
        openModal(Modal.PATRONS);
    }

    Modal modal() {
        return modal;
    }

    private void openModal(Modal opened) {
        modal = opened;
        modalLines = opened == Modal.HELP ? PanelContents.communityHelp()
                : PanelContents.patrons(CommunityConstellation.community(), playerName());
        modalRenderer.resetScroll();
    }

    private void closeModal() {
        modal = Modal.NONE;
    }

    private String playerName() {
        return minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
    }

    private static boolean isOpen(String starId) {
        return CommunityConstellation.community().status(starId) == StarStatus.OPEN;
    }

    private void onResult(Payloads.TransactionResult result) {
        message = ClientSkillState.message(result).withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
        messageUntil = Util.getMillis() + MESSAGE_MILLIS;
    }

    @Override
    public void tick() {
        super.tick();
        keepSelection();
        updateDonationBar();
    }

    private void updateDonationBar() {
        boolean canDonate = !ClientSkillState.requestInFlight() && ClientSkillState.xpBalance() > 0 && isOpen(selected);
        donationBar.update(targetLabel(), canDonate);
    }

    private Component targetLabel() {
        return selectedStar().map(star -> (Component) GuiText.of("community.target", CommunityConstellation.name(star.id())
                        .withStyle(CommunityConstellation.nameStyle(CommunityConstellation.status(star)))))
                .orElse(GuiText.of("community.no_target").withStyle(ChatFormatting.GRAY));
    }

    private void keepSelection() {
        boolean known = selectedStar().isPresent();
        if (!known || selectedWasOpen && !isOpen(selected)) {
            select(defaultTarget().orElse(""));
        }
    }

    private Optional<Payloads.TierInfo> selectedStar() {
        return ClientSkillState.worldRules().communityTier(selected);
    }

    private static Optional<String> defaultTarget() {
        List<Payloads.TierInfo> stars = CommunityConstellation.stars();
        List<Payloads.TierInfo> open = stars.stream().filter(star -> isOpen(star.id())).toList();
        return open.stream().filter(star -> star.exclusive().isEmpty()).findFirst().or(() -> open.stream().findFirst())
                .or(() -> stars.stream().findFirst()).map(Payloads.TierInfo::id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modal != Modal.NONE) {
            if (!modalRenderer.click(mouseX, mouseY) && !modalRenderer.contains(mouseX, mouseY)) {
                closeModal();
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button) || sidebar.click(mouseX, mouseY)) {
            return true;
        }
        Optional<Payloads.TierInfo> clicked = constellation.starAt(mouseX, mouseY);
        clicked.ifPresent(star -> select(star.id()));
        return clicked.isPresent();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal != Modal.NONE) {
            modalRenderer.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modal != Modal.NONE && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeModal();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private double seconds() {
        return ClientConfig.REDUCED_MOTION.get() ? 0 : (Util.getMillis() - openedAt) / 1000.0;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BackgroundRenderer.render(graphics, width, height, 0, 0, seconds());
        tabs.render(graphics);
        if (CommunityConstellation.stars().isEmpty()) {
            return;
        }
        constellation.render(graphics, font, selected, seconds());
        sidebar.render(graphics, selectedStar());
        donationBar.renderPanel(graphics);
        Badge badge = litBadge();
        RenderSystem.enableBlend();
        graphics.blitSprite(HudSprites.PANEL, badge.x(), badge.y(), badge.width(), TreeHud.ISLAND_HEIGHT);
        graphics.blitSprite(HudSprites.POINT, badge.x() + TreeHud.CONTENT_LEFT, badge.y() + TreeHud.ICON_TOP, HudSprites.ICON_SIZE,
                HudSprites.ICON_SIZE);
        RenderSystem.disableBlend();
    }

    private Badge litBadge() {
        Payloads.Community community = CommunityConstellation.community();
        Component text = GuiText.of("community.lit_count", community.litCount(), CommunityConstellation.stars().size());
        int badgeWidth = 2 * TreeHud.CONTENT_LEFT + HudSprites.ICON_SIZE + ICON_TEXT_GAP + font.width(text);
        return new Badge(TreeHud.EDGE, TreeHud.EDGE, badgeWidth, text);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean blocked = modal != Modal.NONE;
        super.render(graphics, blocked ? -1 : mouseX, blocked ? -1 : mouseY, partialTick);
        if (CommunityConstellation.stars().isEmpty()) {
            graphics.drawCenteredString(font, GuiText.of("community.none"), width / 2, height / 2, Palette.TEXT);
            return;
        }
        donationBar.renderText(graphics);
        Badge badge = litBadge();
        graphics.drawString(font, badge.text(), badge.x() + TreeHud.CONTENT_LEFT + HudSprites.ICON_SIZE + ICON_TEXT_GAP,
                badge.y() + TreeHud.TEXT_TOP, Palette.GOLD, true);
        drawMessage(graphics);
        if (blocked) {
            PanelRenderer.View view = new PanelRenderer.View(modalLines,
                    List.of(new PanelRenderer.Action(GuiText.of("close"), this::closeModal, false)));
            modalRenderer.render(graphics, font, view, width, height, left -> SkyTabs.BOTTOM, mouseX, mouseY);
        } else if (!sidebar.contains(mouseX, mouseY) && !donationBar.contains(mouseX, mouseY)) {
            constellation.starAt(mouseX, mouseY).ifPresent(star -> drawStarTooltip(graphics, star, mouseX, mouseY));
        }
    }

    private void drawMessage(GuiGraphics graphics) {
        boolean showMessage = message != null && Util.getMillis() < messageUntil;
        Component line = showMessage ? message : GuiText.of("community.irreversible").withStyle(ChatFormatting.RED);
        graphics.drawCenteredString(font, line, areaCenter, messageTop(), Palette.TEXT);
    }

    private void drawStarTooltip(GuiGraphics graphics, Payloads.TierInfo star, int mouseX, int mouseY) {
        StarStatus status = CommunityConstellation.status(star);
        List<FormattedCharSequence> lines = List.of(
                CommunityConstellation.name(star.id()).withStyle(ChatFormatting.GOLD).getVisualOrderText(),
                GuiText.of("community.status." + status.name().toLowerCase(Locale.ROOT))
                        .withStyle(CommunityConstellation.nameStyle(status)).getVisualOrderText());
        TooltipPanel.render(graphics, font, lines, mouseX, mouseY);
    }
}
