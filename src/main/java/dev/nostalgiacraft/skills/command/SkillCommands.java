package dev.nostalgiacraft.skills.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * /nostalgiaskills open | inspect | points add|set | unlock | reset | validate.
 * Only "open" is available to everyone; everything else needs permission level 2.
 */
@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class SkillCommands {
    private static final SuggestionProvider<CommandSourceStack> NODES = (ctx, b) -> SharedSuggestionProvider.suggest(
            SkillTreeManager.graph().map(g -> g.ids().stream().toList()).orElse(List.of()), b);

    private SkillCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("nostalgiaskills")
                .then(Commands.literal("open").executes(SkillCommands::open))
                .then(Commands.literal("inspect").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(SkillCommands::inspect)))
                .then(Commands.literal("points").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-10_000, 10_000))
                                        .executes(c -> points(c, true, false))
                                        .then(Commands.argument("override_limit", BoolArgumentType.bool())
                                                .executes(c -> points(c, true, BoolArgumentType.getBool(c, "override_limit")))))))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("available_amount", IntegerArgumentType.integer(0, 100_000))
                                        .executes(c -> points(c, false, false))
                                        .then(Commands.argument("override_limit", BoolArgumentType.bool())
                                                .executes(c -> points(c, false, BoolArgumentType.getBool(c, "override_limit"))))))))
                .then(Commands.literal("unlock").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("node_id", ResourceLocationArgument.id()).suggests(NODES)
                                        .executes(c -> unlock(c, false))
                                        .then(Commands.literal("grant_cost").executes(c -> unlock(c, true))))))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(SkillCommands::reset)))
                .then(Commands.literal("validate").requires(s -> s.hasPermission(2)).executes(SkillCommands::validate)));
    }

    private static int open(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PacketDistributor.sendToPlayer(p, new Payloads.OpenScreen(false));
        return 1;
    }

    private static void changed(ServerPlayer p) {
        EffectEngine.refresh(p);
        SkillService.sendState(p);
    }

    private static int inspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        PlayerSkillState s = ModAttachments.state(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.inspect", p.getDisplayName(),
                s.purchasedPoints(), s.netGrantedPoints(), s.activeSpentPoints(), s.availablePoints(),
                s.allocations().size(), s.startingRoot().orElse("-"), s.stateRevision(),
                s.ledgerHolds() ? "ok" : "BROKEN"), false);
        return s.allocations().size();
    }

    private static int points(CommandContext<CommandSourceStack> c, boolean add, boolean override) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        PlayerSkillState s = ModAttachments.state(p);
        int limit = ServerConfig.POINT_LIMIT.get();
        LedgerService.Result r = add
                ? LedgerService.grant(s, IntegerArgumentType.getInteger(c, "amount"), limit, override)
                : LedgerService.setAvailable(s, IntegerArgumentType.getInteger(c, "available_amount"), limit, override);
        if (!r.ok()) {
            c.getSource().sendFailure(Component.translatable(r.reason().orElseThrow().translationKey()));
            return 0;
        }
        changed(p);
        c.getSource().sendSuccess(() -> Component.translatable(override ? "command.nostalgiaskills.points.override"
                : "command.nostalgiaskills.points", p.getDisplayName(), r.available(), limit), true);
        return r.available();
    }

    private static int unlock(CommandContext<CommandSourceStack> c, boolean grantCost) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        String id = ResourceLocationArgument.getId(c, "node_id").toString();
        SkillGraphSnapshot g = SkillTreeManager.graph().orElse(null);
        if (g == null) {
            c.getSource().sendFailure(Component.translatable("error.nostalgiaskills.tree_unavailable"));
            return 0;
        }
        PlayerSkillState s = ModAttachments.state(p);
        if (grantCost && g.contains(id)) {
            // Administrative grant of exactly the node's price, then the normal validated purchase; prerequisites
            // and connectivity still apply, so an invalid build can never be created.
            PlayerSkillState trial = s.copy();
            LedgerService.grant(trial, g.nodes().get(id).cost(), Integer.MAX_VALUE, true);
            if (!AllocationService.validate(g, trial, List.of(id)).ok()) {
                AllocationService.BatchResult why = AllocationService.validate(g, trial, List.of(id));
                c.getSource().sendFailure(Component.translatable(why.rejection().orElseThrow().reason().translationKey()));
                return 0;
            }
            LedgerService.grant(s, g.nodes().get(id).cost(), Integer.MAX_VALUE, true);
        }
        AllocationService.BatchResult r = AllocationService.confirm(g, s, List.of(id));
        if (!r.ok()) {
            c.getSource().sendFailure(Component.translatable(r.rejection().orElseThrow().reason().translationKey())
                    .append(" (" + id + ")"));
            return 0;
        }
        changed(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.unlock", id, p.getDisplayName(),
                r.totalCost()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        RespecService.Preview done = RespecService.resetAll(ModAttachments.state(p));
        changed(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.reset", p.getDisplayName(),
                done.refund(), done.removed().size()), true);
        return done.refund();
    }

    private static int validate(CommandContext<CommandSourceStack> c) {
        ValidationReport r = SkillTreeManager.lastReport();
        c.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.validate",
                SkillTreeManager.lastResult().name(), SkillTreeManager.graph().map(g -> g.nodes().size()).orElse(0),
                SkillTreeManager.revision(), r.errors().size() + SkillTreeManager.readErrors().size(),
                r.warnings().size()), false);
        SkillTreeManager.readErrors().stream().limit(10).forEach(e ->
                c.getSource().sendFailure(Component.literal(e)));
        r.errors().stream().limit(10).forEach(i -> c.getSource().sendFailure(Component.literal(i.toString())));
        r.warnings().stream().limit(5).forEach(i -> c.getSource().sendSuccess(() -> Component.literal(i.toString()), false));
        return r.hasErrors() ? 0 : 1;
    }
}
