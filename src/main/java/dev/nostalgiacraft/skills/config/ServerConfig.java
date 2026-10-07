package dev.nostalgiacraft.skills.config;

import dev.nostalgiacraft.skills.effects.EliteMobs;
import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.XpCurve;
import dev.nostalgiacraft.skills.server.FallenStars;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
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
    public static final ModConfigSpec.ConfigValue<String> BOW_TAG;
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
    public static final ModConfigSpec.BooleanValue REDIRECT_TO_APOTHIC_ATTRIBUTES;
    public static final ModConfigSpec.BooleanValue REDIRECT_TO_PUFFISH_ATTRIBUTES;
    public static final ModConfigSpec.BooleanValue REDIRECT_TO_L2_DAMAGE_TRACKER;
    public static final ModConfigSpec.EnumValue<EliteMobs.Stacking> ELITE_STACKING;
    public static final ModConfigSpec.BooleanValue ECLIPSE_MONSTERS_SPAWN;
    public static final ModConfigSpec.BooleanValue STAR_SHOWER_ENABLED;
    public static final ModConfigSpec.IntValue STAR_SHOWER_AVERAGE_DAYS;
    public static final ModConfigSpec.IntValue STAR_SHOWER_STARS_PER_MINUTE;

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
                .define("allowNodeRefunds", true);
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
        LIGHT_WEAPON_TAG = builder.define("lightWeapons", "nostalgiaskills:light_weapons");
        HEAVY_WEAPON_TAG = builder.define("heavyWeapons", "nostalgiaskills:heavy_weapons");
        BOW_TAG = builder.define("bows", "nostalgiaskills:bows");
        CROSSBOW_TAG = builder.define("crossbows", "nostalgiaskills:crossbows");
        MINING_TOOL_TAG = builder.define("miningTools", "nostalgiaskills:mining_tools");
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
        ELITE_SPAWNER_MOBS = builder.comment("Monsters from spawners and trial spawners may be Constellated; leaving this on with a mob farm turns it into a star fragment farm")
                .define("eliteSpawnerMobs", false);
        ECLIPSE_ENABLED = builder.comment("Some days the moon covers the sun from dawn to dusk: resets and refunds need no Orb of Reconfiguration")
                .define("eclipseEnabled", true);
        ECLIPSE_AVERAGE_DAYS = builder.comment("Average number of game days between two eclipses")
                .defineInRange("eclipseAverageDays", 20, 1, 1000);
        ECLIPSE_MONSTERS_SPAWN = builder.comment("During an eclipse the Overworld is as dark as night: monsters spawn on the surface and undead do not burn")
                .define("eclipseMonstersSpawn", true);
        STAR_SHOWER_ENABLED = builder.comment("Some nights are a star shower: falling stars are far more frequent")
                .define("starShowerEnabled", true);
        STAR_SHOWER_AVERAGE_DAYS = builder.comment("Average number of game days between two star showers")
                .defineInRange("starShowerAverageDays", 12, 1, 1000);
        STAR_SHOWER_STARS_PER_MINUTE = builder.comment("Falling stars per minute, per player, during a star shower")
                .defineInRange("starShowerStarsPerMinute", 4, 1, 60);
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
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static ProgressionService.Settings progression() {
        return new ProgressionService.Settings(XP_PURCHASE_ENABLED.get(), POINT_LIMIT.get(),
                new XpCurve(CURVE_BASE.get(), CURVE_LINEAR.get(), CURVE_QUADRATIC.get(), CURVE_DISCOUNT.get()));
    }
}
