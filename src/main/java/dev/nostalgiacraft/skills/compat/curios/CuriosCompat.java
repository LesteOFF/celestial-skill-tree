package dev.nostalgiacraft.skills.compat.curios;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.BorrowedNodes;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.Condition;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.registry.ModTags;
import dev.nostalgiacraft.skills.registry.WornItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class CuriosCompat {
    public static final String MOD_ID = "curios";
    private static final String API = "top.theillusivec4.curios.api.CuriosApi";
    private static final String CHANGE_EVENT = "top.theillusivec4.curios.api.event.CurioChangeEvent";
    private static final Map<UUID, Map<String, Set<ResourceLocation>>> GRANTED_SLOTS = new HashMap<>();

    private CuriosCompat() {
    }

    public static void register() {
        EffectEngine.registerCondition("has_curio", (player, condition) ->
                OptionalEvents.guarded(MOD_ID, false, () -> wears(player, condition)));
        EffectEngine.registerAttributeTarget(MOD_ID, new SlotTarget());
        WornItems.registerSource(player -> OptionalEvents.guarded(MOD_ID, List.of(), () -> equipped(player)));
        OptionalEvents.listen(MOD_ID, CHANGE_EVENT, CuriosCompat::onCurioChange);
    }

    public static List<ItemStack> equipped(Player player) {
        Predicate<ItemStack> any = stack -> true;
        return inventory(player).map(curios -> (List<?>) Reflection.call(curios, "findCurios", any)).orElse(List.of()).stream()
                .map(found -> (ItemStack) Reflection.call(found, "stack")).toList();
    }

    private static void onCurioChange(LivingEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BorrowedNodes.refreshIfChanged(player);
        }
    }

    private static Optional<?> inventory(Player player) {
        return (Optional<?>) Reflection.callStatic(API, "getCuriosInventory", player);
    }

    private static boolean wears(Player player, Condition condition) {
        return WornItems.inExtraSlots(player).stream().anyMatch(stack -> stack.is(ModTags.itemTag(condition.string("tag"))));
    }

    private static final class SlotTarget implements EffectEngine.AttributeTarget {
        @Override
        public void apply(ServerPlayer player, String attribute, AttributeModifier modifier) {
            String slot = Ids.path(attribute);
            OptionalEvents.guard(MOD_ID, () -> inventory(player).ifPresent(curios -> {
                Reflection.call(curios, "addTransientSlotModifier", slot, modifier.id(), modifier.amount(), modifier.operation());
                GRANTED_SLOTS.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                        .computeIfAbsent(slot, key -> new HashSet<>()).add(modifier.id());
            }));
        }

        @Override
        public void clear(ServerPlayer player) {
            Map<String, Set<ResourceLocation>> granted = GRANTED_SLOTS.remove(player.getUUID());
            if (granted == null) {
                return;
            }
            OptionalEvents.guard(MOD_ID, () -> inventory(player).ifPresent(curios ->
                    granted.forEach((slot, modifierIds) -> modifierIds.forEach(modifierId ->
                            Reflection.call(curios, "removeSlotModifier", slot, modifierId)))));
        }

        @Override
        public void forget(UUID player) {
            GRANTED_SLOTS.remove(player);
        }
    }
}
