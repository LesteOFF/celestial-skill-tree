package dev.nostalgiacraft.skills.command;

import com.mojang.brigadier.context.CommandContext;
import dev.nostalgiacraft.skills.compat.Integrations;
import dev.nostalgiacraft.skills.data.GeneratedTree;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.graph.ValidationReport;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

final class TreeCommands {
    static final int MAX_LISTED_ERRORS = 10;
    static final int MAX_LISTED_WARNINGS = 5;

    private TreeCommands() {
    }

    static int regenerateWarning(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.translatable("command.nostalgiaskills.regenerate_warning"));
        return 0;
    }

    static int regenerate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GeneratedTree.regenerateFor(source.getServer());
        source.getServer().getPlayerList().getPlayers().forEach(SkillService::resync);
        int nodes = SkillTreeManager.graph().map(graph -> graph.nodes().size()).orElse(0);
        source.sendSuccess(() -> Component.translatable("command.nostalgiaskills.regenerated", nodes), true);
        return SkillTreeManager.graph().isPresent() ? 1 : 0;
    }

    static int integrations(CommandContext<CommandSourceStack> context) {
        Map<String, Boolean> installed = Integrations.installed();
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("command.nostalgiaskills.integrations", installed.size()), false);
        installed.forEach((mod, active) -> source.sendSuccess(() -> Component.translatable(
                "command.nostalgiaskills.integrations." + (active ? "active" : "disabled"), mod)
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED), false));
        return installed.size();
    }

    static int validate(CommandContext<CommandSourceStack> context) {
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
