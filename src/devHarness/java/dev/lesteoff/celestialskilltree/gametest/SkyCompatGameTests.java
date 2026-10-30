package dev.lesteoff.celestialskilltree.gametest;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import dev.lesteoff.celestialskilltree.compat.astralsorcery.AstralSorceryCompat;
import dev.lesteoff.celestialskilltree.compat.nomansland.NoMansLandCompat;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.Condition;
import dev.lesteoff.celestialskilltree.progression.SignService;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import dev.lesteoff.celestialskilltree.server.SkyEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
@PrefixGameTestTemplate(false)
public final class SkyCompatGameTests {
    private static final String ANVIL = Scenario.id("anvil");
    private static final String ARMARA = "astralsorcery:armara";
    private static final String CONSTELLATIONS = "hellfirepvp.astralsorcery.common.lib.ConstellationsAS";
    private static final String RESEARCH = "hellfirepvp.astralsorcery.common.research.ResearchHelper";
    private static final String PROGRESS = "hellfirepvp.astralsorcery.common.research.ResearchManager";
    private static final String RESEARCH_TIER = "hellfirepvp.astralsorcery.common.research.ResearchTier";
    private static final String SUN_DOG = "com.farcr.nomansland.common.worldevent.SunDog";
    private static final long DAY_TICKS = 24_000L;
    private static final long NOON = 6_000L;
    private static final int SOLAR_ECLIPSE_PERIOD_DAYS = 36;
    private static final int LATEST_FIRST_SOLAR_ECLIPSE_DAY = 54;
    private static final int SKY_TICKS = 2;
    private static final int SEARCH_TIMEOUT_TICKS = (SOLAR_ECLIPSE_PERIOD_DAYS + 2) * (SKY_TICKS + 1) + 40;
    private static final long MIDNIGHT = 18_000L;
    private static final int LUNAR_ECLIPSE_PERIOD_DAYS = 68;

