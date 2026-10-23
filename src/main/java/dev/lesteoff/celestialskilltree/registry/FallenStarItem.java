package dev.lesteoff.celestialskilltree.registry;

import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.progression.Allocation;
import dev.lesteoff.celestialskilltree.progression.FallenStarService;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.server.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class FallenStarItem extends RedeemableItem {
    private static final String OWNER = "owner";
    private static final String OWNER_NAME = "owner_name";
    private static final String NODE = "node";
    private static final String COST = "cost";

    public record Contents(UUID owner, String ownerName, String nodeId, int paidCost) {
    }

    public FallenStarItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ServerPlayer owner, Allocation allocation) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(OWNER, owner.getUUID());
        tag.putString(OWNER_NAME, owner.getScoreboardName());
        tag.putString(NODE, allocation.nodeId());
        tag.putInt(COST, allocation.paidCost());
        ItemStack stack = new ItemStack(ModItems.FALLEN_STAR.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static Optional<Contents> contents(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(OWNER) || !data.contains(NODE)) {
            return Optional.empty();
        }
        CompoundTag tag = data.copyTag();
        return Optional.of(new Contents(tag.getUUID(OWNER), tag.getString(OWNER_NAME), tag.getString(NODE), Math.max(0, tag.getInt(COST))));
    }

    @Override
    protected boolean redeemBy(ServerPlayer player, ItemStack stack) {
        return redeem(player, stack);
    }

    public static boolean redeem(ServerPlayer player, ItemStack stack) {
        Optional<Contents> contents = contents(stack);
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        if (contents.isEmpty() || graph.isEmpty()) {
            return false;
        }
        PlayerSkillState state = ModAttachments.state(player);
        Contents star = contents.get();
        FallenStarService.Recovery recovery = star.owner().equals(player.getUUID())
                ? FallenStarService.recoverOwn(state, graph.get(), star.nodeId(), star.paidCost())
                : FallenStarService.claimForeign(state, star.paidCost(), ServerConfig.POINT_LIMIT.get());
        ChatFormatting color = recovery == FallenStarService.Recovery.REJECTED ? ChatFormatting.RED : ChatFormatting.GOLD;
        player.displayClientMessage(Component.translatable("message.celestialskilltree.fallen_star." + recovery.name().toLowerCase(Locale.ROOT),
                star.paidCost()).withStyle(color), true);
        if (recovery == FallenStarService.Recovery.REJECTED) {
            return false;
        }
        stack.shrink(1);
        announce(player, recovery, star);
        EffectEngine.refresh(player);
        SkillService.sendState(player);
        return true;
    }

    private static void announce(ServerPlayer player, FallenStarService.Recovery recovery, Contents star) {
        if (recovery == FallenStarService.Recovery.RELEARNED) {
            SkillEvents.post(new SkillEvents.NodesLearned(player, List.of(star.nodeId())));
        } else {
            SkillEvents.post(new SkillEvents.PointsGranted(player, star.paidCost(), SkillEvents.GrantSource.ITEM));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        contents(stack).ifPresent(star -> {
            String nameKey = SkillTreeManager.graph().flatMap(graph -> graph.node(star.nodeId())).map(SkillNodeDefinition::nameKey)
                    .orElse(star.nodeId());
            lines.add(Component.translatable("item.celestialskilltree.fallen_star.contents", Component.translatable(nameKey),
                    star.paidCost(), star.ownerName()).withStyle(ChatFormatting.GOLD));
        });
        lines.add(Component.translatable("item.celestialskilltree.fallen_star.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
