package dev.lesteoff.celestialskilltree.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.DiscoveryService;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class SkillCommands {
    static final String PLAYER = "player";
    static final String TARGETS = "targets";
    static final String NODE = "node_id";
    private static final String SHORT_ALIAS = "celestial";
    private static final int ADMIN_PERMISSION = 2;
    private static final int OWNER_PERMISSION = 3;
    private static final int MAX_GRANT = 10_000;
    private static final int MAX_AVAILABLE = 100_000;
    private static final int MAX_AUDIT_LINES = 100;
    private static final SuggestionProvider<CommandSourceStack> NODES = (context, builder) -> SharedSuggestionProvider.suggest(
            SkillTreeManager.graph().map(graph -> graph.ids().stream().toList()).orElse(List.of()), builder);
    private static final SuggestionProvider<CommandSourceStack> DISCOVERY_KEYS = (context, builder) -> SharedSuggestionProvider.suggest(
            SkillTreeManager.graph().map(graph -> DiscoveryService.keys(graph).stream().sorted().toList()).orElse(List.of()), builder);
    private static final SuggestionProvider<CommandSourceStack> SIGNS = (context, builder) -> SharedSuggestionProvider.suggestResource(
            SkillTreeManager.sortedSignIds().stream().map(ResourceLocation::parse), builder);

    private SkillCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralCommandNode<CommandSourceStack> root = event.getDispatcher().register(Commands.literal(CelestialSkillTree.MODID)
                .then(Commands.literal("open").executes(SkillCommands::open))
                .then(admin("inspect").then(player().executes(PlayerCommands::inspect)))
                .then(admin("audit").then(player().executes(context -> PlayerCommands.audit(context, PlayerCommands.DEFAULT_AUDIT_LINES))
                        .then(Commands.argument("lines", IntegerArgumentType.integer(1, MAX_AUDIT_LINES))
                                .executes(context -> PlayerCommands.audit(context, IntegerArgumentType.getInteger(context, "lines"))))))
                .then(owner("points")
                        .then(Commands.literal("add").then(targets()
                                .then(pointsArgument("amount", -MAX_GRANT, MAX_GRANT, true))))
                        .then(Commands.literal("set").then(targets()
                                .then(pointsArgument("available_amount", 0, MAX_AVAILABLE, false)))))
                .then(owner("unlock").then(player().then(node()
                        .executes(context -> PlayerCommands.unlock(context, false))
                        .then(Commands.literal("grant_cost").executes(context -> PlayerCommands.unlock(context, true))))))
                .then(owner("refund").then(player().then(node().executes(PlayerCommands::refund))))
                .then(owner("toggle").then(player().then(node().executes(PlayerCommands::toggle))))
                .then(owner("reset").then(targets().executes(PlayerCommands::reset)))
                .then(owner("cooldowns").then(Commands.literal("clear").then(targets().executes(CharacterCommands::clearCooldowns))))
                .then(owner("discoveries")
                        .then(Commands.literal("reveal").then(targets().then(Commands.argument("key", ResourceLocationArgument.id())
                                .suggests(DISCOVERY_KEYS).executes(CharacterCommands::reveal))))
                        .then(Commands.literal("clear").then(targets().executes(CharacterCommands::forget))))
                .then(owner("sign")
                        .then(Commands.literal("set").then(targets().then(Commands.argument("sign", ResourceLocationArgument.id())
                                .suggests(SIGNS).executes(CharacterCommands::setSign))))
                        .then(Commands.literal("reroll").then(targets().executes(CharacterCommands::rerollSign))))
                .then(owner("extrasign").then(targets()
                        .then(Commands.argument("granted", BoolArgumentType.bool()).executes(CharacterCommands::extraSign))))
                .then(admin("star").then(player().executes(WorldCommands::star)))
                .then(admin("constellate").executes(WorldCommands::constellate))
                .then(owner("community")
                        .then(Commands.literal("set").then(Commands.argument("xp", LongArgumentType.longArg(0))
                                .executes(context -> WorldCommands.community(context, false))))
                        .then(Commands.literal("add").then(Commands.argument("xp", LongArgumentType.longArg(0))
                                .executes(context -> WorldCommands.community(context, true)))))
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
        event.getDispatcher().register(Commands.literal(SHORT_ALIAS).redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(ADMIN_PERMISSION));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> owner(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(OWNER_PERMISSION));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> player() {
        return Commands.argument(PLAYER, EntityArgument.player());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targets() {
        return Commands.argument(TARGETS, EntityArgument.players());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> node() {
        return Commands.argument(NODE, ResourceLocationArgument.id()).suggests(NODES);
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
