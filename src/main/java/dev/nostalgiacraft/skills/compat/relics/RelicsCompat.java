package dev.nostalgiacraft.skills.compat.relics;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.registry.LivingFragmentItem;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RelicsCompat {
    public static final String MOD_ID = "relics";
    private static final String EXPERIENCE_EVENT = "it.hurts.sskirillss.relics.api.events.relic.leveling.RelicExperienceChangeEvent";
    private static final Map<UUID, Double> FRACTIONS = new HashMap<>();

    private RelicsCompat() {
    }

    public static void register() {
        OptionalEvents.listen(MOD_ID, EXPERIENCE_EVENT, RelicsCompat::onRelicExperience);
    }

    private static void onRelicExperience(Event event) {
        double shared = (double) Reflection.call(event, "getDelta") * ServerConfig.RELIC_XP_SHARE.get();
        if (shared > 0 && Reflection.call(event, "getBearer") instanceof ServerPlayer player) {
            double owed = FRACTIONS.getOrDefault(player.getUUID(), 0.0) + shared;
            int whole = (int) owed;
            FRACTIONS.put(player.getUUID(), owed - whole);
            if (whole > 0) {
                player.giveExperiencePoints(whole);
                LivingFragmentItem.feed(player, whole);
            }
        }
    }
}
