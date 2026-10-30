package dev.lesteoff.celestialskilltree.compat.astralsorcery;

import dev.lesteoff.celestialskilltree.compat.OptionalEvents;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.Condition;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import dev.lesteoff.celestialskilltree.server.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiPredicate;

public final class AstralSorceryCompat {
    public static final String MOD_ID = "astralsorcery";
    private static final String RESEARCH = "hellfirepvp.astralsorcery.common.research.ResearchManager";
    private static final String SKY = "hellfirepvp.astralsorcery.common.constellation.level.LevelSkyHandler";
    private static final String GENERAL_CONFIG = "hellfirepvp.astralsorcery.common.config.server.GeneralConfig";
    private static final String DISCOVERY = "hellfirepvp.astralsorcery.common.event.ResearchEvent$DiscoverConstellation";
    private static final String ATTUNED = "hellfirepvp.astralsorcery.common.event.ResearchEvent$AttunedConstellationSet";
    private static final String MEMORIZE = "hellfirepvp.astralsorcery.common.event.ResearchEvent$MemorizeConstellation";
    private static final String TIER_SET = "hellfirepvp.astralsorcery.common.event.ResearchEvent$ResearchTierSet";

    private AstralSorceryCompat() {
    }

    public static void register() {
        Reflection.requireTypes(RESEARCH, SKY, GENERAL_CONFIG, DISCOVERY, ATTUNED, MEMORIZE, TIER_SET);
        EffectEngine.registerCondition("astral_attuned", constellationCondition(AstralSorceryCompat::isAttuned));
        EffectEngine.registerCondition("astral_discovered", constellationCondition(AstralSorceryCompat::hasDiscovered));
        EffectEngine.registerCondition("astral_tier", AstralSorceryCompat::hasReachedTier);
        OptionalEvents.listen(MOD_ID, DISCOVERY, (PlayerEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EffectEngine.updateConditionals(player);
                SkillService.sendState(player);
            }
        });
        OptionalEvents.listen(MOD_ID, ATTUNED, (PlayerEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EffectEngine.updateConditionals(player);
            }
        });
        OptionalEvents.listen(MOD_ID, TIER_SET, (PlayerEvent event) -> {
            Object tier = Reflection.call(event, "getTier");
            int tiersGained = tierIndex(tier) - Math.max(0, tierIndex(Reflection.call(event, "getPrevious")));
            if (event.getEntity() instanceof ServerPlayer player && tiersGained > 0) {
                EffectEngine.updateConditionals(player);
                reward(player, tiersGained * ServerConfig.ASTRAL_TIER_FRAGMENTS.get(), "astral_tier_reward", Reflection.call(tier, "getName"));
            }
        });
        OptionalEvents.listen(MOD_ID, MEMORIZE, (PlayerEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                reward(player, ServerConfig.ASTRAL_CONSTELLATION_FRAGMENTS.get(), "astral_constellation_reward",
                        Reflection.call(Reflection.call(event, "getConstellation"), "getName"));
            }
        });
    }

    private static void reward(ServerPlayer player, int fragments, String messageKey, Object source) {
        if (fragments <= 0) {
            return;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.STAR_FRAGMENT.get(), fragments));
        player.displayClientMessage(Component.translatable("message.celestialskilltree." + messageKey, source, fragments)
                .withStyle(ChatFormatting.AQUA), true);
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isSolarEclipseDay(ServerLevel overworld) {
        return isEclipseDay(overworld, "getSolarEclipse");
    }

    public static boolean isLunarEclipseDay(ServerLevel overworld) {
        return isEclipseDay(overworld, "getLunarEclipse");
    }

    private static boolean isEclipseDay(ServerLevel overworld, String eclipseGetter) {
        return OptionalEvents.guarded(MOD_ID, false, () -> overworld.getDayTime() > dayLength() && ((Optional<?>) Reflection.callStatic(SKY, "getContext", overworld, LogicalSide.SERVER))
                .map(context -> Reflection.call(Reflection.call(context, "getCelestialEventHandler"), eclipseGetter))
                .map(eclipse -> (Boolean) Reflection.call(eclipse, "isActiveDay"))
                .orElse(false));
    }

    private static int dayLength() {
        return (Integer) ((ModConfigSpec.IntValue) Reflection.field(Reflection.staticField(GENERAL_CONFIG, "CONFIG"), "dayLength")).get();
    }

    private static boolean hasReachedTier(Player player, Condition condition) {
        return player instanceof ServerPlayer serverPlayer && OptionalEvents.guarded(MOD_ID, false,
                () -> tierIndex(Reflection.call(progress(serverPlayer), "getTierReached")) >= Condition.ASTRAL_TIERS.indexOf(condition.string("tier")));
    }

    private static int tierIndex(Object tier) {
        return tier instanceof Enum<?> known ? Condition.ASTRAL_TIERS.indexOf(known.name().toLowerCase(Locale.ROOT)) : -1;
    }

    private static BiPredicate<Player, Condition> constellationCondition(BiPredicate<Object, String> test) {
        return (player, condition) -> player instanceof ServerPlayer serverPlayer
                && OptionalEvents.guarded(MOD_ID, false, () -> test.test(progress(serverPlayer), condition.string("constellation")));
    }

    private static Object progress(ServerPlayer player) {
        return Reflection.callStatic(RESEARCH, "getProgress", player, LogicalSide.SERVER);
    }

    private static boolean isAttuned(Object progress, String constellation) {
        Optional<String> attuned = ((Optional<?>) Reflection.call(progress, "getAttunedConstellation")).flatMap(AstralSorceryCompat::id);
        return constellation == null ? attuned.isPresent() : attuned.filter(constellation::equals).isPresent();
    }

    private static boolean hasDiscovered(Object progress, String constellation) {
        return ((Collection<?>) Reflection.call(progress, "getKnownConstellations")).stream()
                .flatMap(known -> id(known).stream()).anyMatch(constellation::equals);
    }

    private static Optional<String> id(Object constellation) {
        return ((Optional<?>) Reflection.call(constellation, "getHolder")).map(holder -> ((Holder<?>) holder).getRegisteredName());
    }
}
