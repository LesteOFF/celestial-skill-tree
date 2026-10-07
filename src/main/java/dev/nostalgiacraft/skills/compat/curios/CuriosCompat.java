package dev.nostalgiacraft.skills.compat.curios;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.Condition;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.registry.ModTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class CuriosCompat {
    public static final String MOD_ID = "curios";
    private static final String API = "top.theillusivec4.curios.api.CuriosApi";
    private static final Map<UUID, Map<String, ResourceLocation>> GRANTED_SLOTS = new HashMap<>();

    private CuriosCompat() {
    }

    public static void register() {
        EffectEngine.registerCondition("has_curio", (player, condition) ->
                OptionalEvents.guarded(MOD_ID, false, () -> wears(player, condition)));
        EffectEngine.registerAttributeTarget(MOD_ID, new SlotTarget());
    }

    private static Optional<?> inventory(Player player) {
        return (Optional<?>) Reflection.callStatic(API, "getCuriosInventory", player);
    }

    private static boolean wears(Player player, Condition condition) {
        Predicate<ItemStack> tagged = stack -> stack.is(ModTags.itemTag(condition.string("tag")));
        return inventory(player).map(curios -> (boolean) Reflection.call(curios, "isEquipped", tagged)).orElse(false);
    }

    private static final class SlotTarget implements EffectEngine.AttributeTarget {
        @Override
        public void apply(ServerPlayer player, String attribute, AttributeModifier modifier) {
            String slot = Ids.path(attribute);
            OptionalEvents.guard(MOD_ID, () -> inventory(player).ifPresent(curios -> {
                Reflection.call(curios, "addTransientSlotModifier", slot, modifier.id(), modifier.amount(), modifier.operation());
                GRANTED_SLOTS.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(slot, modifier.id());
            }));
        }

        @Override
        public void clear(ServerPlayer player) {
            Map<String, ResourceLocation> granted = GRANTED_SLOTS.remove(player.getUUID());
            if (granted == null) {
                return;
            }
            OptionalEvents.guard(MOD_ID, () -> inventory(player).ifPresent(curios ->
                    granted.forEach((slot, modifierId) -> Reflection.call(curios, "removeSlotModifier", slot, modifierId))));
        }
    }
}
