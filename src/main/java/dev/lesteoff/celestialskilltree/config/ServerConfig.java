package dev.lesteoff.celestialskilltree.config;

import dev.lesteoff.celestialskilltree.effects.EliteMobs;
import dev.lesteoff.celestialskilltree.progression.ProgressionService;
import dev.lesteoff.celestialskilltree.progression.XpCurve;
import dev.lesteoff.celestialskilltree.server.FallenStars;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.regex.Pattern;

public final class ServerConfig {
    private static final Pattern OBJECTIVE_NAME = Pattern.compile("[A-Za-z0-9_.+-]*");
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue XP_PURCHASE_ENABLED;
    public static final ModConfigSpec.IntValue POINT_LIMIT;
    public static final ModConfigSpec.IntValue CURVE_BASE;
    public static final ModConfigSpec.IntValue CURVE_LINEAR;
    public static final ModConfigSpec.IntValue CURVE_QUADRATIC;
    public static final ModConfigSpec.DoubleValue CURVE_DISCOUNT;
    public static final ModConfigSpec.IntValue COST_MINOR;
    public static final ModConfigSpec.IntValue COST_NOTABLE;
    public static final ModConfigSpec.IntValue COST_KEYSTONE;
    public static final ModConfigSpec.IntValue COST_ROOT;
    public static final ModConfigSpec.BooleanValue KEEP_ON_DEATH;
    public static final ModConfigSpec.BooleanValue ORB_REQUIRED_FOR_RESET;
    public static final ModConfigSpec.BooleanValue ALLOW_NODE_REFUNDS;
    public static final ModConfigSpec.BooleanValue ORB_REQUIRED_FOR_NODE_REFUND;
    public static final ModConfigSpec.DoubleValue ATTRIBUTE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CAP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CAP_CRIT_CHANCE;
    public static final ModConfigSpec.DoubleValue CAP_DODGE_CHANCE;
    public static final ModConfigSpec.DoubleValue CAP_LIFESTEAL;
    public static final ModConfigSpec.DoubleValue CAP_DAMAGE_REDUCTION;
    public static final ModConfigSpec.DoubleValue CAP_MAGIC_RESISTANCE;
    public static final ModConfigSpec.DoubleValue CAP_MOVEMENT_SPEED;
    public static final ModConfigSpec.IntValue LIFESTEAL_COOLDOWN_TICKS;
    public static final ModConfigSpec.ConfigValue<String> LIGHT_WEAPON_TAG;
    public static final ModConfigSpec.ConfigValue<String> HEAVY_WEAPON_TAG;
    public static final ModConfigSpec.ConfigValue<String> CROSSBOW_TAG;
    public static final ModConfigSpec.ConfigValue<String> MINING_TOOL_TAG;
    public static final ModConfigSpec.IntValue MAX_REQUESTS_PER_SECOND;
    public static final ModConfigSpec.IntValue MAX_BATCH_SIZE;
    public static final ModConfigSpec.BooleanValue SHOOTING_STARS_ENABLED;
    public static final ModConfigSpec.IntValue SHOOTING_STAR_AVERAGE_MINUTES;
    public static final ModConfigSpec.DoubleValue ELITE_MOB_CHANCE;
    public static final ModConfigSpec.BooleanValue ECLIPSE_ENABLED;
    public static final ModConfigSpec.BooleanValue COMMUNITY_ENABLED;
    public static final ModConfigSpec.EnumValue<FallenStars.Mode> DROP_STAR_ON_DEATH;
    public static final ModConfigSpec.IntValue ECLIPSE_AVERAGE_DAYS;
    public static final ModConfigSpec.IntValue ELITE_MOB_NODES;
    public static final ModConfigSpec.BooleanValue ELITE_SPAWNER_MOBS;
    public static final ModConfigSpec.BooleanValue ELITE_RADAR_TEAM;
    public static final ModConfigSpec.BooleanValue ELITE_BOSS_BAR;
    public static final ModConfigSpec.IntValue BOSS_FRAGMENTS;
    public static final ModConfigSpec.IntValue FAMILIAR_SWAP_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue L2HOSTILITY_LEVELS_PER_NODE;
    public static final ModConfigSpec.DoubleValue RELIC_XP_SHARE;
    public static final ModConfigSpec.IntValue LIVING_FRAGMENT_XP_PER_LEVEL;
    public static final ModConfigSpec.IntValue LIVING_FRAGMENT_POINTS;
    public static final ModConfigSpec.DoubleValue ELITE_HEALTH_BONUS;
    public static final ModConfigSpec.DoubleValue ELITE_DAMAGE_BONUS;
    public static final ModConfigSpec.IntValue CURSED_RING_POINTS;
    public static final ModConfigSpec.DoubleValue PET_TREE_SHARE;
    public static final ModConfigSpec.IntValue LIQUID_XP_MB_PER_POINT;
    public static final ModConfigSpec.IntValue CONDENSER_XP_PER_FRAGMENT;
    public static final ModConfigSpec.IntValue BASIN_MB_PER_TICK;
    public static final ModConfigSpec.IntValue STELLAR_AMMO_SHOTS;
    public static final ModConfigSpec.ConfigValue<String> LAST_LIFE_OBJECTIVE;
    public static final ModConfigSpec.IntValue LAST_LIFE_SCORE;
    public static final ModConfigSpec.DoubleValue STELLAR_AMMO_BONUS;
    public static final ModConfigSpec.BooleanValue REDIRECT_TO_APOTHIC_ATTRIBUTES;
    public static final ModConfigSpec.BooleanValue REDIRECT_TO_PUFFISH_ATTRIBUTES;
    public static final ModConfigSpec.BooleanValue REDIRECT_TO_L2_DAMAGE_TRACKER;
    public static final ModConfigSpec.EnumValue<EliteMobs.Stacking> ELITE_STACKING;
    public static final ModConfigSpec.BooleanValue ECLIPSE_MONSTERS_SPAWN;
    public static final ModConfigSpec.BooleanValue ECLIPSE_FOLLOWS_ASTRAL_SORCERY;
    public static final ModConfigSpec.BooleanValue STAR_SHOWER_ENABLED;
    public static final ModConfigSpec.IntValue STAR_SHOWER_AVERAGE_DAYS;
    public static final ModConfigSpec.IntValue STAR_SHOWER_STARS_PER_MINUTE;
    public static final ModConfigSpec.BooleanValue STAR_SHOWER_FOLLOWS_ASTRAL_SORCERY;
    public static final ModConfigSpec.IntValue ASTRAL_TIER_FRAGMENTS;
    public static final ModConfigSpec.IntValue ASTRAL_CONSTELLATION_FRAGMENTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Skill point purchases with vanilla experience").push("progression");
        XP_PURCHASE_ENABLED = builder.comment("Allow 'Buy Skill Point' with raw vanilla XP").define("xpPurchaseEnabled", true);
        POINT_LIMIT = builder.comment("Maximum points a player can earn through purchases (and grants without override)")
                .defineInRange("pointLimit", 10_000, 1, 10_000);
        CURVE_BASE = builder.comment("nextPointCost(p) = ceil((base + linear*p + quadratic*p^2) * (1 - discount)), p = points bought so far")
                .defineInRange("curveBase", 100, 1, 1_000_000);
        CURVE_LINEAR = builder.defineInRange("curveLinear", 20, 0, 1_000_000);
        CURVE_QUADRATIC = builder.defineInRange("curveQuadratic", 2, 0, 1_000_000);
        CURVE_DISCOUNT = builder.comment("Global discount fraction, 0.0 - 0.9").defineInRange("curveDiscount", 0.0, 0.0, 0.9);
        builder.pop();
        builder.comment("Point cost per node type; -1 keeps the cost written in the tree data").push("costs");
        COST_MINOR = builder.defineInRange("minor", -1, -1, 100);
        COST_NOTABLE = builder.defineInRange("notable", -1, -1, 100);
        COST_KEYSTONE = builder.defineInRange("keystone", -1, -1, 100);
        COST_ROOT = builder.comment("Cost of additional roots reached through the graph (the starting root is always free)")
                .defineInRange("root", -1, -1, 100);
        builder.pop();
        builder.push("lifecycle");
        KEEP_ON_DEATH = builder.comment("Keep skill progression after death").define("keepOnDeath", true);
        ORB_REQUIRED_FOR_RESET = builder.comment("Players need an Orb of Reconfiguration to reset (operators can always use the command)")
                .define("orbRequiredForReset", true);
        ALLOW_NODE_REFUNDS = builder.comment("Allow refunding a single skill (together with the allocations that depend on it) without a full reset")
                .define("allowSingleSkillRefunds", false);
        ORB_REQUIRED_FOR_NODE_REFUND = builder.comment("Also consume an Orb of Reconfiguration for a single-skill refund")
                .define("orbRequiredForNodeRefund", false);
        builder.pop();
        builder.comment("Scaling of everything the tree grants").push("tuning");
        ATTRIBUTE_MULTIPLIER = builder.comment("Multiplies the amount of every attribute effect granted by the tree (1.0 = as designed)")
                .defineInRange("attributeMultiplier", 1.0, 0.0, 10.0);
        CAP_MULTIPLIER = builder.comment("Multiplies every cap below (1.0 = as configured)")
                .defineInRange("capMultiplier", 1.0, 0.0, 10.0);
        builder.pop();
        builder.comment("Caps on what the tree itself grants; equipment and other mods are never clamped").push("caps");
        CAP_CRIT_CHANCE = builder.defineInRange("critChance", 0.5, 0.0, 1.0);
        CAP_DODGE_CHANCE = builder.defineInRange("dodgeChance", 0.3, 0.0, 0.9);
        CAP_LIFESTEAL = builder.defineInRange("lifesteal", 0.1, 0.0, 1.0);
        CAP_DAMAGE_REDUCTION = builder.defineInRange("damageReduction", 0.4, 0.0, 0.9);
        CAP_MAGIC_RESISTANCE = builder.defineInRange("magicResistance", 0.6, 0.0, 0.9);
        CAP_MOVEMENT_SPEED = builder.comment("Maximum total movement-speed multiplier bonus granted by the tree")
                .defineInRange("movementSpeed", 0.5, 0.0, 2.0);
        LIFESTEAL_COOLDOWN_TICKS = builder.defineInRange("lifestealCooldownTicks", 10, 0, 200);
        builder.pop();
        builder.comment("Item classification tags").push("tags");
        LIGHT_WEAPON_TAG = builder.define("lightWeapons", "celestialskilltree:light_weapons", ServerConfig::isResourceId);
        HEAVY_WEAPON_TAG = builder.define("heavyWeapons", "celestialskilltree:heavy_weapons", ServerConfig::isResourceId);
        CROSSBOW_TAG = builder.define("crossbows", "celestialskilltree:crossbows", ServerConfig::isResourceId);
        MINING_TOOL_TAG = builder.define("miningTools", "celestialskilltree:mining_tools", ServerConfig::isResourceId);
        builder.pop();
        builder.comment("Network abuse limits").push("limits");
        MAX_REQUESTS_PER_SECOND = builder.defineInRange("maxRequestsPerSecond", 10, 1, 100);
        MAX_BATCH_SIZE = builder.defineInRange("maxBatchSize", 400, 1, 400);
        builder.pop();
        builder.comment("World events").push("events");
        SHOOTING_STARS_ENABLED = builder.comment("At night, a falling star occasionally crosses the sky near a player in the Overworld and leaves a star fragment")
                .define("shootingStarsEnabled", true);
        SHOOTING_STAR_AVERAGE_MINUTES = builder.comment("Average minutes of night between two shooting stars, per player")
                .defineInRange("shootingStarAverageMinutes", 30, 1, 1440);
        ELITE_MOB_CHANCE = builder.comment("Chance that a newly spawned monster is Constellated (stronger, drops a star fragment); 0 disables")
                .defineInRange("eliteMobChance", 0.02, 0.0, 1.0);
        ELITE_MOB_NODES = builder.comment("How many random skills beyond a hub a Constellated monster receives")
                .defineInRange("eliteMobNodes", 8, 0, 60);
        ELITE_HEALTH_BONUS = builder.comment("Extra maximum health of a Constellated monster on top of its constellation (0.5 = +50%)")
                .defineInRange("eliteHealthBonus", 0.5, 0.0, 10.0);
        ELITE_DAMAGE_BONUS = builder.comment("Extra attack damage of a Constellated monster on top of its constellation (0.25 = +25%)")
                .defineInRange("eliteDamageBonus", 0.25, 0.0, 10.0);
        ELITE_SPAWNER_MOBS = builder.comment("Monsters from spawners and trial spawners may be Constellated; leaving this on with a mob farm turns it into a star fragment farm")
                .define("eliteSpawnerMobs", false);
        ELITE_RADAR_TEAM = builder.comment("Constellated monsters join the light purple scoreboard team 'celestialskilltree_constellated', so radar mods such as Xaero's Minimap list them in their own team category")
                .define("eliteRadarTeam", true);
        ELITE_BOSS_BAR = builder.comment("A Constellated monster shows a purple boss bar to players within 24 blocks who can see it; Enhanced Boss Bars draws it with this mod's own frame")
                .define("eliteBossBar", true);
        BOSS_FRAGMENTS = builder.comment("Star fragments a player receives the first time they kill each monster of #celestialskilltree:first_kill_fragment (bosses); 0 disables")
                .defineInRange("bossFragments", 2, 0, 16);
        FAMILIAR_SWAP_COOLDOWN_SECONDS = builder.comment("Occultism: seconds a familiar needs before it can swap saved builds with its owner again (sneak and use an Orb of Reconfiguration on it; the orb is not consumed)")
                .defineInRange("familiarSwapCooldownSeconds", 600, 0, 86_400);
        L2HOSTILITY_LEVELS_PER_NODE = builder.comment("L2Hostility: a Constellated monster learns one more skill of its constellation for every this many hostility levels (at most 60 extra); 0 disables")
                .defineInRange("l2hostilityLevelsPerNode", 10, 0, 1_000);
        RELIC_XP_SHARE = builder.comment("Relics: fraction of the experience a relic gains that its bearer also receives as ordinary experience, the currency skill points are bought with; 0 disables")
                .defineInRange("relicXpShare", 0.25, 0.0, 10.0);
        LIVING_FRAGMENT_XP_PER_LEVEL = builder.comment("Experience a Living Fragment must witness for each of its 5 levels; it grows with the experience orbs its carrier picks up (the experience is not taken)")
                .defineInRange("livingFragmentXpPerLevel", 500, 1, 1_000_000);
        LIVING_FRAGMENT_POINTS = builder.comment("Skill points a fully grown Living Fragment is worth")
                .defineInRange("livingFragmentPoints", 2, 1, 100);
        CURSED_RING_POINTS = builder.comment("Enigmatic Legacy+: skill points lent while the Ring of the Seven Curses is worn; taking the ring off takes them back and, if they were already spent, resets the whole build; 0 disables")
                .defineInRange("cursedRingPoints", 5, 0, 100);
        PET_TREE_SHARE = builder.comment("Fraction of the owner's tree bonuses to health, attack damage, armor, toughness, movement speed and knockback resistance that tamed creatures of #celestialskilltree:inherits_tree receive (Ice and Fire dragons, Companions); 0 disables")
                .defineInRange("petTreeShare", 0.25, 0.0, 1.0);
        LIQUID_XP_MB_PER_POINT = builder.comment("Millibuckets of a #c:experience fluid worth one experience point (20 is the convention of Create, Mekanism, Industrial Foregoing and others). Tanks and buckets carried in the inventory then count towards buying skill points; 0 disables")
                .defineInRange("liquidXpMbPerPoint", 20, 0, 10_000);
        CONDENSER_XP_PER_FRAGMENT = builder.comment("Experience points of liquid experience a Stellar Condenser turns into one Star Fragment. This automates skill points: every fragment is one point. 0 disables the condenser")
                .defineInRange("condenserXpPerFragment", 4_000, 0, 10_000_000);
        BASIN_MB_PER_TICK = builder.comment("Millibuckets of liquid experience a Stellar Condenser or a Community Basin absorbs per tick; with the defaults a fragment takes 200 seconds")
                .defineInRange("basinMbPerTick", 20, 1, 100_000);
        STELLAR_AMMO_SHOTS = builder.comment("Gunshots one Stellar Ammo empowers; a shot is spent when a bullet of #celestialskilltree:counts_as_projectile hits")
                .defineInRange("stellarAmmoShots", 24, 1, 1_000);
        STELLAR_AMMO_BONUS = builder.comment("Extra damage of a gunshot empowered by Stellar Ammo (0.5 = +50%)")
                .defineInRange("stellarAmmoBonus", 0.5, 0.0, 10.0);
        LAST_LIFE_OBJECTIVE = builder.comment("Lives mods (Third Life and the like): scoreboard objective holding each player's remaining lives; while a player's score equals lastLifeScore they carry a second birth sign, the one opposite their own. Empty disables; /celestialskilltree extrasign and the KubeJS binding grant the same sign by hand")
                .define("lastLifeObjective", "", ServerConfig::isObjectiveName);
        LAST_LIFE_SCORE = builder.comment("Score of lastLifeObjective that means the player is on their last life")
                .defineInRange("lastLifeScore", 1, -1_000_000, 1_000_000);
        ECLIPSE_ENABLED = builder.comment("Some days the moon covers the sun from dawn to dusk: resets and refunds need no Orb of Reconfiguration")
                .define("eclipseEnabled", true);
        ECLIPSE_AVERAGE_DAYS = builder.comment("Average number of game days between two eclipses")
                .defineInRange("eclipseAverageDays", 20, 1, 1000);
        ECLIPSE_MONSTERS_SPAWN = builder.comment("During an eclipse the Overworld is as dark as night: monsters spawn on the surface and undead do not burn")
                .define("eclipseMonstersSpawn", true);
        ECLIPSE_FOLLOWS_ASTRAL_SORCERY = builder.comment("With Astral Sorcery installed, the eclipse happens on the day of its solar eclipse instead of on its own days")
                .define("eclipseFollowsAstralSorcery", true);
        STAR_SHOWER_ENABLED = builder.comment("Some nights are a star shower: falling stars are far more frequent")
                .define("starShowerEnabled", true);
        STAR_SHOWER_AVERAGE_DAYS = builder.comment("Average number of game days between two star showers")
                .defineInRange("starShowerAverageDays", 12, 1, 1000);
        STAR_SHOWER_STARS_PER_MINUTE = builder.comment("Falling stars per minute, per player, during a star shower")
                .defineInRange("starShowerStarsPerMinute", 4, 1, 60);
        STAR_SHOWER_FOLLOWS_ASTRAL_SORCERY = builder.comment("With Astral Sorcery installed, the star shower happens on the night of its lunar eclipse instead of on its own nights")
                .define("starShowerFollowsAstralSorcery", true);
        DROP_STAR_ON_DEATH = builder.comment("On death the newest stand-alone skill drops as a Fallen Star: its owner can take it back,",
                        "anyone else gains its points. OFF, PVP_ONLY (killed by another player) or ALWAYS")
                .defineEnum("dropStarOnDeath", FallenStars.Mode.OFF);
        COMMUNITY_ENABLED = builder.comment("Players may donate XP to the server-wide constellation; each tier reached rewards everyone")
                .define("communityEnabled", true);
        builder.pop();
        builder.comment("Optional mod integrations").push("integrations");
        REDIRECT_TO_APOTHIC_ATTRIBUTES = builder.comment("With Apothic Attributes installed, skills that grant critical chance, critical damage, dodge,",
                        "lifesteal, mining speed and healing received raise the Apothic attribute instead of this mod's own,",
                        "so each stat is rolled once and shown as one number")
                .define("redirectToApothicAttributes", true);
        REDIRECT_TO_PUFFISH_ATTRIBUTES = builder.comment("With Pufferfish's Attributes installed, skills that grant lifesteal, mining speed, healing received and",
                        "projectile damage raise the Pufferfish attribute instead of this mod's own; Apothic Attributes wins where both overlap")
                .define("redirectToPuffishAttributes", true);
        REDIRECT_TO_L2_DAMAGE_TRACKER = builder.comment("With L2DamageTracker installed and Apothic Attributes absent, skills that grant critical chance and",
                        "critical damage raise its Weapon Crit Rate and Weapon Crit Damage instead of this mod's own")
                .define("redirectToL2DamageTracker", true);
        ELITE_STACKING = builder.comment("EXCLUSIVE: a monster that Champions or L2Hostility already made special is never Constellated too.",
                        "STACK: the rolls are independent and one monster may be all of them")
                .defineEnum("eliteStacking", EliteMobs.Stacking.EXCLUSIVE);
        ASTRAL_TIER_FRAGMENTS = builder.comment("With Astral Sorcery installed, Star Fragments given for each new research tier reached; 0 turns it off")
                .defineInRange("astralTierFragments", 2, 0, 64);
        ASTRAL_CONSTELLATION_FRAGMENTS = builder.comment("With Astral Sorcery installed, Star Fragments given for each constellation memorized; 0 turns it off")
                .defineInRange("astralConstellationFragments", 1, 0, 64);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static ProgressionService.Settings progression() {
        return new ProgressionService.Settings(XP_PURCHASE_ENABLED.get(), POINT_LIMIT.get(),
                new XpCurve(CURVE_BASE.get(), CURVE_LINEAR.get(), CURVE_QUADRATIC.get(), CURVE_DISCOUNT.get()));
    }

    private static boolean isResourceId(Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    private static boolean isObjectiveName(Object value) {
        return value instanceof String name && OBJECTIVE_NAME.matcher(name).matches();
    }
}
