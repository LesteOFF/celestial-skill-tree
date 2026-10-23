package dev.nostalgiacraft.skills.gametest;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.data.WorldSkillData;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import dev.nostalgiacraft.skills.entity.FallingStar;
import dev.nostalgiacraft.skills.graph.CommunityTier;
import dev.nostalgiacraft.skills.progression.CommunityProgress;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import dev.nostalgiacraft.skills.registry.FallenStarItem;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import dev.nostalgiacraft.skills.registry.ModBlocks;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.registry.ModTags;
import dev.nostalgiacraft.skills.registry.StarFragmentItem;
import dev.nostalgiacraft.skills.server.CommunityTree;
import dev.nostalgiacraft.skills.server.FallenStars;
import dev.nostalgiacraft.skills.server.PlayerExperience;
import dev.nostalgiacraft.skills.server.SkillService;
import dev.nostalgiacraft.skills.server.SkyEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(NostalgiaSkills.MODID)
@PrefixGameTestTemplate(false)
public final class WorldGameTests {
    private static final String GUARDIAN_HUB = "guardian/hub";
    private static final String GATEKEEPER = "guardian/gatekeeper";
    private static final String CONSTELLATED_TEAM = "nostalgiaskills_constellated";
    private static final BlockPos SOIL = Scenario.CENTER.below();
    private static final Vec3 STAR_TRAJECTORY = new Vec3(0.4, -0.8, 0.4);
    private static final double STAR_HEIGHT = 10.0;
    private static final int NOON = 6_000;
    private static final int ELITE_NODES = 8;
    private static final long ELITE_SEED = 42;
    private static final float FATAL = 10_000f;

    private WorldGameTests() {
    }

