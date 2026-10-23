package dev.nostalgiacraft.skills.compat.apotheosis;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.BorrowedNodes;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ApotheosisCompat {
    public static final String MOD_ID = "apotheosis";
    private static final String AFFIX_HELPER = "dev.shadowsoffire.apotheosis.affix.AffixHelper";
    private static final String KEYSTONE_FOLDER = "keystone/";
    private static final String BOSS_KEY = "apoth.boss";

    private ApotheosisCompat() {
    }

    public static void register() {
        Reflection.requireTypes(AFFIX_HELPER);
        EliteMobs.registerRival(monster -> monster.getPersistentData().contains(BOSS_KEY));
        BorrowedNodes.registerLender(player -> OptionalEvents.guarded(MOD_ID, List.of(), () -> keystonesOnEquipment(player)));
    }

    private static Collection<String> keystonesOnEquipment(Player player) {
        List<String> keystones = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                keystones.addAll(keystonesOf(stack));
            }
        }
        return keystones;
    }

    private static List<String> keystonesOf(ItemStack stack) {
        Map<?, ?> affixes = (Map<?, ?>) Reflection.callStatic(AFFIX_HELPER, "getAffixes", stack);
        return affixes.keySet().stream().map(holder -> (ResourceLocation) Reflection.call(holder, "getId"))
                .filter(affix -> affix.getPath().startsWith(KEYSTONE_FOLDER))
                .map(affix -> affix.getNamespace() + ":" + affix.getPath().substring(KEYSTONE_FOLDER.length())).toList();
    }
}
