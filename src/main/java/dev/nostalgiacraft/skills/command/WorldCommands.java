package dev.nostalgiacraft.skills.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import dev.nostalgiacraft.skills.entity.FallingStar;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import dev.nostalgiacraft.skills.registry.ModTags;
import dev.nostalgiacraft.skills.server.CommunityTree;
import dev.nostalgiacraft.skills.server.SkyEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;

import java.util.Optional;

final class WorldCommands {
    private WorldCommands() {
    }

    static int star(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        boolean fell = FallingStar.spawnNear(player).isPresent();
        context.getSource().sendSuccess(() -> Component.translatable(fell ? "command.nostalgiaskills.star"
                : "command.nostalgiaskills.star.unloaded", player.getDisplayName()), true);
        return fell ? 1 : 0;
    }

    static int constellate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Holder<EntityType<?>>> type = BuiltInRegistries.ENTITY_TYPE.getRandomElementOf(ModTags.RITUAL_CONSTELLATED,
                source.getLevel().getRandom());
        Entity summoned = type.map(Holder::value)
                .map(chosen -> chosen.spawn(source.getLevel(), BlockPos.containing(source.getPosition()), MobSpawnType.COMMAND))
                .orElse(null);
        if (summoned instanceof Monster monster && EliteMobs.constellate(monster)) {
            source.sendSuccess(() -> Component.translatable("command.nostalgiaskills.constellate", monster.getDisplayName()), true);
            return 1;
        }
        if (summoned != null) {
            summoned.discard();
        }
        source.sendFailure(Component.translatable("command.nostalgiaskills.constellate.failed"));
        return 0;
    }

    static int skyEvent(CommandContext<CommandSourceStack> context, SkyEvent event, boolean start) {
        SkyEvents.force(context.getSource().getServer(), start ? event : SkyEvent.NONE);
        String messageKey = "command.nostalgiaskills." + event.id() + (start ? ".start" : ".stop");
        context.getSource().sendSuccess(() -> Component.translatable(messageKey), true);
        return 1;
    }

    static int community(CommandContext<CommandSourceStack> context) {
        int xp = IntegerArgumentType.getInteger(context, "xp");
        CommunityTree.setProgress(context.getSource().getServer(), xp);
        context.getSource().sendSuccess(() -> Component.translatable("command.nostalgiaskills.community", xp), true);
        return 1;
    }
}
