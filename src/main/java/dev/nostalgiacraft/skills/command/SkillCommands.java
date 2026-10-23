package dev.nostalgiacraft.skills.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class SkillCommands {
    private static final int ADMIN_PERMISSION = 2;
    private static final int OWNER_PERMISSION = 3;
    private static final int MAX_GRANT = 10_000;
    private static final int MAX_AVAILABLE = 100_000;
    private static final SuggestionProvider<CommandSourceStack> NODES = (context, builder) -> SharedSuggestionProvider.suggest(
            SkillTreeManager.graph().map(graph -> graph.ids().stream().toList()).orElse(List.of()), builder);

    private SkillCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nostalgiaskills")
                .then(Commands.literal("open").executes(SkillCommands::open))
                .then(admin("inspect").then(playerArgument().executes(PlayerCommands::inspect)))
                .then(owner("points")
                        .then(Commands.literal("add").then(playerArgument()
                                .then(pointsArgument("amount", -MAX_GRANT, MAX_GRANT, true))))
                        .then(Commands.literal("set").then(playerArgument()
                                .then(pointsArgument("available_amount", 0, MAX_AVAILABLE, false)))))
                .then(owner("unlock").then(playerArgument()
                        .then(Commands.argument("node_id", ResourceLocationArgument.id()).suggests(NODES)
                                .executes(context -> PlayerCommands.unlock(context, false))
                                .then(Commands.literal("grant_cost").executes(context -> PlayerCommands.unlock(context, true))))))
                .then(owner("reset").then(playerArgument().executes(PlayerCommands::reset)))
                .then(admin("star").then(playerArgument().executes(WorldCommands::star)))
                .then(admin("constellate").executes(WorldCommands::constellate))
                .then(owner("extrasign").then(playerArgument()
                        .then(Commands.argument("granted", BoolArgumentType.bool()).executes(PlayerCommands::extraSign))))
                .then(owner("community").then(Commands.literal("set")
                        .then(Commands.argument("xp", IntegerArgumentType.integer(0)).executes(WorldCommands::community))))
                .then(admin("eclipse")
                        .then(Commands.literal("start").executes(context -> WorldCommands.skyEvent(context, SkyEvent.ECLIPSE, true)))
                        .then(Commands.literal("stop").executes(context -> WorldCommands.skyEvent(context, SkyEvent.ECLIPSE, false))))
                .then(admin("shower")
                        .then(Commands.literal("start").executes(context -> WorldCommands.skyEvent(context, SkyEvent.STAR_SHOWER, true)))
                        .then(Commands.literal("stop").executes(context -> WorldCommands.skyEvent(context, SkyEvent.STAR_SHOWER, false))))
                .then(admin("validate").executes(TreeCommands::validate))
                .then(admin("integrations").executes(TreeCommands::integrations))
                .then(owner("regenerate").executes(TreeCommands::regenerateWarning)
                        .then(Commands.literal("confirm").executes(TreeCommands::regenerate))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(ADMIN_PERMISSION));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> owner(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(OWNER_PERMISSION));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> playerArgument() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> pointsArgument(String name, int min, int max,
                                                                                    boolean add) {
        return Commands.argument(name, IntegerArgumentType.integer(min, max))
                .executes(context -> PlayerCommands.points(context, name, add, false))
                .then(Commands.argument("override_limit", BoolArgumentType.bool())
                        .executes(context -> PlayerCommands.points(context, name, add, BoolArgumentType.getBool(context, "override_limit"))));
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), new Payloads.OpenScreen(false));
        return 1;
    }
}
