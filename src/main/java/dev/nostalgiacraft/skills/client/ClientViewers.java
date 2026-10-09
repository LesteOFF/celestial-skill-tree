package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class ClientViewers {
    private static final Map<UUID, String> SECTOR_BY_VIEWER = new ConcurrentHashMap<>();

    private ClientViewers() {
    }

    public static void set(UUID player, boolean open, String sectorKey) {
        if (open) {
            SECTOR_BY_VIEWER.put(player, sectorKey);
        } else {
            SECTOR_BY_VIEWER.remove(player);
        }
    }

    public static void clear() {
        SECTOR_BY_VIEWER.clear();
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        String sectorKey = SECTOR_BY_VIEWER.get(event.getEntity().getUUID());
        if (sectorKey != null) {
            Component stars = (sectorKey.isEmpty() ? Component.translatable("gui.nostalgiaskills.viewing_stars")
                    : Component.translatable("gui.nostalgiaskills.viewing_sector", Component.translatable(sectorKey)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            event.setContent(Component.empty().append(event.getContent()).append(" ").append(stars));
        }
    }
}
