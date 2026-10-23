package dev.lesteoff.celestialskilltree.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.lesteoff.celestialskilltree.data.WorldSkillData;
import dev.lesteoff.celestialskilltree.effects.EliteMobs;
import dev.lesteoff.celestialskilltree.entity.FallingStar;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.registry.ModTags;
import dev.lesteoff.celestialskilltree.server.CommunityTree;
import dev.lesteoff.celestialskilltree.server.SkyEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
        context.getSource().sendSuccess(() -> Component.translatable(fell ? "command.celestialskilltree.star"
                : "command.celestialskilltree.star.unloaded", player.getDisplayName()), true);
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
            source.sendSuccess(() -> Component.translatable("command.celestialskilltree.constellate", monster.getDisplayName()), true);
            return 1;
        }
        if (summoned != null) {
            summoned.discard();
        }
        source.sendFailure(Component.translatable("command.celestialskilltree.constellate.failed"));
        return 0;
    }

    static int skyEvent(CommandContext<CommandSourceStack> context, SkyEvent event, boolean start) {
        SkyEvents.force(context.getSource().getServer(), start ? event : SkyEvent.NONE);
        String messageKey = "command.celestialskilltree." + event.id() + (start ? ".start" : ".stop");
        context.getSource().sendSuccess(() -> Component.translatable(messageKey), true);
        return 1;
    }

    static int community(CommandContext<CommandSourceStack> context, boolean add) {
        MinecraftServer server = context.getSource().getServer();
        long xp = LongArgumentType.getLong(context, "xp");
        long total = add ? WorldSkillData.of(server).communityXp() + xp : xp;
        CommunityTree.setProgress(server, total);
        context.getSource().sendSuccess(() -> Component.translatable("command.celestialskilltree.community", total), true);
        return 1;
    }
}
