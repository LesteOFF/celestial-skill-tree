package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.progression.RefundService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class TreeScreenQa {
    private TreeScreenQa() {
    }

    public static void setup(SkillTreeScreen screen, Collection<String> pendingIds, boolean stats, boolean reset) {
        screen.pending.clear();
        screen.pending.addAll(pendingIds);
        screen.panel = stats ? SkillTreeScreen.Panel.STATS : reset ? SkillTreeScreen.Panel.RESET : SkillTreeScreen.Panel.NONE;
        screen.statesDirty = true;
    }

    public static void hover(SkillTreeScreen screen, String nodeId) {
        ClientSkillState.graph().flatMap(graph -> graph.node(nodeId)).ifPresent(node -> {
            screen.camera.centerOn(node.position().x(), node.position().y());
            screen.simulatedMouseX = screen.camera.toScreenX(node.position().x());
            screen.simulatedMouseY = screen.camera.toScreenY(node.position().y());
        });
    }

    public static void hoverAt(SkillTreeScreen screen, String nodeId, double widthFraction, double heightFraction) {
        ClientSkillState.graph().flatMap(graph -> graph.node(nodeId)).ifPresent(node -> {
            double screenX = screen.width * widthFraction;
            double screenY = screen.height * heightFraction;
            screen.camera.centerOn(node.position().x() - (screenX - screen.camera.vcx()) / screen.camera.zoomValue(),
                    node.position().y() - (screenY - screen.camera.vcy()) / screen.camera.zoomValue());
            screen.simulatedMouseX = screenX;
            screen.simulatedMouseY = screenY;
        });
    }

    public static void buy(SkillTreeScreen screen) {
        screen.buyPoints(1);
    }

    public static void buyMany(SkillTreeScreen screen, int count) {
        screen.buyPoints(count);
    }

    public static CommunityScreen openCommunity(SkillTreeScreen screen) {
        pressTab(screen, "COMMUNITY");
        return (CommunityScreen) Minecraft.getInstance().screen;
    }

    public static void pressTab(Screen screen, String tab) {
        tab(screen, tab).onPress();
    }

    public static boolean tabAvailable(Screen screen, String tab) {
        return tab(screen, tab).active;
    }

    public static boolean tabSelected(Screen screen, String tab) {
        return tab(screen, tab).selected();
    }

    private static SkyTabs.TabButton tab(Screen screen, String tab) {
        Component label = GuiText.of(SkyTabs.Tab.valueOf(tab).key);
        return screen.children().stream().filter(SkyTabs.TabButton.class::isInstance).map(SkyTabs.TabButton.class::cast)
                .filter(button -> button.getMessage().equals(label)).findFirst().orElseThrow();
    }

    public static void openSign(SkillTreeScreen screen) {
        Minecraft.getInstance().setScreen(new BirthSignScreen(screen));
    }

    public static void openSignHovering(SkillTreeScreen screen, String signId) {
        BirthSignScreen chart = new BirthSignScreen(screen);
        chart.simulatedHover = signId;
        Minecraft.getInstance().setScreen(chart);
    }

    public static void showHelp(SkillTreeScreen screen) {
        screen.panel = SkillTreeScreen.Panel.HELP;
    }

    public static void showStats(SkillTreeScreen screen) {
        screen.panel = SkillTreeScreen.Panel.STATS;
    }

    public static String pending(SkillTreeScreen screen) {
        screen.refreshStatesIfDirty();
        return screen.pending.nodes().size() + " node(s), " + screen.nodeStates.pendingCost() + " point(s)";
    }

    public static List<String> statsText(SkillTreeScreen screen) {
        screen.refreshStatesIfDirty();
        StatsSheet sheet = screen.statsSheet();
        List<String> text = new ArrayList<>();
        sheet.sign().ifPresent(sign -> text.add(sign.name().getString()));
        sheet.sections().stream().flatMap(section -> section.stats().stream()).forEach(stat -> text.add(stat.label().getString() + " "
                + stat.value() + stat.preview().map(preview -> " → " + preview).orElse("")));
        return text;
    }

    public static List<Integer> statsPoints(SkillTreeScreen screen) {
        screen.refreshStatesIfDirty();
        StatsSheet.Points points = screen.statsSheet().points();
        return List.of(points.total(), points.spent(), points.free(), points.pending());
    }

    public static int pointsLeftAfterSelection(SkillTreeScreen screen) {
        screen.refreshStatesIfDirty();
        return SkillTreeScreen.playerState().availablePoints() - screen.nodeStates.pendingCost();
    }

    public static List<String> statsEffectNames(SkillTreeScreen screen) {
        screen.refreshStatesIfDirty();
        StatsSheet sheet = screen.statsSheet();
        return Stream.concat(sheet.effects().stream(), sheet.triggers().stream()).map(effect -> effect.name().getString()).toList();
    }

    public static boolean dragStatsScrollbarToEnd(SkillTreeScreen screen) {
        StatsSheetRenderer stats = screen.statsRenderer;
        double x = stats.scrollbarX();
        screen.mouseClicked(x, stats.scrollbarY(0), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseDragged(x, stats.scrollbarY(1), GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, 0);
        screen.mouseReleased(x, stats.scrollbarY(1), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        return stats.scrolledToEnd();
    }

    public static void pressEscape(Screen screen) {
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
    }

    public static boolean asksToDiscard(Screen screen) {
        return screen instanceof SkillTreeScreen tree && tree.panel == SkillTreeScreen.Panel.DISCARD;
    }

    public static void confirm(SkillTreeScreen screen) {
        screen.confirm();
    }

    public static void reset(SkillTreeScreen screen) {
        screen.sendReset();
    }

    public static void refund(SkillTreeScreen screen, String nodeId) {
        screen.openRefundPanel(nodeId);
        screen.statesDirty = false;
    }

    public static void confirmRefund(SkillTreeScreen screen) {
        screen.sendRefund();
    }

    public static String refundPreview(String nodeId) {
        RefundService.Plan plan = RefundService.plan(SkillTreeScreen.graph(), SkillTreeScreen.playerState(), nodeId,
                ClientSkillState.economy().nodeRefundsEnabled());
        return plan.ok() ? plan.removed().size() + " node(s), " + plan.refund() + " point(s): " + plan.removed()
                : "rejected: " + plan.reason().orElseThrow();
    }

    public static void fit(SkillTreeScreen screen) {
        screen.cameraPlaced = true;
        screen.fit();
    }

    public static void scaleZoom(SkillTreeScreen screen, double factor) {
        screen.camera.zoom *= factor;
    }

    public static void zoom(SkillTreeScreen screen, double zoom, String centerNode) {
        screen.cameraPlaced = true;
        screen.camera.zoom = zoom;
        String nodeId = centerNode.contains(":") ? centerNode : CelestialSkillTree.MODID + ":" + centerNode;
        ClientSkillState.graph().flatMap(graph -> graph.node(nodeId)).ifPresent(screen::centerOn);
    }

    public static void donate(CommunityScreen community, int amount) {
        community.donate(amount);
    }
}
