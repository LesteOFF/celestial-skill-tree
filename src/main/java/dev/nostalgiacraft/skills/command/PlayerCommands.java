package dev.nostalgiacraft.skills.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.server.ExtraSign;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

final class PlayerCommands {
    private PlayerCommands() {
    }

    static void changed(ServerPlayer player) {
        EffectEngine.refresh(player);
        SkillService.sendState(player);
    }

    static void fail(CommandContext<CommandSourceStack> context, RejectReason reason) {
        context.getSource().sendFailure(Component.translatable(reason.translationKey()));
    }

    static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        PlayerSkillState state = ModAttachments.state(player);
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.inspect", player.getDisplayName(),
                state.purchasedPoints(), state.netGrantedPoints(), state.activeSpentPoints(), state.availablePoints(),
                state.allocations().size(), state.startingRoot().orElse("-"), state.stateRevision(),
                state.ledgerHolds() ? "ok" : "BROKEN"), false);
        return state.allocations().size();
    }

    static int points(CommandContext<CommandSourceStack> context, String amountArgument, boolean add,
                              boolean override) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        PlayerSkillState state = ModAttachments.state(player);
        int limit = ServerConfig.POINT_LIMIT.get();
        int amount = IntegerArgumentType.getInteger(context, amountArgument);
        LedgerService.Result result = add ? LedgerService.grant(state, amount, limit, override)
                : LedgerService.setAvailable(state, amount, limit, override);
        if (!result.ok()) {
            fail(context, result.reason().orElseThrow());
            return 0;
        }
        changed(player);
        String messageKey = override ? "command.nostalgiaskills.points.override" : "command.nostalgiaskills.points";
        context.getSource().sendSuccess(
                () -> Component.translatable(messageKey, player.getDisplayName(), result.available(), limit), true);
        return result.available();
    }

    static int unlock(CommandContext<CommandSourceStack> context, boolean grantCost) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String id = ResourceLocationArgument.getId(context, "node_id").toString();
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        if (graph.isEmpty()) {
            fail(context, RejectReason.TREE_UNAVAILABLE);
            return 0;
        }
        PlayerSkillState state = ModAttachments.state(player);
        if (grantCost && graph.get().contains(id) && !grantCostOf(context, graph.get(), state, id)) {
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
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.unlock", id,
                player.getDisplayName(), result.totalCost()), true);
        return 1;
    }

    static boolean grantCostOf(CommandContext<CommandSourceStack> context, SkillGraphSnapshot graph,
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
        return true;
    }

    static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        RespecService.Preview done = RespecService.resetAll(ModAttachments.state(player));
        changed(player);
        SkillEvents.post(new SkillEvents.BuildReset(player, done.removed(), done.refund()));
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.reset", player.getDisplayName(),
                done.refund(), done.removed().size()), true);
        return done.refund();
    }

    static int extraSign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        boolean granted = BoolArgumentType.getBool(context, "granted");
        ExtraSign.setGranted(player, granted);
        context.getSource().sendSuccess(() -> Component.translatable(granted ? "command.nostalgiaskills.extrasign.granted"
                : "command.nostalgiaskills.extrasign.revoked", player.getDisplayName()), true);
        return 1;
    }
}
