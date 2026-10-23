package dev.lesteoff.celestialskilltree.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.LedgerService;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.RefundService;
import dev.lesteoff.celestialskilltree.progression.RejectReason;
import dev.lesteoff.celestialskilltree.progression.RespecService;
import dev.lesteoff.celestialskilltree.progression.ToggleService;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.server.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

final class PlayerCommands {
    static final int DEFAULT_AUDIT_LINES = 10;

    private PlayerCommands() {
    }

    static void changed(ServerPlayer player) {
        EffectEngine.refresh(player);
        SkillService.sendState(player);
    }

    static void fail(CommandContext<CommandSourceStack> context, RejectReason reason) {
        context.getSource().sendFailure(Component.translatable(reason.translationKey()));
    }

    static void failFor(CommandContext<CommandSourceStack> context, ServerPlayer player, RejectReason reason) {
        context.getSource().sendFailure(Component.translatable(reason.translationKey()).append(" (").append(player.getDisplayName()).append(")"));
    }

    private static Optional<SkillGraphSnapshot> graph(CommandContext<CommandSourceStack> context) {
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        if (graph.isEmpty()) {
            fail(context, RejectReason.TREE_UNAVAILABLE);
        }
        return graph;
    }

    private static String node(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, SkillCommands.NODE).toString();
    }

    static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, SkillCommands.PLAYER);
        PlayerSkillState state = ModAttachments.state(player);
        int nextPointCost = ServerConfig.progression().curve().cost(state.purchasedPoints());
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.inspect", player.getDisplayName(),
                state.purchasedPoints(), state.netGrantedPoints(), state.activeSpentPoints(), state.availablePoints(),
                state.allocations().size(), state.startingRoot().orElse("-"), state.stateRevision(),
                state.ledgerHolds() ? "ok" : "BROKEN"), false);
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.inspect.details",
                state.sign().orElse("-"), state.discoveries().size(), state.disabled().size(), state.lifetimeSpent(),
                state.lifetimeRefunded(), nextPointCost, state.audit().size()).withStyle(ChatFormatting.GRAY), false);
        return state.allocations().size();
    }

    static int audit(CommandContext<CommandSourceStack> context, int lines) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, SkillCommands.PLAYER);
        List<String> audit = ModAttachments.state(player).audit();
        List<String> shown = audit.subList(Math.max(0, audit.size() - lines), audit.size());
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.audit", player.getDisplayName(),
                shown.size(), audit.size()), false);
        shown.forEach(line -> context.getSource().sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false));
        return shown.size();
    }

    static int points(CommandContext<CommandSourceStack> context, String amountArgument, boolean add,
                      boolean override) throws CommandSyntaxException {
        int limit = ServerConfig.POINT_LIMIT.get();
        int amount = IntegerArgumentType.getInteger(context, amountArgument);
        String messageKey = override ? "command.celestialskilltree.points.override" : "command.celestialskilltree.points";
        int done = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, SkillCommands.TARGETS)) {
            PlayerSkillState state = ModAttachments.state(player);
            int before = state.availablePoints();
            LedgerService.Result result = add ? LedgerService.grant(state, amount, limit, override)
                    : LedgerService.setAvailable(state, amount, limit, override);
            if (!result.ok()) {
                failFor(context, player, result.reason().orElseThrow());
                continue;
            }
            changed(player);
            SkillEvents.post(new SkillEvents.PointsGranted(player, result.available() - before, SkillEvents.GrantSource.COMMAND));
            context.getSource().sendSuccess(() -> Component.translatable(messageKey, player.getDisplayName(), result.available(), limit), true);
            done++;
        }
        return done;
    }

    static int unlock(CommandContext<CommandSourceStack> context, boolean grantCost) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, SkillCommands.PLAYER);
        String id = node(context);
        Optional<SkillGraphSnapshot> graph = graph(context);
        if (graph.isEmpty()) {
            return 0;
        }
        PlayerSkillState state = ModAttachments.state(player);
        if (grantCost && graph.get().contains(id) && !grantCostOf(context, player, graph.get(), state, id)) {
            return 0;
        }
        AllocationService.BatchResult result = AllocationService.confirm(graph.get(), state, List.of(id));
        if (!result.ok()) {
            RejectReason reason = result.rejection().orElseThrow().reason();
            context.getSource().sendFailure(Component.translatable(reason.translationKey()).append(" (" + id + ")"));
            return 0;
        }
        changed(player);
        SkillEvents.post(new SkillEvents.NodesLearned(player, result.nodeIds()));
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.unlock", id,
                player.getDisplayName(), result.totalCost()), true);
        return 1;
    }

    private static boolean grantCostOf(CommandContext<CommandSourceStack> context, ServerPlayer player, SkillGraphSnapshot graph,
                                       PlayerSkillState state, String id) {
        int cost = graph.nodes().get(id).cost();
        PlayerSkillState trial = state.copy();
        LedgerService.grant(trial, cost, Integer.MAX_VALUE, true);
        AllocationService.BatchResult trialResult = AllocationService.validate(graph, trial, List.of(id));
        if (!trialResult.ok()) {
            fail(context, trialResult.rejection().orElseThrow().reason());
            return false;
        }
        LedgerService.grant(state, cost, Integer.MAX_VALUE, true);
        SkillEvents.post(new SkillEvents.PointsGranted(player, cost, SkillEvents.GrantSource.COMMAND));
        return true;
    }

    static int refund(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, SkillCommands.PLAYER);
        String id = node(context);
        Optional<SkillGraphSnapshot> graph = graph(context);
        if (graph.isEmpty()) {
            return 0;
        }
        RefundService.Plan done = RefundService.apply(graph.get(), ModAttachments.state(player), id, true);
        if (!done.ok()) {
            context.getSource().sendFailure(Component.translatable(done.reason().orElseThrow().translationKey()).append(" (" + id + ")"));
            return 0;
        }
        changed(player);
        SkillEvents.post(new SkillEvents.NodesRefunded(player, done.removed(), done.refund()));
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.refund", id, player.getDisplayName(),
                done.refund(), done.removed().size()), true);
        return done.refund();
    }

    static int toggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, SkillCommands.PLAYER);
        String id = node(context);
        Optional<SkillGraphSnapshot> graph = graph(context);
        if (graph.isEmpty()) {
            return 0;
        }
        PlayerSkillState state = ModAttachments.state(player);
        Optional<RejectReason> rejected = ToggleService.toggle(graph.get(), state, id);
        if (rejected.isPresent()) {
            context.getSource().sendFailure(Component.translatable(rejected.get().translationKey()).append(" (" + id + ")"));
            return 0;
        }
        changed(player);
        String messageKey = state.isDisabled(id) ? "command.celestialskilltree.toggle.off" : "command.celestialskilltree.toggle.on";
        context.getSource().sendSuccess(() -> Component.translatable(messageKey, id, player.getDisplayName()), true);
        return 1;
    }

    static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int refunded = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, SkillCommands.TARGETS)) {
            RespecService.Preview done = RespecService.resetAll(ModAttachments.state(player));
            changed(player);
            SkillEvents.post(new SkillEvents.BuildReset(player, done.removed(), done.refund()));
            context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.reset", player.getDisplayName(),
                    done.refund(), done.removed().size()), true);
            refunded += done.refund();
        }
        return refunded;
    }
}
