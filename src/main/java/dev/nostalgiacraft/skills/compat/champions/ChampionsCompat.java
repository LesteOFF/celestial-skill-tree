package dev.nostalgiacraft.skills.compat.champions;

import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.fml.ModLoadingContext;

import java.util.Optional;

public final class ChampionsCompat {
    public static final String MOD_ID = "champions";
    private static final String API = "top.theillusivec4.champions.api.ChampionsApi";
    private static final String LEGACY_ATTACHMENT = "top.theillusivec4.champions.common.capability.ChampionAttachment";

    private ChampionsCompat() {
    }

    public static void register() {
        boolean hasApi = Reflection.exists(API);
        if (hasApi) {
            AstralAffix.register(ModLoadingContext.get().getActiveContainer().getEventBus());
        }
        EliteMobs.registerRival(monster -> OptionalEvents.guarded(MOD_ID, false,
                () -> hasApi ? isChampion(monster) : isLegacyChampion(monster)));
    }

    private static boolean isChampion(Monster monster) {
        return (boolean) Reflection.call(Reflection.callStatic(API, "get"), "isChampion", monster);
    }

    private static boolean isLegacyChampion(Monster monster) {
        Optional<?> champion = (Optional<?>) Reflection.callStatic(LEGACY_ATTACHMENT, "getAttachment", monster);
        return champion.map(found -> (Optional<?>) Reflection.call(Reflection.call(found, "getServer"), "getRank"))
                .flatMap(rank -> rank).map(rank -> (int) Reflection.call(rank, "getTier") > 0).orElse(false);
    }
}