    private SkyCompatGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (ModList.get().isLoaded(AstralSorceryCompat.MOD_ID) || ModList.get().isLoaded(NoMansLandCompat.MOD_ID)) {
            event.register(SkyCompatGameTests.class);
        }
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY)
    public static void discoveringThePairedConstellationTurnsOnTheSignBonus(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            if (!AstralSorceryCompat.isLoaded()) {
                return;
            }
            ServerPlayer player = scenario.blankPlayer();
            SignService.set(ModAttachments.state(player), ANVIL);
            EffectEngine.refresh(player);
            Condition discovered = new Condition("astral_discovered", Map.of("constellation", ARMARA));
            Condition attuned = new Condition("astral_attuned", Map.of("constellation", ARMARA));
            scenario.check(!EffectEngine.holds(player, discovered, null), "a new player has not discovered Armara");
            double armorBefore = player.getAttributeValue(Attributes.ARMOR);
            boolean newlyDiscovered = (Boolean) Reflection.callStatic(RESEARCH, "discoverConstellation", player, armara());
            scenario.check(newlyDiscovered, "Astral Sorcery accepts the discovery of Armara");
            scenario.check(EffectEngine.holds(player, discovered, null), "Armara counts as discovered");
            scenario.check(!EffectEngine.holds(player, attuned, null), "discovering is not attuning");
            scenario.near(armorBefore + EffectEngine.scaled(1.0), player.getAttributeValue(Attributes.ARMOR),
                    "armor once the Anvil's constellation is discovered");
        });
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY)
    public static void reachingAResearchTierRewardsStarFragmentsAndMeetsTheTierCondition(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            if (!AstralSorceryCompat.isLoaded()) {
                return;
            }
            ServerPlayer player = scenario.blankPlayer();
            Condition luminance = new Condition("astral_tier", Map.of("tier", "luminance"));
            scenario.check(!EffectEngine.holds(player, luminance, null), "a new player has not reached Luminance");
            int tiersGained = Condition.ASTRAL_TIERS.indexOf("luminance") - tierIndex(player);
            int fragmentsBefore = starFragments(player);
            boolean advanced = (Boolean) Reflection.callStatic(RESEARCH, "setResearchProgress", player, Reflection.staticField(RESEARCH_TIER, "LUMINANCE"));
            scenario.check(advanced, "Astral Sorcery accepts the new research tier");
            scenario.check(EffectEngine.holds(player, luminance, null), "Luminance counts as reached");
            scenario.check(EffectEngine.holds(player, new Condition("astral_tier", Map.of("tier", "illumination")), null),
                    "an earlier tier counts as reached too");
            scenario.check(!EffectEngine.holds(player, new Condition("astral_tier", Map.of("tier", "radiance")), null),
                    "a later tier does not count");
            scenario.check(starFragments(player) - fragmentsBefore == tiersGained * ServerConfig.ASTRAL_TIER_FRAGMENTS.get(),
                    "each tier gained gives its Star Fragments");
        });
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY)
    public static void memorizingAConstellationRewardsStarFragments(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            if (!AstralSorceryCompat.isLoaded()) {
                return;
            }
            ServerPlayer player = scenario.blankPlayer();
            int fragmentsBefore = starFragments(player);
            boolean memorized = (Boolean) Reflection.callStatic(RESEARCH, "memorizeConstellation", player, armara());
            scenario.check(memorized, "Astral Sorcery accepts the memorized constellation");
            scenario.check(starFragments(player) - fragmentsBefore == ServerConfig.ASTRAL_CONSTELLATION_FRAGMENTS.get(),
                    "memorizing gives its Star Fragments");
        });
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY)
    public static void anAttunementWithoutAConstellationMatchesAnyAttunement(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            if (!AstralSorceryCompat.isLoaded()) {
                return;
            }
            ServerPlayer player = scenario.blankPlayer();
            Condition anyAttunement = new Condition("astral_attuned", Map.of());
            scenario.check(!EffectEngine.holds(player, anyAttunement, null), "a new player is not attuned");
            Reflection.callStatic(RESEARCH, "discoverConstellation", player, armara());
            boolean attuned = (Boolean) Reflection.callStatic(RESEARCH, "attuneConstellation", player, armara());
            scenario.check(attuned, "Astral Sorcery accepts the attunement to Armara");
            scenario.check(EffectEngine.holds(player, anyAttunement, null), "any attunement counts");
        });
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY, batch = "astralsorcery_lunar_eclipse")
    public static void theStarShowerFollowsTheAstralSorceryLunarEclipse(GameTestHelper helper) {
        if (!AstralSorceryCompat.isLoaded() || !ServerConfig.STAR_SHOWER_ENABLED.get() || !ServerConfig.STAR_SHOWER_FOLLOWS_ASTRAL_SORCERY.get()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        long start = level.getDayTime();
        long eclipseDay = (start / DAY_TICKS / LUNAR_ECLIPSE_PERIOD_DAYS + 1) * LUNAR_ECLIPSE_PERIOD_DAYS;
        level.setDayTime(eclipseDay * DAY_TICKS + MIDNIGHT);
        helper.runAfterDelay(SKY_TICKS, () -> {
            boolean lunarEclipse = AstralSorceryCompat.isLunarEclipseDay(level);
            SkyEvents.force(level.getServer(), SkyEvent.NONE);
            SkyEvent atMidnight = SkyEvents.current();
            restoreSky(level, start);
            helper.assertTrue(lunarEclipse, "day " + eclipseDay + " is an Astral Sorcery lunar eclipse day");
            helper.assertTrue(atMidnight == SkyEvent.STAR_SHOWER, "midnight of the lunar eclipse is a star shower, but was " + atMidnight);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY, batch = "astralsorcery_first_night")
    public static void theFirstNightOfAWorldIsNoAstralSorceryEclipse(GameTestHelper helper) {
        if (!AstralSorceryCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        long start = level.getDayTime();
        level.setDayTime(MIDNIGHT);
        helper.runAfterDelay(SKY_TICKS, () -> {
            boolean lunarEclipse = AstralSorceryCompat.isLunarEclipseDay(level);
            boolean solarEclipse = AstralSorceryCompat.isSolarEclipseDay(level);
            restoreSky(level, start);
            helper.assertFalse(lunarEclipse, "the first night is no lunar eclipse");
            helper.assertFalse(solarEclipse, "the first day is no solar eclipse");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY)
    public static void anEclipseEndsTheSunDogs(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            if (!ModList.get().isLoaded(NoMansLandCompat.MOD_ID)) {
                return;
            }
            Object sunDog = Reflection.callStatic(SUN_DOG, "getOrDefault", scenario.level());
            Reflection.call(sunDog, "forceStart");
            scenario.check(isActive(sunDog), "the sun dogs start");
            scenario.forceSky(SkyEvent.ECLIPSE);
            scenario.check(!isActive(sunDog), "the eclipse ends the sun dogs");
        });
    }

    @GameTest(templateNamespace = CelestialSkillTree.MODID, template = Scenario.EMPTY, batch = "astralsorcery_eclipse",
            timeoutTicks = SEARCH_TIMEOUT_TICKS)
    public static void theEclipseFollowsTheAstralSorcerySolarEclipse(GameTestHelper helper) {
        if (!AstralSorceryCompat.isLoaded() || !ServerConfig.ECLIPSE_ENABLED.get() || !ServerConfig.ECLIPSE_FOLLOWS_ASTRAL_SORCERY.get()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        long start = level.getDayTime();
        searchSolarEclipse(helper, start, start / DAY_TICKS + LATEST_FIRST_SOLAR_ECLIPSE_DAY, 0);
    }

    private static void searchSolarEclipse(GameTestHelper helper, long start, long firstDay, int offset) {
        ServerLevel level = helper.getLevel();
        if (offset > SOLAR_ECLIPSE_PERIOD_DAYS) {
            restoreSky(level, start);
            helper.fail("Astral Sorcery had no solar eclipse day in " + SOLAR_ECLIPSE_PERIOD_DAYS + " days");
            return;
        }
        level.setDayTime((firstDay + offset) * DAY_TICKS + NOON);
        helper.runAfterDelay(SKY_TICKS, () -> {
            if (!AstralSorceryCompat.isSolarEclipseDay(level)) {
                searchSolarEclipse(helper, start, firstDay, offset + 1);
                return;
            }
            SkyEvents.force(level.getServer(), SkyEvent.NONE);
            SkyEvent atNoon = SkyEvents.current();
            restoreSky(level, start);
            helper.assertTrue(atNoon == SkyEvent.ECLIPSE, "noon of Astral Sorcery's solar eclipse day is an eclipse, but was " + atNoon);
            helper.succeed();
        });
    }

    private static void restoreSky(ServerLevel level, long dayTime) {
        level.setDayTime(dayTime);
        SkyEvents.force(level.getServer(), SkyEvent.NONE);
    }

    private static Object armara() {
        return ((Supplier<?>) Reflection.staticField(CONSTELLATIONS, "ARMARA")).get();
    }

    private static int tierIndex(ServerPlayer player) {
        Object progress = Reflection.callStatic(PROGRESS, "getProgress", player, LogicalSide.SERVER);
        return Condition.ASTRAL_TIERS.indexOf(((Enum<?>) Reflection.call(progress, "getTierReached")).name().toLowerCase(Locale.ROOT));
    }

    private static int starFragments(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.STAR_FRAGMENT.get());
    }

    private static boolean isActive(Object sunDog) {
        return (Boolean) Reflection.call(sunDog, "isActive");
    }
}
