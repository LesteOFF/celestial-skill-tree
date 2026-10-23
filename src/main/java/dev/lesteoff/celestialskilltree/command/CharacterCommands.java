package dev.lesteoff.celestialskilltree.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.progression.DiscoveryService;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.SignService;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.server.Discoveries;
import dev.lesteoff.celestialskilltree.server.ExtraSign;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;
import java.util.Random;

final class CharacterCommands {
    private CharacterCommands() {
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayers(context, SkillCommands.TARGETS);
    }

    static int clearCooldowns(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = targets(context);
        players.forEach(player -> ModAttachments.cooldowns(player).clear());
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.cooldowns", players.size()), true);
        return players.size();
    }

    static int reveal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String key = ResourceLocationArgument.getId(context, "key").toString();
        boolean known = SkillTreeManager.graph().filter(graph -> DiscoveryService.keys(graph).contains(key)).isPresent();
        if (!known) {
            context.getSource().sendFailure(Component.translatable("command.celestialskilltree.discoveries.unknown", key));
            return 0;
        }
        int revealed = 0;
        for (ServerPlayer player : targets(context)) {
            boolean had = ModAttachments.state(player).discoveries().contains(key);
            Discoveries.reveal(player, key);
            revealed += had ? 0 : 1;
        }
        int count = revealed;
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.discoveries.reveal", key, count), true);
        return count;
    }

    static int forget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int forgotten = 0;
        for (ServerPlayer player : targets(context)) {
            if (DiscoveryService.forgetAll(ModAttachments.state(player))) {
                PlayerCommands.changed(player);
                forgotten++;
            }
        }
        int count = forgotten;
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.discoveries.clear", count), true);
        return count;
    }

    static int setSign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String signId = ResourceLocationArgument.getId(context, "sign").toString();
        if (SkillTreeManager.sign(signId).isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.celestialskilltree.sign.unknown", signId));
            return 0;
        }
        Collection<ServerPlayer> players = targets(context);
        for (ServerPlayer player : players) {
            SignService.set(ModAttachments.state(player), signId);
            announceSign(context, player, signId);
        }
        return players.size();
    }

    static int rerollSign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int rerolled = 0;
        for (ServerPlayer player : targets(context)) {
            PlayerSkillState state = ModAttachments.state(player);
            Optional<String> signId = SignService.reroll(state, SkillTreeManager.sortedSignIds(), new Random(player.getRandom().nextLong()));
            if (signId.isEmpty()) {
                context.getSource().sendFailure(Component.translatable("command.celestialskilltree.sign.no_other", player.getDisplayName()));
                continue;
            }
            announceSign(context, player, signId.get());
            rerolled++;
        }
        return rerolled;
    }

    private static void announceSign(CommandContext<CommandSourceStack> context, ServerPlayer player, String signId) {
        PlayerCommands.changed(player);
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.sign.set", player.getDisplayName(),
                Component.translatable(SkillSign.nameKey(signId))), true);
    }

    static int extraSign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        boolean granted = BoolArgumentType.getBool(context, "granted");
        Collection<ServerPlayer> players = targets(context);
        for (ServerPlayer player : players) {
            ExtraSign.setGranted(player, granted);
            context.getSource().sendSuccess(() -> Component.translatable(granted ? "command.celestialskilltree.extrasign.granted"
                    : "command.celestialskilltree.extrasign.revoked", player.getDisplayName()), true);
        }
        return players.size();
    }
}
