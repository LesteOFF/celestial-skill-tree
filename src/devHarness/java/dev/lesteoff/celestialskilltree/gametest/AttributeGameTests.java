package dev.lesteoff.celestialskilltree.gametest;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.effects.CombatEvents;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.progression.PathFinder;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

import java.util.List;

@GameTestHolder(CelestialSkillTree.MODID)
@PrefixGameTestTemplate(false)
public final class AttributeGameTests {
    private static final float HIT = 4f;
    private static final double TARGET_HEALTH = 1000;
    private static final String RECKLESS_FURY = "berserker/reckless_fury";
    private static final double RECKLESS_FURY_DEALT = 0.20;
    private static final double RECKLESS_FURY_TAKEN = 0.10;
    private static final float WOUNDED = 10f;
    private static final double HALF = 0.5;
    private static final double QUARTER = 0.25;
    private static final double CERTAIN = 1.0;
    private static final double CLOSE_SHOT_BLOCKS = 2.0;
    private static final double LONG_SHOT_BLOCKS = 20.0;

    private AttributeGameTests() {
    }

    private static Cow target(Scenario scenario) {
        Cow target = scenario.mob(EntityType.COW);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
        target.setHealth(target.getMaxHealth());
        return target;
    }

    private static float meleeDamage(ServerPlayer attacker, Cow target) {
        target.setHealth(target.getMaxHealth());
        return Scenario.strike(target, attacker.damageSources().playerAttack(attacker), HIT);
    }

    private static float shotDamage(ServerPlayer archer, Cow target, double blocksAway) {
        target.setHealth(target.getMaxHealth());
        Arrow arrow = new Arrow(target.level(), archer, new ItemStack(Items.ARROW), null);
        arrow.setPos(target.position().add(blocksAway, 0, 0));
        CombatEvents.rememberOrigin(arrow);
        return Scenario.strike(target, archer.damageSources().arrow(arrow, archer), HIT);
    }

    private static void hold(ServerPlayer player, Item item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    @GameTest(template = Scenario.EMPTY)
    public static void recklessFuryChangesDealtAndTakenDamage(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer berserker = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(berserker, "berserker/hub");
            List<String> route = PathFinder.route(scenario.graph(), state, List.of(), Scenario.id(RECKLESS_FURY)).orElseThrow().newNodes();
            scenario.confirm(state, route.stream().filter(id -> !id.equals(Scenario.id(RECKLESS_FURY))).toList(), "the way to Reckless Fury");
            EffectEngine.refresh(berserker);
            Cow target = target(scenario);
            float plain = meleeDamage(berserker, target);
            float plainTaken = Scenario.strike(berserker, scenario.level().damageSources().fellOutOfWorld(), HIT);
            scenario.confirm(state, List.of(Scenario.id(RECKLESS_FURY)), "Reckless Fury");
            EffectEngine.refresh(berserker);
            float furious = meleeDamage(berserker, target);
            float taken = Scenario.strike(berserker, scenario.level().damageSources().fellOutOfWorld(), HIT);
            if (Scenario.otherModsChangeDamage()) {
                scenario.check(furious > plain && taken > plainTaken, "Reckless Fury raises dealt (" + plain + " -> " + furious
                        + ") and taken (" + plainTaken + " -> " + taken + ") damage");
            } else {
                scenario.near(plain + HIT * RECKLESS_FURY_DEALT, furious, "damage dealt with Reckless Fury");
                scenario.near(plainTaken + HIT * RECKLESS_FURY_TAKEN, taken, "damage taken with Reckless Fury");
            }
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
            float dealt = meleeDamage(vampire, target(scenario));
            scenario.near(WOUNDED + dealt * HALF, vampire.getHealth(), "health after stealing half of " + dealt);
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aTreeCriticalMultipliesMeleeDamage(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer duelist = scenario.blankPlayer();
            Cow target = target(scenario);
            float plain = meleeDamage(duelist, target);
            Scenario.set(duelist, ModAttributes.CRIT_CHANCE, CERTAIN);
            Scenario.set(duelist, ModAttributes.CRIT_DAMAGE, CERTAIN);
            scenario.near(plain * 2, meleeDamage(duelist, target), "damage of a certain critical with +100% critical damage");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void weaponBonusesFollowTheHeldWeaponClass(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer fighter = scenario.blankPlayer();
            Scenario.set(fighter, ModAttributes.HEAVY_WEAPON_DAMAGE, HALF);
            Scenario.set(fighter, ModAttributes.LIGHT_WEAPON_DAMAGE, QUARTER);
            Cow target = target(scenario);
            float bareHanded = meleeDamage(fighter, target);
            hold(fighter, Items.IRON_AXE);
            scenario.near(bareHanded * (1 + HALF), meleeDamage(fighter, target), "damage with a heavy weapon");
            hold(fighter, Items.IRON_SWORD);
            scenario.near(bareHanded * (1 + QUARTER), meleeDamage(fighter, target), "damage with a light weapon");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void lowHealthDamageNeedsAWoundedAttacker(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer berserker = scenario.blankPlayer();
            Scenario.set(berserker, ModAttributes.LOW_HEALTH_DAMAGE, HALF);
            Cow target = target(scenario);
            float atFullHealth = meleeDamage(berserker, target);
            berserker.setHealth(berserker.getMaxHealth() * (float) QUARTER);
            scenario.near(atFullHealth * (1 + HALF), meleeDamage(berserker, target), "damage below half health");
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
            Cow target = target(scenario);
            float plainShot = shotDamage(archer, target, LONG_SHOT_BLOCKS);
            Scenario.set(archer, ModAttributes.PROJECTILE_DAMAGE, HALF);
            Scenario.set(archer, ModAttributes.LONG_RANGE_DAMAGE, HALF);
            scenario.near(plainShot * (1 + HALF), shotDamage(archer, target, CLOSE_SHOT_BLOCKS), "damage of a close shot");
            scenario.near(plainShot * (1 + HALF + HALF), shotDamage(archer, target, LONG_SHOT_BLOCKS), "damage of a long shot");
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
