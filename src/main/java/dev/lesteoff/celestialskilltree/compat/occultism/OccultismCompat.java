package dev.lesteoff.celestialskilltree.compat.occultism;

import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.BuildSwapService;
import dev.lesteoff.celestialskilltree.progression.BuildSwapService.Build;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import dev.lesteoff.celestialskilltree.server.SkillService;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class OccultismCompat {
    public static final String MOD_ID = "occultism";
    private static final String FAMILIAR = "com.klikli_dev.occultism.common.entity.familiar.IFamiliar";
    private static final String BUILD_KEY = "celestialskilltree_build";
    private static final String ROOT_KEY = "root";
    private static final String NODES_KEY = "nodes";
    private static final String DISABLED_KEY = "disabled";
    private static final String SWAPPED_AT_KEY = "celestialskilltree_build_swapped_at";

    private OccultismCompat() {
    }

    public static void register() {
        Reflection.requireTypes(FAMILIAR);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class,
                event -> OptionalEvents.guard(MOD_ID, () -> onInteract(event)));
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        boolean asksForSwap = event.getEntity() instanceof ServerPlayer player && player.isShiftKeyDown()
                && event.getItemStack().is(ModItems.ORB_OF_RECONFIGURATION.get()) && isOwnFamiliar(event.getTarget(), player);
        if (asksForSwap) {
            swap((ServerPlayer) event.getEntity(), event.getTarget());
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean isOwnFamiliar(Entity target, ServerPlayer player) {
        return Reflection.isInstance(FAMILIAR, target) && Reflection.call(target, "getFamiliarOwner") == player;
    }

    private static void swap(ServerPlayer player, Entity familiar) {
        long now = player.serverLevel().getGameTime();
        long readyAt = familiar.getPersistentData().getLong(SWAPPED_AT_KEY)
                + (long) ServerConfig.FAMILIAR_SWAP_COOLDOWN_SECONDS.get() * SharedConstants.TICKS_PER_SECOND;
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        boolean resting = familiar.getPersistentData().contains(SWAPPED_AT_KEY) && now < readyAt;
        if (graph.isEmpty() || resting) {
            player.sendSystemMessage(Component.translatable("message.celestialskilltree.familiar_swap.cooldown",
                    Math.max(1, (readyAt - now) / SharedConstants.TICKS_PER_SECOND)));
            return;
        }
        BuildSwapService.Result result = BuildSwapService.swap(graph.get(), ModAttachments.state(player), stored(familiar));
        if (!result.ok()) {
            AllocationService.Rejection why = result.rejection().orElseThrow();
            player.sendSystemMessage(Component.translatable("message.celestialskilltree.familiar_swap.rejected",
                    Component.translatable(why.reason().translationKey(), why.nodeId())));
            return;
        }
        store(familiar, result.previous(), now);
        announce(player, result.previous());
    }

    private static void announce(ServerPlayer player, Build given) {
        EffectEngine.refresh(player);
        Build taken = Build.of(ModAttachments.state(player));
        SkillEvents.post(new SkillEvents.BuildReset(player, given.learned(), 0));
        SkillEvents.post(new SkillEvents.NodesLearned(player, taken.learned()));
        SkillService.sendState(player);
        player.sendSystemMessage(Component.translatable("message.celestialskilltree.familiar_swap.done", given.size(), taken.size()));
    }

    private static Build stored(Entity familiar) {
        CompoundTag saved = familiar.getPersistentData().getCompound(BUILD_KEY);
        Optional<String> root = Optional.of(saved.getString(ROOT_KEY)).filter(id -> !id.isEmpty());
        return new Build(root, strings(saved, NODES_KEY), Set.copyOf(strings(saved, DISABLED_KEY)));
    }

    private static List<String> strings(CompoundTag saved, String key) {
        return saved.getList(key, Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
    }

    private static ListTag stringList(Collection<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    private static void store(Entity familiar, Build build, long now) {
        CompoundTag saved = new CompoundTag();
        saved.putString(ROOT_KEY, build.startingRoot().orElse(""));
        saved.put(NODES_KEY, stringList(build.nodes()));
        saved.put(DISABLED_KEY, stringList(build.disabled()));
        familiar.getPersistentData().put(BUILD_KEY, saved);
        familiar.getPersistentData().putLong(SWAPPED_AT_KEY, now);
    }
}