    private static int fragments(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.STAR_FRAGMENT.get());
    }

    private static void slay(LivingEntity victim, ServerPlayer killer) {
        victim.hurt(killer.damageSources().playerAttack(killer), FATAL);
    }

    private static long communityGoal() {
        return CommunityProgress.goal(SkillTreeManager.communityTiers().stream().map(CommunityTier::xpRequired).toList());
    }

    private static int harvestRipeWheat(Scenario scenario, GameTestHelper helper, ServerPlayer farmer) {
        helper.setBlock(SOIL, Blocks.FARMLAND);
        helper.setBlock(SOIL.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        farmer.gameMode.destroyBlock(helper.absolutePos(SOIL.above()));
        return scenario.collect(Items.WHEAT);
    }

    @GameTest(template = Scenario.EMPTY)
    public static void bothExperienceBasinsShowTheirTankToPipesAndSensors(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            for (Block basin : List.of(ModBlocks.STELLAR_CONDENSER.get(), ModBlocks.COMMUNITY_BASIN.get())) {
                helper.setBlock(Scenario.CENTER, basin);
                IFluidHandler tank = scenario.level().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(Scenario.CENTER), null);
                scenario.check(tank != null && tank.getTanks() == 1 && tank.getTankCapacity(0) > 0,
                        BuiltInRegistries.BLOCK.getKey(basin) + " exposes its liquid experience tank");
            }
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aStarFragmentGrantsOnePointUpToTheLimit(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.player();
            PlayerSkillState state = ModAttachments.state(player);
            int limit = ServerConfig.POINT_LIMIT.get();
            ItemStack fragments = new ItemStack(ModItems.STAR_FRAGMENT.get(), 2);
            scenario.check(StarFragmentItem.redeem(player, fragments), "the first fragment is redeemed");
            scenario.same(1, state.availablePoints(), "points after one fragment");
            scenario.same(1, fragments.getCount(), "fragments left");
            LedgerService.grant(state, limit - state.totalPoints(), limit, false);
            scenario.check(!StarFragmentItem.redeem(player, fragments), "nothing is redeemed at the point limit");
            scenario.same(limit, state.totalPoints(), "points at the limit");
            scenario.same(1, fragments.getCount(), "fragments left at the limit");
            scenario.check(state.ledgerHolds(), "ledger invariant");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aFallingStarLandsAsAFragment(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            FallingStar star = scenario.track(FallingStar.launch(scenario.level(), scenario.center().add(0, STAR_HEIGHT, 0), STAR_TRAJECTORY));
            scenario.check(star.isAlive() && star.getDeltaMovement().y < 0, "the star falls from above");
            ItemEntity landed = scenario.track(star.land());
            scenario.check(star.isRemoved(), "the star is gone once it lands");
            scenario.check(landed.getItem().is(ModItems.STAR_FRAGMENT.get()), "it leaves a star fragment");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aConstellatedMonsterIsStrongerAndMarked(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            Zombie zombie = scenario.mob(EntityType.ZOMBIE);
            double health = zombie.getMaxHealth();
            double damage = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
            scenario.check(!EliteMobs.isElite(zombie), "an ordinary zombie is not Constellated");
            scenario.check(EliteMobs.empower(zombie, RandomSource.create(ELITE_SEED), ELITE_NODES), "the zombie is empowered");
            scenario.check(EliteMobs.isElite(zombie) && zombie.hasCustomName(), "it is marked and named");
            scenario.check(zombie.getTeam() != null && zombie.getTeam().getName().equals(CONSTELLATED_TEAM), "it joins the radar team");
            scenario.atLeast(health * 1.5, zombie.getMaxHealth(), "max health");
            scenario.atLeast(damage * 1.25, zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), "attack damage");
            scenario.near(zombie.getMaxHealth(), zombie.getHealth(), "health after being empowered");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void onlyAConstellatedMonsterDropsAFragment(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer killer = scenario.blankPlayer();
            slay(scenario.mob(EntityType.ZOMBIE), killer);
            scenario.same(0, scenario.collect(ModItems.STAR_FRAGMENT.get()), "fragments from an ordinary zombie");
            Zombie elite = scenario.mob(EntityType.ZOMBIE);
            EliteMobs.empower(elite, RandomSource.create(ELITE_SEED), ELITE_NODES);
            slay(elite, killer);
            scenario.check(elite.isDeadOrDying(), "the Constellated zombie dies");
            scenario.same(1, scenario.collect(ModItems.STAR_FRAGMENT.get()), "fragments from a Constellated zombie");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aBossPaysFragmentsOnlyOnTheFirstKill(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer killer = scenario.player();
            for (int kill = 0; kill < 2; kill++) {
                scenario.mob(EntityType.WARDEN).die(killer.damageSources().playerAttack(killer));
            }
            int reward = ServerConfig.BOSS_FRAGMENTS.get();
            scenario.same(reward, fragments(killer), "fragments after killing two wardens");
            scenario.check(EntityType.WARDEN.is(ModTags.NEVER_CONSTELLATED), "bosses are never Constellated");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aBossPaysEveryPlayerWhoFoughtIt(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer fighter = scenario.blankPlayer();
            ServerPlayer killer = scenario.blankPlayer();
            ServerPlayer bystander = scenario.blankPlayer();
            Warden warden = scenario.mob(EntityType.WARDEN);
            scenario.check(Scenario.strike(warden, fighter.damageSources().playerAttack(fighter), 1f) > 0, "the fighter wounds the warden");
            warden.die(killer.damageSources().playerAttack(killer));
            int reward = ServerConfig.BOSS_FRAGMENTS.get();
            scenario.same(reward, fragments(fighter), "fragments of the player who only wounded it");
            scenario.same(reward, fragments(killer), "fragments of the killer");
            scenario.same(0, fragments(bystander), "fragments of a player who did not fight");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void anEclipseDarkensNoonAndWaivesTheOrb(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            PlayerSkillState state = ModAttachments.state(scenario.player());
            boolean orbConfigured = ServerConfig.ORB_REQUIRED_FOR_RESET.get();
            helper.setDayTime(NOON);
            scenario.forceSky(SkyEvent.ECLIPSE);
            scenario.check(SkyEvents.isEclipse(), "the forced eclipse is active");
            scenario.check(!scenario.level().isDay(), "the eclipse makes noon as dark as night");
            scenario.check(!SkillService.economy(state).orbRequired(), "no orb is asked for during an eclipse");
            scenario.forceSky(SkyEvent.STAR_SHOWER);
            scenario.same(SkyEvent.STAR_SHOWER, SkyEvents.current(), "a star shower replaces the eclipse");
            scenario.forceSky(SkyEvent.NONE);
            scenario.same(SkyEvent.NONE, SkyEvents.current(), "the sky event stops");
            scenario.check(scenario.level().isDay(), "daylight is back");
            scenario.same(orbConfigured, SkillService.economy(state).orbRequired(), "the orb rule returns after the eclipse");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aFallenStarReturnsTheSkillToItsOwner(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer owner = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(owner, GUARDIAN_HUB, GATEKEEPER);
            int learned = state.allocations().size();
            int available = state.availablePoints();
            ItemEntity star = scenario.track(FallenStars.drop(owner).orElseThrow());
            scenario.same(learned - 1, state.allocations().size(), "skills after dropping the star");
            scenario.same(available, state.availablePoints(), "the lost skill is not refunded");
            scenario.check(state.ledgerHolds(), "ledger invariant after the drop");
            scenario.check(FallenStarItem.redeem(owner, star.getItem()), "the owner takes the star back");
            scenario.same(learned, state.allocations().size(), "skills after taking it back");
            scenario.check(state.ledgerHolds(), "ledger invariant after the recovery");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aFallenStarPaysItsPointsToAThief(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer owner = scenario.blankPlayer();
            ServerPlayer thief = scenario.blankPlayer();
            scenario.learn(owner, GUARDIAN_HUB, GATEKEEPER);
            ItemEntity star = scenario.track(FallenStars.drop(owner).orElseThrow());
            int paid = FallenStarItem.contents(star.getItem()).orElseThrow().paidCost();
            PlayerSkillState thiefState = ModAttachments.state(thief);
            scenario.check(FallenStarItem.redeem(thief, star.getItem()), "another player claims the star");
            scenario.same(paid, thiefState.availablePoints(), "points gained by the thief");
            scenario.check(thiefState.allocations().isEmpty(), "the thief learns nothing");
            scenario.check(star.getItem().isEmpty(), "the star is used up");
            scenario.check(thiefState.ledgerHolds(), "ledger invariant");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aDonationUnlocksATierForEveryone(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer donor = scenario.blankPlayer();
            ServerPlayer bystander = scenario.blankPlayer();
            long firstTier = SkillTreeManager.communityTiers().get(0).xpRequired();
            scenario.communityProgress(0);
            double healthBefore = bystander.getMaxHealth();
            scenario.communityProgress(firstTier - 50);
            PlayerExperience.set(donor, 300);
            CommunityProgress.Donation donation = CommunityTree.donate(donor, 120);
            scenario.check(donation.ok(), "the donation is accepted");
            scenario.same(120, donation.accepted(), "XP accepted");
            scenario.same(180, Scenario.experience(donor), "XP left with the donor");
            scenario.same(firstTier + 70, WorldSkillData.of(scenario.server()).communityXp(), "shared progress");
            scenario.near(healthBefore + 1, bystander.getMaxHealth(), "max health of a player who did not donate");
            scenario.communityProgress(0);
            scenario.near(healthBefore, bystander.getMaxHealth(), "max health once progress is reset");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aCompleteConstellationTakesNoMoreExperience(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer donor = scenario.blankPlayer();
            long goal = communityGoal();
            scenario.communityProgress(goal - 30);
            scenario.same(30, CommunityTree.contribute(scenario.server(), 500), "XP accepted from a machine");
            scenario.same(goal, WorldSkillData.of(scenario.server()).communityXp(), "shared progress");
            scenario.same(0, CommunityTree.remaining(scenario.server()), "XP still missing");
            PlayerExperience.set(donor, 300);
            scenario.same(RejectReason.COMMUNITY_COMPLETE, CommunityTree.donate(donor, 10).reason().orElseThrow(), "why the donation is refused");
            scenario.same(300, Scenario.experience(donor), "XP left with the donor");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void theHarvestBonusAddsOneCropToARipeHarvest(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer farmer = scenario.blankPlayer();
            scenario.same(1, harvestRipeWheat(scenario, helper, farmer), "wheat from an ordinary harvest");
            Scenario.set(farmer, ModAttributes.HARVEST_BONUS, 1.0);
            scenario.same(2, harvestRipeWheat(scenario, helper, farmer), "wheat with a certain harvest bonus");
        });
    }
}
