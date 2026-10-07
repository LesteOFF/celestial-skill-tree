package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.ExclusiveGroup;
import dev.nostalgiacraft.skills.graph.Sector;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RejectReason;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class SkillTooltips {
    private static final ResourceLocation PREVIEW_MODIFIER = ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "preview");
    private static final double WHOLE_NUMBER_TOLERANCE = 1e-6;

    private record Subject(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> pending,
                           SkillNodeDefinition node) {
        boolean holds(String term) {
            if (!term.startsWith("#")) {
                return state.isAllocated(term) || pending.contains(term);
            }
            String tag = term.substring(1);
            return Stream.concat(state.allocations().keySet().stream(), pending.stream())
                    .anyMatch(id -> graph.node(id).filter(held -> held.tags().contains(tag)).isPresent());
        }

        Component termLabel(String term) {
            if (term.startsWith("#")) {
                return GuiText.of("any_tagged", term.substring(1));
            }
            return graph.node(term).<Component>map(required -> SkillTexts.name(required))
                    .orElse(Component.literal(term));
        }

        String sectorKey() {
            return graph.sectors().stream().filter(sector -> sector.id().equals(node.sector())).map(Sector::nameKey)
                    .findFirst().orElse(node.sector());
        }

        int cost() {
            boolean freeStart = node.isRoot() && state.startingRoot().isEmpty()
                    && pending.stream().noneMatch(id -> graph.nodes().get(id).isRoot());
            return freeStart ? 0 : AllocationService.priceOf(graph, node, state::isAllocated);
        }
    }

    private SkillTooltips() {
    }

    public static List<Component> build(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> pending,
                                        SkillNodeDefinition node, NodeState nodeState, int pendingCost,
                                        Optional<PathFinder.Route> route, boolean detailed) {
        Subject subject = new Subject(graph, state, pending, node);
        List<Component> lines = new ArrayList<>();
        addHeader(lines, subject);
        if (detailed && nodeState != NodeState.ALLOCATED) {
            addAttributePreviews(lines, node);
        }
        addRequirements(lines, subject);
        graph.groupOf(node.id()).ifPresent(group -> lines.add(exclusiveChoice(subject, group)));
        addStatus(lines, subject, nodeState, pendingCost, route);
        return lines;
    }

    private static void addHeader(List<Component> lines, Subject subject) {
        SkillNodeDefinition node = subject.node();
        ChatFormatting nameColor = switch (node.type()) {
            case KEYSTONE -> ChatFormatting.GOLD;
            case NOTABLE -> ChatFormatting.LIGHT_PURPLE;
            case ROOT -> ChatFormatting.AQUA;
            case MINOR -> ChatFormatting.WHITE;
            case PORTAL -> ChatFormatting.DARK_AQUA;
        };
        lines.add(SkillTexts.name(node).withStyle(nameColor, ChatFormatting.BOLD));
        lines.add(Component.translatable(subject.sectorKey()).append(" · ")
                .append(GuiText.of("type." + node.type().serializedName())).withStyle(ChatFormatting.GRAY));
        boolean owned = subject.state().isAllocated(node.id());
        int shownCost = owned ? subject.state().allocations().get(node.id()).paidCost() : subject.cost();
        lines.add(GuiText.of("cost", shownCost).append("   ").append(GuiText.of("rank", owned ? 1 : 0, node.maxRank()))
                .withStyle(ChatFormatting.YELLOW));
        lines.add(SkillTexts.description(node).withStyle(ChatFormatting.GREEN));
        SkillTexts.numbersBehindPlainText(node).ifPresent(numbers -> lines.add(numbers.withStyle(ChatFormatting.DARK_GREEN)));
        subject.graph().portalDestination(node.id()).flatMap(subject.graph()::node).ifPresent(destination -> lines.add(
                GuiText.of("portal_destination", SkillTexts.name(destination)).withStyle(ChatFormatting.AQUA)));
        if (node.traits().amplifiesNeighbours()) {
            lines.add(GuiText.of("amplifier", format(node.traits().neighbourMultiplier(), false)).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static void addRequirements(List<Component> lines, Subject subject) {
        SkillNodeDefinition node = subject.node();
        for (String term : node.requirements().allOf()) {
            lines.add(GuiText.of("requires_all", subject.termLabel(term)).withStyle(metColor(subject.holds(term))));
        }
        List<String> anyOf = node.requirements().anyOf();
        if (!anyOf.isEmpty()) {
            MutableComponent line = GuiText.of("requires_any");
            for (int i = 0; i < anyOf.size(); i++) {
                line.append(i == 0 ? " " : " / ").append(subject.termLabel(anyOf.get(i)));
            }
            lines.add(line.withStyle(metColor(anyOf.stream().anyMatch(subject::holds))));
        }
        int minimumSectorPoints = node.requirements().minimumSectorPoints();
        if (minimumSectorPoints > 0) {
            lines.add(GuiText.of("requires_sector_points", minimumSectorPoints, Component.translatable(subject.sectorKey()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static ChatFormatting metColor(boolean met) {
        return met ? ChatFormatting.DARK_GREEN : ChatFormatting.RED;
    }

    private static Component exclusiveChoice(Subject subject, ExclusiveGroup group) {
        MutableComponent line = GuiText.of("exclusive", Component.translatable(group.nameKey()));
        for (String member : group.members()) {
            if (!member.equals(subject.node().id())) {
                boolean active = subject.state().isAllocated(member) || subject.pending().contains(member);
                line.append(" ").append(Component.translatable(subject.graph().nodes().get(member).nameKey())
                        .withStyle(active ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
        }
        return line.withStyle(ChatFormatting.GOLD);
    }

    private static void addStatus(List<Component> lines, Subject subject, NodeState nodeState, int pendingCost,
                                  Optional<PathFinder.Route> route) {
        switch (nodeState) {
            case ALLOCATED -> addAllocatedStatus(lines, subject);
            case PENDING -> lines.add(GuiText.of("status.pending").withStyle(ChatFormatting.AQUA));
            case AVAILABLE -> lines.add(availableStatus(subject, pendingCost));
            case CONFLICTING -> lines.add(conflictStatus(subject));
            case HIDDEN -> {
            }
            case LOCKED -> addLockedStatus(lines, subject, route);
        }
    }

    private static Component conflictStatus(Subject subject) {
        boolean consumed = AllocationService.eligibility(subject.graph(), subject.state(), subject.pending(), subject.node().id())
                .filter(why -> why.reason() == RejectReason.CONSUMED).isPresent();
        return GuiText.of(consumed ? "status.consumed" : "status.conflict").withStyle(ChatFormatting.RED);
    }

    private static void addAllocatedStatus(List<Component> lines, Subject subject) {
        lines.add(GuiText.of("status.allocated").withStyle(ChatFormatting.GOLD));
        if (ClientSkillState.economy().nodeRefundsEnabled()) {
            boolean startingRoot = subject.state().startingRoot().filter(subject.node().id()::equals).isPresent();
            lines.add(GuiText.of(startingRoot ? "status.root_reset_only" : "status.refundable").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static Component availableStatus(Subject subject, int pendingCost) {
        int pointsLeft = subject.state().availablePoints() - pendingCost;
        int cost = subject.cost();
        return pointsLeft < cost ? GuiText.of("status.unaffordable", cost, Math.max(0, pointsLeft)).withStyle(ChatFormatting.RED)
                : GuiText.of("status.available").withStyle(ChatFormatting.GREEN);
    }

    private static void addLockedStatus(List<Component> lines, Subject subject, Optional<PathFinder.Route> route) {
        Component reason = AllocationService.eligibility(subject.graph(), subject.state(), subject.pending(), subject.node().id())
                .<Component>map(why -> Component.translatable(why.reason().translationKey()))
                .orElse(GuiText.of("status.locked"));
        lines.add(GuiText.of("status.locked_reason", reason).withStyle(ChatFormatting.RED));
        route.ifPresent(found -> lines.add(routeHint(found)));
    }

    private static Component routeHint(PathFinder.Route route) {
        if (route.valid()) {
            return GuiText.of("route.hint", route.cost(), route.newNodes().size()).withStyle(ChatFormatting.YELLOW);
        }
        Component problem = Component.translatable(route.unresolved().orElseThrow().reason().translationKey());
        return GuiText.of("route.unresolved", problem).withStyle(ChatFormatting.DARK_RED);
    }

    private static void addAttributePreviews(List<Component> lines, SkillNodeDefinition node) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (SkillEffect effect : node.effects()) {
            if (effect instanceof SkillEffect.Attribute attribute) {
                BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attribute.attribute())).map(player::getAttribute)
                        .ifPresent(instance -> lines.add(attributePreview(instance, attribute)));
            }
        }
    }

    private static Component attributePreview(AttributeInstance instance, SkillEffect.Attribute attribute) {
        AttributeModifier preview = new AttributeModifier(PREVIEW_MODIFIER, attribute.amount(), EffectEngine.op(attribute.operation()));
        boolean percent = attribute.attribute().startsWith(NostalgiaSkills.MODID + ":");
        return GuiText.of("preview", Component.translatable(instance.getAttribute().value().getDescriptionId()),
                format(instance.getValue(), percent), format(AttributeMath.withExtra(instance, List.of(preview)), percent))
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    static String format(double value, boolean percent) {
        if (percent) {
            return String.format(Locale.ROOT, "%.1f%%", value * 100);
        }
        boolean whole = Math.abs(value - Math.rint(value)) < WHOLE_NUMBER_TOLERANCE;
        return String.format(Locale.ROOT, whole ? "%.0f" : "%.2f", value);
    }
}
