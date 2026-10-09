package dev.nostalgiacraft.skills.gametest;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.effects.GameplayEvents;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NostalgiaSkills.MODID)
@PrefixGameTestTemplate(false)
public final class AttributeGameTests {
    private static final float HIT = 4f;
    private static final float WOUNDED = 10f;
    private static final double HALF = 0.5;
    private static final double QUARTER = 0.25;
    private static final double CERTAIN = 1.0;
    private static final double CLOSE_SHOT_BLOCKS = 2.0;
    private static final double LONG_SHOT_BLOCKS = 20.0;

    private AttributeGameTests() {
    }

    private static float meleeDamage(Scenario scenario, ServerPlayer attacker) {
        return Scenario.strike(scenario.mob(EntityType.COW), attacker.damageSources().playerAttack(attacker), HIT);
    }

    private static float shotDamage(Scenario scenario, ServerPlayer archer, double blocksAway) {
        Cow target = scenario.mob(EntityType.COW);
        Arrow arrow = new Arrow(scenario.level(), archer, new ItemStack(Items.ARROW), null);
        arrow.setPos(target.position().add(blocksAway, 0, 0));
        GameplayEvents.rememberOrigin(arrow);
        return Scenario.strike(target, archer.damageSources().arrow(arrow, archer), HIT);
    }

    private static void hold(ServerPlayer player, Item item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    @GameTest(template = Scenario.EMPTY)
    public static void recklessFuryChangesDealtAndTakenDamage(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer berserker = scenario.blankPlayer();
            float plain = meleeDamage(scenario, berserker);
            scenario.learn(berserker, "berserker/hub", "berserker/reckless_fury");
            float furious = meleeDamage(scenario, berserker);
            scenario.check(furious > plain * 1.15f && furious < plain * 1.6f, "dealt " + furious + " vs plain " + plain);
            float taken = Scenario.strike(berserker, scenario.level().damageSources().fellOutOfWorld(), HIT);
            scenario.near(HIT * 1.10, taken, "damage taken with Reckless Fury");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void damageReductionCutsEveryHit(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer tank = scenario.blankPlayer();
            DamageSource theVoid = scenario.level().damageSources().fellOutOfWorld();
            scenario.near(HIT, Scenario.strike(tank, theVoid, HIT), "damage taken without reduction");
            Scenario.set(tank, ModAttributes.DAMAGE_REDUCTION, HALF);
            scenario.near(HIT * HALF, Scenario.strike(tank, theVoid, HIT), "damage taken with 50% reduction");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void magicResistanceOnlyCutsMagic(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer mage = scenario.blankPlayer();
            Scenario.set(mage, ModAttributes.MAGIC_RESISTANCE, HALF);
            scenario.afterSpawnProtection(() -> {
                scenario.near(HIT * HALF, Scenario.strike(mage, mage.damageSources().magic(), HIT), "magic damage taken");
                scenario.near(HIT, Scenario.strike(mage, mage.damageSources().generic(), HIT), "ordinary damage taken");
            });
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aCertainDodgeAvoidsAMonsterAttack(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer defender = scenario.blankPlayer();
            scenario.afterSpawnProtection(() -> {
                Zombie attacker = scenario.mob(EntityType.ZOMBIE);
                DamageSource bite = defender.damageSources().mobAttack(attacker);
                scenario.check(Scenario.strike(defender, bite, HIT) > 0, "the attack lands without dodge");
                Scenario.set(defender, ModAttributes.DODGE_CHANCE, CERTAIN);
                scenario.near(0, Scenario.strike(defender, bite, HIT), "damage taken with certain dodge");
            });
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void lifestealHealsAShareOfMeleeDamage(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer vampire = scenario.blankPlayer();
            Scenario.set(vampire, ModAttributes.LIFESTEAL, HALF);
            vampire.setHealth(WOUNDED);
            float dealt = meleeDamage(scenario, vampire);
            scenario.near(WOUNDED + dealt * HALF, vampire.getHealth(), "health after stealing half of " + dealt);
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aTreeCriticalMultipliesMeleeDamage(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer duelist = scenario.blankPlayer();
            float plain = meleeDamage(scenario, duelist);
            Scenario.set(duelist, ModAttributes.CRIT_CHANCE, CERTAIN);
            Scenario.set(duelist, ModAttributes.CRIT_DAMAGE, CERTAIN);
            scenario.near(plain * 2, meleeDamage(scenario, duelist), "damage of a certain critical with +100% critical damage");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void weaponBonusesFollowTheHeldWeaponClass(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer fighter = scenario.blankPlayer();
            Scenario.set(fighter, ModAttributes.HEAVY_WEAPON_DAMAGE, HALF);
            Scenario.set(fighter, ModAttributes.LIGHT_WEAPON_DAMAGE, QUARTER);
            scenario.near(HIT, meleeDamage(scenario, fighter), "bare-handed damage");
            hold(fighter, Items.IRON_AXE);
            scenario.near(HIT * (1 + HALF), meleeDamage(scenario, fighter), "damage with a heavy weapon");
            hold(fighter, Items.IRON_SWORD);
            scenario.near(HIT * (1 + QUARTER), meleeDamage(scenario, fighter), "damage with a light weapon");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void lowHealthDamageNeedsAWoundedAttacker(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer berserker = scenario.blankPlayer();
            Scenario.set(berserker, ModAttributes.LOW_HEALTH_DAMAGE, HALF);
            scenario.near(HIT, meleeDamage(scenario, berserker), "damage at full health");
            berserker.setHealth(berserker.getMaxHealth() * (float) QUARTER);
            scenario.near(HIT * (1 + HALF), meleeDamage(scenario, berserker), "damage below half health");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void healingReceivedScalesEveryHeal(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer patient = scenario.blankPlayer();
            Scenario.set(patient, ModAttributes.HEALING_RECEIVED, HALF);
            patient.setHealth(WOUNDED);
            patient.heal(HIT);
            scenario.near(WOUNDED + HIT * (1 + HALF), patient.getHealth(), "health after a boosted heal");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void longRangeDamageNeedsADistantShot(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer archer = scenario.blankPlayer();
            scenario.near(HIT, shotDamage(scenario, archer, LONG_SHOT_BLOCKS), "shot damage without bonuses");
            Scenario.set(archer, ModAttributes.PROJECTILE_DAMAGE, HALF);
            Scenario.set(archer, ModAttributes.LONG_RANGE_DAMAGE, HALF);
            scenario.near(HIT * (1 + HALF), shotDamage(scenario, archer, CLOSE_SHOT_BLOCKS), "damage of a close shot");
            scenario.near(HIT * (1 + HALF + HALF), shotDamage(scenario, archer, LONG_SHOT_BLOCKS), "damage of a long shot");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void miningSpeedOnlyHelpsTheRightTool(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer miner = scenario.blankPlayer();
            BlockState stone = Blocks.STONE.defaultBlockState();
            float bareHanded = miner.getDigSpeed(stone, null);
            hold(miner, Items.IRON_PICKAXE);
            float withPickaxe = miner.getDigSpeed(stone, null);
            Scenario.set(miner, ModAttributes.MINING_SPEED, CERTAIN);
            scenario.near(withPickaxe * 2, miner.getDigSpeed(stone, null), "pickaxe speed with +100% mining speed");
            hold(miner, Items.AIR);
            scenario.near(bareHanded, miner.getDigSpeed(stone, null), "bare-handed speed with +100% mining speed");
        });
    }
}
