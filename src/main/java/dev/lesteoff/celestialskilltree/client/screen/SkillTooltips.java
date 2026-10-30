package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.client.ClientSkillState;
import dev.lesteoff.celestialskilltree.client.render.GraphRenderer;
import dev.lesteoff.celestialskilltree.client.screen.TooltipCard.Marker;
import dev.lesteoff.celestialskilltree.client.screen.TooltipCard.Row;
import dev.lesteoff.celestialskilltree.effects.BorrowedNodes;
import dev.lesteoff.celestialskilltree.graph.ExclusiveGroup;
import dev.lesteoff.celestialskilltree.graph.Sector;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.PathFinder;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class SkillTooltips {
    private static final double WHOLE_NUMBER_TOLERANCE = 1e-6;
    private static final int NAMES_SHOWN = 3;

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
                    .orElse(GuiText.of("unknown_skill", readable(term)));
        }

        String sectorKey() {
            return graph.sectors().stream().filter(sector -> sector.id().equals(node.sector())).map(Sector::nameKey)
                    .findFirst().orElse(node.sector());
        }

        int cost() {
            return AllocationService.priceWithFreeStart(graph, state, pending, node);
        }

        List<SkillNodeDefinition> neighbours() {
            return graph.neighbours(node.id()).stream().flatMap(id -> graph.node(id).stream()).toList();
        }

        Optional<SkillNodeDefinition> blackHoleNextTo() {
            return neighbours().stream().filter(neighbour -> neighbour.traits().amplifiesNeighbours() && holds(neighbour.id())).findFirst();
        }
    }

    private SkillTooltips() {
    }

    public static TooltipCard build(SkillGraphSnapshot graph, PlayerSkillState state, Collection<String> pending,
                                    SkillNodeDefinition node, NodeState nodeState, int pendingCost,
                                    Optional<PathFinder.Route> route, boolean detailed) {
        Subject subject = new Subject(graph, state, pending, node);
        return new TooltipCard(TooltipCard.Icon.node(GraphRenderer.frameTex(node.frame()), GraphRenderer.iconTex(node.icon())),
                SkillTexts.name(node).withStyle(nameColor(node), ChatFormatting.BOLD), subtitle(subject), badge(subject),
                List.of(whatItDoes(subject, detailed), specialRules(subject), status(subject, nodeState, pendingCost, route)));
    }

    private static ChatFormatting nameColor(SkillNodeDefinition node) {
        return switch (node.type()) {
            case KEYSTONE -> ChatFormatting.GOLD;
            case NOTABLE -> node.traits().amplifiesNeighbours() ? ChatFormatting.DARK_PURPLE : ChatFormatting.LIGHT_PURPLE;
            case ROOT -> ChatFormatting.AQUA;
            case MINOR -> ChatFormatting.WHITE;
            case PORTAL -> ChatFormatting.DARK_AQUA;
        };
    }

    private static Component subtitle(Subject subject) {
        String type = subject.node().traits().amplifiesNeighbours() ? "type.black_hole" : "type." + subject.node().type().serializedName();
        return Component.translatable(subject.sectorKey()).append(" · ").append(GuiText.of(type)).withStyle(ChatFormatting.GRAY);
    }

    private static Component badge(Subject subject) {
        SkillNodeDefinition node = subject.node();
        boolean owned = subject.state().isAllocated(node.id());
        int cost = owned ? subject.state().allocations().get(node.id()).paidCost() : subject.cost();
        return GuiText.of("cost", cost).withStyle(ChatFormatting.YELLOW);
    }

    private static List<Row> whatItDoes(Subject subject, boolean detailed) {
        SkillNodeDefinition node = subject.node();
        List<Row> rows = new ArrayList<>();
        boolean written = !node.descriptionKey().isEmpty();
        if (written) {
            rows.add(Row.plain(Component.translatable(node.descriptionKey()).withStyle(ChatFormatting.WHITE)));
        }
        List<SkillTexts.EffectLine> effects = SkillTexts.effectLines(node);
        if (!written || detailed) {
            effects.forEach(line -> rows.add(effectRow(line)));
        } else if (!effects.isEmpty()) {
            rows.add(Row.plain(GuiText.of("hold_shift").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }
        if (node.ranked()) {
            rows.add(Row.of(Marker.RANK, rankLine(subject).withStyle(ChatFormatting.YELLOW)));
        }
        return rows;
    }

    private static Row effectRow(SkillTexts.EffectLine line) {
        return switch (line.tone()) {
            case GAIN -> Row.of(Marker.GAIN, line.text().withStyle(ChatFormatting.GREEN));
            case COST -> Row.of(Marker.COST, line.text().withStyle(ChatFormatting.RED));
            case NEUTRAL -> Row.of(Marker.EFFECT, line.text().withStyle(ChatFormatting.GOLD));
        };
    }

    private static MutableComponent rankLine(Subject subject) {
        SkillNodeDefinition node = subject.node();
        int rank = subject.state().rank(node.id()) + lentRanks(node);
        int marked = Collections.frequency(subject.pending(), node.id());
        return marked > 0 ? GuiText.of("rank_pending", rank, node.maxRank(), marked) : GuiText.of("rank", rank, node.maxRank());
    }

    private static int lentRanks(SkillNodeDefinition node) {
        Player player = Minecraft.getInstance().player;
        return player != null && BorrowedNodes.lentTo(player).contains(node.id()) ? 1 : 0;
    }

    private static List<Row> specialRules(Subject subject) {
        SkillNodeDefinition node = subject.node();
        List<Row> rows = new ArrayList<>();
        subject.graph().portalDestination(node.id()).flatMap(subject.graph()::node).ifPresent(destination -> rows.add(
                Row.of(Marker.PORTAL, GuiText.of("portal_destination", SkillTexts.name(destination)).withStyle(ChatFormatting.AQUA))));
        if (node.traits().amplifiesNeighbours()) {
            addBlackHole(rows, subject);
        } else if (subject.state().isAllocated(node.id())) {
            subject.blackHoleNextTo().ifPresent(blackHole -> rows.add(Row.of(Marker.BLACK_HOLE, GuiText.of("black_hole.boosted_by",
                    multiplier(blackHole), SkillTexts.name(blackHole)).withStyle(ChatFormatting.LIGHT_PURPLE))));
        }
        subject.graph().groupOf(node.id()).ifPresent(group -> rows.add(Row.of(Marker.PAIR, exclusiveChoice(subject, group))));
        addRequirements(rows, subject);
        return rows;
    }

    private static void addBlackHole(List<Row> rows, Subject subject) {
        rows.add(Row.of(Marker.BLACK_HOLE, GuiText.of("amplifier", multiplier(subject.node())).withStyle(ChatFormatting.LIGHT_PURPLE)));
        if (!subject.holds(subject.node().id())) {
            return;
        }
        List<SkillNodeDefinition> boosted = subject.neighbours().stream().filter(neighbour -> subject.holds(neighbour.id())).toList();
        List<SkillNodeDefinition> sealed = subject.neighbours().stream().filter(neighbour -> !subject.holds(neighbour.id())).toList();
        if (!boosted.isEmpty()) {
            rows.add(Row.of(Marker.GAIN, GuiText.of("black_hole.boosts", names(boosted)).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        if (!sealed.isEmpty()) {
            rows.add(Row.of(Marker.SEALED, GuiText.of("black_hole.seals", names(sealed)).withStyle(ChatFormatting.DARK_PURPLE)));
        }
    }

    private static String multiplier(SkillNodeDefinition blackHole) {
        return BigDecimal.valueOf(blackHole.traits().neighbourMultiplier()).stripTrailingZeros().toPlainString();
    }

    private static Component names(List<SkillNodeDefinition> nodes) {
        MutableComponent joined = Component.empty();
        nodes.stream().limit(NAMES_SHOWN).forEach(node -> joined.append(joined.getSiblings().isEmpty() ? Component.empty()
                : Component.literal(", ")).append(SkillTexts.name(node)));
        if (nodes.size() > NAMES_SHOWN) {
            joined.append(GuiText.of("and_more", nodes.size() - NAMES_SHOWN));
        }
        return joined;
    }

    private static Component exclusiveChoice(Subject subject, ExclusiveGroup group) {
        MutableComponent others = Component.empty();
        group.members().stream().filter(member -> !member.equals(subject.node().id())).forEach(member -> {
            boolean taken = subject.holds(member);
            others.append(others.getSiblings().isEmpty() ? Component.empty() : Component.literal(" / "))
                    .append(SkillTexts.name(subject.graph().nodes().get(member)).withStyle(taken ? ChatFormatting.RED : ChatFormatting.WHITE));
        });
        return GuiText.of("exclusive", others).withStyle(ChatFormatting.GOLD);
    }

    private static void addRequirements(List<Row> rows, Subject subject) {
        SkillNodeDefinition node = subject.node();
        for (String term : node.requirements().allOf()) {
            rows.add(Row.of(Marker.REQUIREMENT, GuiText.of("requires_all", subject.termLabel(term)).withStyle(metColor(subject.holds(term)))));
        }
        List<String> anyOf = node.requirements().anyOf();
        if (!anyOf.isEmpty()) {
            MutableComponent line = GuiText.of("requires_any");
            for (int i = 0; i < anyOf.size(); i++) {
                line.append(i == 0 ? " " : " / ").append(subject.termLabel(anyOf.get(i)));
            }
            rows.add(Row.of(Marker.REQUIREMENT, line.withStyle(metColor(anyOf.stream().anyMatch(subject::holds)))));
        }
        int minimumSectorPoints = node.requirements().minimumSectorPoints();
        if (minimumSectorPoints > 0) {
            rows.add(Row.of(Marker.REQUIREMENT, GuiText.of("requires_sector_points", minimumSectorPoints,
                    Component.translatable(subject.sectorKey())).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static ChatFormatting metColor(boolean met) {
        return met ? ChatFormatting.DARK_GREEN : ChatFormatting.RED;
    }

    private static List<Row> status(Subject subject, NodeState nodeState, int pendingCost, Optional<PathFinder.Route> route) {
        List<Row> rows = new ArrayList<>();
        switch (nodeState) {
            case ALLOCATED -> addAllocatedStatus(rows, subject);
            case PENDING -> rows.add(Row.of(Marker.CHECK, GuiText.of("status.pending").withStyle(ChatFormatting.AQUA)));
            case AVAILABLE -> rows.add(availableStatus(subject, pendingCost));
            case CONFLICTING -> rows.add(Row.of(Marker.PAIR, GuiText.of("status.conflict").withStyle(ChatFormatting.RED)));
            case SEALED -> rows.add(Row.of(Marker.SEALED, sealedStatus(subject)));
            case HIDDEN -> {
            }
            case LOCKED -> addLockedStatus(rows, subject, route);
        }
        return rows;
    }

    private static Component sealedStatus(Subject subject) {
        return subject.blackHoleNextTo()
                .<Component>map(blackHole -> GuiText.of("status.sealed_by", SkillTexts.name(blackHole)))
                .orElse(GuiText.of("status.consumed")).copy().withStyle(ChatFormatting.DARK_PURPLE);
    }

    private static void addAllocatedStatus(List<Row> rows, Subject subject) {
        boolean disabled = subject.state().isDisabled(subject.node().id());
        rows.add(disabled ? Row.of(Marker.LOCK, GuiText.of("status.disabled").withStyle(ChatFormatting.RED))
                : Row.of(Marker.CHECK, GuiText.of("status.allocated").withStyle(ChatFormatting.GREEN)));
        if (subject.node().traits().toggleable()) {
            rows.add(Row.of(Marker.CLICK, GuiText.of(disabled ? "status.toggle_on" : "status.toggle_off").withStyle(ChatFormatting.YELLOW)));
        }
        boolean canRankUp = AllocationService.canRankUp(subject.graph(), subject.state(), subject.node().id());
        if (subject.node().ranked()) {
            rows.add(rankStatus(subject, canRankUp));
        }
        if (ClientSkillState.economy().nodeRefundsEnabled()) {
            boolean startingRoot = subject.state().startingRoot().filter(subject.node().id()::equals).isPresent();
            String refund = startingRoot ? "status.root_reset_only" : canRankUp ? "status.refundable_shift" : "status.refundable";
            rows.add(Row.of(Marker.CLICK, GuiText.of(refund).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static Row rankStatus(Subject subject, boolean canRankUp) {
        String id = subject.node().id();
        int marked = Collections.frequency(subject.pending(), id);
        if (!canRankUp) {
            return Row.of(Marker.RANK, GuiText.of("status.max_rank").withStyle(ChatFormatting.GOLD));
        }
        if (marked >= AllocationService.rankRoom(subject.graph(), subject.state(), id)) {
            return Row.of(Marker.CLICK, GuiText.of("status.rank_pending_full").withStyle(ChatFormatting.AQUA));
        }
        if (marked > 0) {
            return Row.of(Marker.CLICK, GuiText.of("status.rank_pending", marked).withStyle(ChatFormatting.AQUA));
        }
        return Row.of(Marker.CLICK, GuiText.of("status.rank_up", subject.state().rank(id) + 1, subject.node().cost())
                .withStyle(ChatFormatting.YELLOW));
    }

    private static Row availableStatus(Subject subject, int pendingCost) {
        int pointsLeft = subject.state().availablePoints() - pendingCost;
        int cost = subject.cost();
        return pointsLeft < cost
                ? Row.of(Marker.LOCK, GuiText.of("status.unaffordable", cost, Math.max(0, pointsLeft)).withStyle(ChatFormatting.RED))
                : Row.of(Marker.CLICK, GuiText.of("status.available").withStyle(ChatFormatting.YELLOW));
    }

    private static void addLockedStatus(List<Row> rows, Subject subject, Optional<PathFinder.Route> route) {
        Component reason = AllocationService.eligibility(subject.graph(), subject.state(), subject.pending(), subject.node().id())
                .<Component>map(why -> Component.translatable(why.reason().translationKey()))
                .orElse(GuiText.of("status.locked"));
        rows.add(Row.of(Marker.LOCK, reason.copy().withStyle(ChatFormatting.RED)));
        route.ifPresent(found -> rows.add(routeHint(found)));
    }

    private static Row routeHint(PathFinder.Route route) {
        if (route.valid()) {
            return Row.of(Marker.CLICK, GuiText.of("route.hint", route.cost(), route.newNodes().size()).withStyle(ChatFormatting.YELLOW));
        }
        Component problem = Component.translatable(route.unresolved().orElseThrow().reason().translationKey());
        return Row.of(Marker.LOCK, GuiText.of("route.unresolved", problem).withStyle(ChatFormatting.DARK_RED));
    }

    private static String readable(String nodeId) {
        return nodeId.substring(nodeId.indexOf(':') + 1).replace('_', ' ');
    }

    static String format(double value, boolean percent) {
        if (percent) {
            return String.format(GuiText.locale(), "%.1f%%", value * 100);
        }
        boolean whole = Math.abs(value - Math.rint(value)) < WHOLE_NUMBER_TOLERANCE;
        return String.format(GuiText.locale(), whole ? "%.0f" : "%.2f", value);
    }
}
