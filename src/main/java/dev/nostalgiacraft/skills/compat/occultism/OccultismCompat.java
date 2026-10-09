package dev.nostalgiacraft.skills.compat.occultism;

import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.BuildSwapService;
import dev.nostalgiacraft.skills.progression.BuildSwapService.Build;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.server.SkillService;
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

import java.util.List;
import java.util.Optional;

public final class OccultismCompat {
    public static final String MOD_ID = "occultism";
    private static final String FAMILIAR = "com.klikli_dev.occultism.common.entity.familiar.IFamiliar";
    private static final String BUILD_KEY = "nostalgiaskills_build";
    private static final String ROOT_KEY = "root";
    private static final String NODES_KEY = "nodes";
    private static final String SWAPPED_AT_KEY = "nostalgiaskills_build_swapped_at";
    private static final int TICKS_PER_SECOND = 20;

    private OccultismCompat() {
    }

    public static void register() {
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
                + (long) ServerConfig.FAMILIAR_SWAP_COOLDOWN_SECONDS.get() * TICKS_PER_SECOND;
        Optional<SkillGraphSnapshot> graph = SkillTreeManager.graph();
        boolean resting = familiar.getPersistentData().contains(SWAPPED_AT_KEY) && now < readyAt;
        if (graph.isEmpty() || resting) {
            player.sendSystemMessage(Component.translatable("message.nostalgiaskills.familiar_swap.cooldown",
                    Math.max(1, (readyAt - now) / TICKS_PER_SECOND)));
            return;
        }
        BuildSwapService.Result result = BuildSwapService.swap(graph.get(), ModAttachments.state(player), stored(familiar));
        if (!result.ok()) {
            AllocationService.Rejection why = result.rejection().orElseThrow();
            player.sendSystemMessage(Component.translatable("message.nostalgiaskills.familiar_swap.rejected",
                    Component.translatable(why.reason().translationKey(), why.nodeId())));
            return;
        }
        store(familiar, result.previous(), now);
        announce(player, result.previous());
    }

    private static void announce(ServerPlayer player, Build given) {
        EffectEngine.refresh(player);
        Build taken = Build.of(ModAttachments.state(player));
        SkillEvents.post(new SkillEvents.BuildReset(player, given.nodes(), 0));
        SkillEvents.post(new SkillEvents.NodesLearned(player, taken.nodes()));
        SkillService.sendState(player);
        player.sendSystemMessage(Component.translatable("message.nostalgiaskills.familiar_swap.done", given.size(), taken.size()));
    }

    private static Build stored(Entity familiar) {
        CompoundTag saved = familiar.getPersistentData().getCompound(BUILD_KEY);
        Optional<String> root = Optional.of(saved.getString(ROOT_KEY)).filter(id -> !id.isEmpty());
        List<String> nodes = saved.getList(NODES_KEY, Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
        return new Build(root, nodes);
    }

    private static void store(Entity familiar, Build build, long now) {
        CompoundTag saved = new CompoundTag();
        saved.putString(ROOT_KEY, build.startingRoot().orElse(""));
        ListTag nodes = new ListTag();
        build.nodes().forEach(id -> nodes.add(StringTag.valueOf(id)));
        saved.put(NODES_KEY, nodes);
        familiar.getPersistentData().put(BUILD_KEY, saved);
        familiar.getPersistentData().putLong(SWAPPED_AT_KEY, now);
    }
}
