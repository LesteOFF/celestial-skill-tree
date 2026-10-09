package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.progression.DiscoveryService;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.WornItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class WornDiscoveries {
    public static final String KEY_PREFIX = NostalgiaSkills.MODID + ":worn/";
    private static final int CHECK_PERIOD_TICKS = 40;

    private WornDiscoveries() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_PERIOD_TICKS == 0) {
            SkillTreeManager.graph().ifPresent(graph -> revealWorn(player, DiscoveryService.keys(graph)));
        }
    }

    private static void revealWorn(ServerPlayer player, Set<String> keys) {
        Set<String> known = ModAttachments.state(player).discoveries();
        List<String> pending = keys.stream().filter(key -> key.startsWith(KEY_PREFIX) && !known.contains(key)).toList();
        if (pending.isEmpty()) {
            return;
        }
        Set<String> worn = wornKeys(player);
        pending.stream().filter(worn::contains).forEach(key -> Discoveries.reveal(player, key));
    }

    private static Set<String> wornKeys(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(WornItems.inExtraSlots(player));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stacks.add(player.getItemBySlot(slot));
        }
        return stacks.stream().filter(stack -> !stack.isEmpty()).map(WornDiscoveries::keyOf).collect(Collectors.toSet());
    }

    private static String keyOf(ItemStack stack) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return KEY_PREFIX + item.getNamespace() + "/" + item.getPath();
    }
}
