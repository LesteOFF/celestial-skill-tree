package dev.nostalgiacraft.skills.compat.pandora;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.compat.curios.CuriosCompat;
import dev.nostalgiacraft.skills.registry.WornItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PandoraCompat {
    public static final String MOD_ID = "pandora";
    private static final String HOLDER = "dev.xkmc.pandora.content.base.IPandoraHolder";

    private PandoraCompat() {
    }

    public static void register() {
        WornItems.registerSource(player -> OptionalEvents.guarded(MOD_ID, List.of(), () -> charmsInside(player)));
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> charmsInside(Player player) {
        return CuriosCompat.equipped(player).stream()
                .flatMap(holder -> ((List<ItemStack>) Reflection.callStatic(HOLDER, "getItems", holder)).stream()).toList();
    }
}
