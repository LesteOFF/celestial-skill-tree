package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.Allocation;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RefundService;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class PanelContents {
    private static final int MAX_LISTED_NODES = 10;

    private PanelContents() {
    }

    static List<Component> reset(SkillGraphSnapshot graph, PlayerSkillState state, Payloads.Economy economy) {
        RespecService.Preview preview = RespecService.preview(state);
        List<Component> lines = new ArrayList<>();
        lines.add(GuiText.of("reset.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(GuiText.of("reset.refund", preview.refund(), preview.removed().size()));
        lines.add(GuiText.of("reset.keeps").withStyle(ChatFormatting.GRAY));
        listNodes(lines, preview.removed(), id -> nodeName(graph, id));
        if (economy.orbRequired()) {
            lines.add(GuiText.of("reset.orb").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        addEclipseNote(lines);
        return lines;
    }

    static List<Component> refund(SkillGraphSnapshot graph, PlayerSkillState state, RefundService.Plan plan,
                                  Payloads.Economy economy) {
        List<Component> lines = new ArrayList<>();
        lines.add(GuiText.of("refund.title", nodeName(graph, plan.target())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(GuiText.of("refund.body", plan.refund(), plan.removed().size()));
        if (plan.chained()) {
            lines.add(GuiText.of("refund.chain").withStyle(ChatFormatting.RED));
        }
        listNodes(lines, plan.removed(), id -> nodeName(graph, id).append(" (" + paidCost(state, id) + ")"));
        lines.add(GuiText.of("refund.keeps").withStyle(ChatFormatting.GRAY));
        if (economy.orbRequiredForNodeRefund()) {
            lines.add(GuiText.of("refund.orb").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        addEclipseNote(lines);
        return lines;
    }

    static List<Component> discard(int pendingCount, int pendingCost) {
        return List.of(GuiText.of("discard.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                GuiText.of("discard.body", pendingCount, pendingCost));
    }

    static List<Component> dependents(SkillGraphSnapshot graph, String removedId, Collection<String> dependents) {
        List<Component> lines = new ArrayList<>();
        lines.add(GuiText.of("dependents.title", nodeName(graph, removedId)).withStyle(ChatFormatting.GOLD));
        dependents.stream().limit(MAX_LISTED_NODES).forEach(id -> lines.add(listed(nodeName(graph, id))));
        int released = dependents.stream().mapToInt(id -> graph.nodes().get(id).cost()).sum() + graph.nodes().get(removedId).cost();
        lines.add(GuiText.of("dependents.cost", dependents.size() + 1, released));
        return lines;
    }

    private static void addEclipseNote(List<Component> lines) {
        if (ClientSkillState.worldRules().skyEvent() == SkyEvent.ECLIPSE) {
            lines.add(GuiText.of("eclipse.no_orb").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static void listNodes(List<Component> lines, List<String> ids, Function<String, MutableComponent> label) {
        ids.stream().limit(MAX_LISTED_NODES).forEach(id -> lines.add(listed(label.apply(id))));
        if (ids.size() > MAX_LISTED_NODES) {
            lines.add(GuiText.of("reset.more", ids.size() - MAX_LISTED_NODES).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component listed(MutableComponent label) {
        return Component.literal(" - ").append(label).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent nodeName(SkillGraphSnapshot graph, String id) {
        return Component.translatable(graph.node(id).map(SkillNodeDefinition::nameKey).orElse(id));
    }

    private static int paidCost(PlayerSkillState state, String id) {
        return Optional.ofNullable(state.allocations().get(id)).map(Allocation::paidCost).orElse(0);
    }
}
