package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.progression.RefundService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

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
        CommunityScreen community = new CommunityScreen(screen);
        Minecraft.getInstance().setScreen(community);
        return community;
    }

    public static void openSign(SkillTreeScreen screen) {
        Minecraft.getInstance().setScreen(new BirthSignScreen(screen));
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
        return screen.statsLines().stream().map(Component::getString).toList();
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

    public static void zoom(SkillTreeScreen screen, double zoom, String centerNode) {
        screen.cameraPlaced = true;
        screen.camera.zoom = zoom;
        String nodeId = centerNode.contains(":") ? centerNode : NostalgiaSkills.MODID + ":" + centerNode;
        ClientSkillState.graph().flatMap(graph -> graph.node(nodeId)).ifPresent(screen::centerOn);
    }

    public static void donate(CommunityScreen community, int amount) {
        community.donate(amount);
    }
}
