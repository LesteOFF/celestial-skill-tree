package dev.nostalgiacraft.skills.config;

import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.XpCurve;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative gameplay settings (world/serverconfig/nostalgiaskills-server.toml). */
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

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Skill point purchases with vanilla experience").push("progression");
        XP_PURCHASE_ENABLED = b.comment("Allow 'Buy Skill Point' with raw vanilla XP").define("xpPurchaseEnabled", true);
        POINT_LIMIT = b.comment("Maximum points a player can earn through purchases (and grants without override)")
                .defineInRange("pointLimit", 120, 1, 10_000);
        CURVE_BASE = b.comment("nextPointCost(p) = ceil((base + linear*p + quadratic*p^2) * (1 - discount)), p = points bought so far")
                .defineInRange("curveBase", 100, 1, 1_000_000);
        CURVE_LINEAR = b.defineInRange("curveLinear", 20, 0, 1_000_000);
        CURVE_QUADRATIC = b.defineInRange("curveQuadratic", 2, 0, 1_000_000);
        CURVE_DISCOUNT = b.comment("Global discount fraction, 0.0 - 0.9").defineInRange("curveDiscount", 0.0, 0.0, 0.9);
        b.pop();
        b.comment("Point cost per node type; -1 keeps the cost written in the tree data").push("costs");
        COST_MINOR = b.defineInRange("minor", -1, -1, 100);
        COST_NOTABLE = b.defineInRange("notable", -1, -1, 100);
        COST_KEYSTONE = b.defineInRange("keystone", -1, -1, 100);
        COST_ROOT = b.comment("Cost of additional roots reached through the graph (the starting root is always free)")
                .defineInRange("root", -1, -1, 100);
        b.pop();
        b.push("lifecycle");
        KEEP_ON_DEATH = b.comment("Keep skill progression after death").define("keepOnDeath", true);
        ORB_REQUIRED_FOR_RESET = b.comment("Players need an Orb of Reconfiguration to reset (operators can always use the command)")
                .define("orbRequiredForReset", true);
        b.pop();
        b.comment("Caps on this mod's own contribution; equipment and other mods are never clamped").push("caps");
        CAP_CRIT_CHANCE = b.defineInRange("critChance", 0.5, 0.0, 1.0);
        CAP_DODGE_CHANCE = b.defineInRange("dodgeChance", 0.3, 0.0, 0.9);
        CAP_LIFESTEAL = b.defineInRange("lifesteal", 0.1, 0.0, 1.0);
        CAP_DAMAGE_REDUCTION = b.defineInRange("damageReduction", 0.4, 0.0, 0.9);
        CAP_MAGIC_RESISTANCE = b.defineInRange("magicResistance", 0.6, 0.0, 0.9);
        CAP_MOVEMENT_SPEED = b.comment("Maximum total movement-speed multiplier bonus granted by the tree")
                .defineInRange("movementSpeed", 0.5, 0.0, 2.0);
        LIFESTEAL_COOLDOWN_TICKS = b.defineInRange("lifestealCooldownTicks", 10, 0, 200);
        b.pop();
        b.comment("Item classification tags").push("tags");
        LIGHT_WEAPON_TAG = b.define("lightWeapons", "nostalgiaskills:light_weapons");
        HEAVY_WEAPON_TAG = b.define("heavyWeapons", "nostalgiaskills:heavy_weapons");
        BOW_TAG = b.define("bows", "nostalgiaskills:bows");
        CROSSBOW_TAG = b.define("crossbows", "nostalgiaskills:crossbows");
        MINING_TOOL_TAG = b.define("miningTools", "nostalgiaskills:mining_tools");
        b.pop();
        b.comment("Network abuse limits").push("limits");
        MAX_REQUESTS_PER_SECOND = b.defineInRange("maxRequestsPerSecond", 10, 1, 100);
        MAX_BATCH_SIZE = b.defineInRange("maxBatchSize", 400, 1, 400);
        b.pop();
        SPEC = b.build();
    }

    private ServerConfig() {
    }

    public static ProgressionService.Settings progression() {
        return new ProgressionService.Settings(XP_PURCHASE_ENABLED.get(), POINT_LIMIT.get(),
                new XpCurve(CURVE_BASE.get(), CURVE_LINEAR.get(), CURVE_QUADRATIC.get(), CURVE_DISCOUNT.get()));
    }
}
