package dev.nostalgiacraft.skills.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.ValidationReport;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.server.CommunityTree;
import dev.nostalgiacraft.skills.server.Eclipse;
import dev.nostalgiacraft.skills.server.ShootingStars;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class SkillCommands {
    private static final int ADMIN_PERMISSION = 2;
    private static final int MAX_GRANT = 10_000;
    private static final int MAX_AVAILABLE = 100_000;
    private static final int MAX_LISTED_ERRORS = 10;
    private static final int MAX_LISTED_WARNINGS = 5;
    private static final SuggestionProvider<CommandSourceStack> NODES = (context, builder) -> SharedSuggestionProvider.suggest(
            SkillTreeManager.graph().map(graph -> graph.ids().stream().toList()).orElse(List.of()), builder);

    private SkillCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nostalgiaskills")
                .then(Commands.literal("open").executes(SkillCommands::open))
                .then(admin("inspect").then(playerArgument().executes(SkillCommands::inspect)))
                .then(admin("points")
                        .then(Commands.literal("add").then(playerArgument()
                                .then(pointsArgument("amount", -MAX_GRANT, MAX_GRANT, true))))
                        .then(Commands.literal("set").then(playerArgument()
                                .then(pointsArgument("available_amount", 0, MAX_AVAILABLE, false)))))
                .then(admin("unlock").then(playerArgument()
                        .then(Commands.argument("node_id", ResourceLocationArgument.id()).suggests(NODES)
                                .executes(context -> unlock(context, false))
                                .then(Commands.literal("grant_cost").executes(context -> unlock(context, true))))))
                .then(admin("reset").then(playerArgument().executes(SkillCommands::reset)))
                .then(admin("star").then(playerArgument().executes(SkillCommands::star)))
                .then(admin("community").then(Commands.literal("set")
                        .then(Commands.argument("xp", IntegerArgumentType.integer(0)).executes(SkillCommands::community))))
                .then(admin("eclipse")
                        .then(Commands.literal("start").executes(context -> eclipse(context, true)))
                        .then(Commands.literal("stop").executes(context -> eclipse(context, false))))
                .then(admin("validate").executes(SkillCommands::validate)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(ADMIN_PERMISSION));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> playerArgument() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> pointsArgument(String name, int min, int max,
                                                                                    boolean add) {
        return Commands.argument(name, IntegerArgumentType.integer(min, max))
                .executes(context -> points(context, name, add, false))
                .then(Commands.argument("override_limit", BoolArgumentType.bool())
                        .executes(context -> points(context, name, add, BoolArgumentType.getBool(context, "override_limit"))));
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), new Payloads.OpenScreen(false));
        return 1;
    }

    private static void changed(ServerPlayer player) {
        EffectEngine.refresh(player);
        SkillService.sendState(player);
    }

    private static void fail(CommandContext<CommandSourceStack> context, RejectReason reason) {
        context.getSource().sendFailure(Component.translatable(reason.translationKey()));
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        PlayerSkillState state = ModAttachments.state(player);
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.inspect", player.getDisplayName(),
                state.purchasedPoints(), state.netGrantedPoints(), state.activeSpentPoints(), state.availablePoints(),
                state.allocations().size(), state.startingRoot().orElse("-"), state.stateRevision(),
                state.ledgerHolds() ? "ok" : "BROKEN"), false);
        return state.allocations().size();
    }

    private static int points(CommandContext<CommandSourceStack> context, String amountArgument, boolean add,
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

    private static int unlock(CommandContext<CommandSourceStack> context, boolean grantCost) throws CommandSyntaxException {
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
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.unlock", id,
                player.getDisplayName(), result.totalCost()), true);
        return 1;
    }

    private static boolean grantCostOf(CommandContext<CommandSourceStack> context, SkillGraphSnapshot graph,
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

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        RespecService.Preview done = RespecService.resetAll(ModAttachments.state(player));
        changed(player);
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.reset", player.getDisplayName(),
                done.refund(), done.removed().size()), true);
        return done.refund();
    }

    private static int star(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        boolean fell = ShootingStars.spawnNear(player).isPresent();
        context.getSource().sendSuccess(() -> Component.translatable(fell ? "command.nostalgiaskills.star"
                : "command.nostalgiaskills.star.unloaded", player.getDisplayName()), true);
        return fell ? 1 : 0;
    }

    private static int eclipse(CommandContext<CommandSourceStack> context, boolean start) {
        Eclipse.force(context.getSource().getServer(), start);
        context.getSource().sendSuccess(() -> Component.translatable(start ? "command.nostalgiaskills.eclipse.start"
                : "command.nostalgiaskills.eclipse.stop"), true);
        return 1;
    }

    private static int community(CommandContext<CommandSourceStack> context) {
        int xp = IntegerArgumentType.getInteger(context, "xp");
        CommunityTree.setProgress(context.getSource().getServer(), xp);
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.community", xp), true);
        return 1;
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ValidationReport report = SkillTreeManager.lastReport();
        List<String> readErrors = SkillTreeManager.readErrors();
        source.sendSuccess(() -> Component.translatable("command.nostalgiaskills.validate",
                SkillTreeManager.lastResult().name(), SkillTreeManager.graph().map(graph -> graph.nodes().size()).orElse(0),
                SkillTreeManager.revision(), report.errors().size() + readErrors.size(), report.warnings().size()), false);
        readErrors.stream().limit(MAX_LISTED_ERRORS).forEach(error -> source.sendFailure(Component.literal(error)));
        report.errors().stream().limit(MAX_LISTED_ERRORS)
                .forEach(issue -> source.sendFailure(Component.literal(issue.toString())));
        report.warnings().stream().limit(MAX_LISTED_WARNINGS)
                .forEach(issue -> source.sendSuccess(() -> Component.literal(issue.toString()), false));
        return report.hasErrors() ? 0 : 1;
    }
}
