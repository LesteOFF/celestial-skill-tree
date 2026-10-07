package dev.nostalgiacraft.skills.gametest;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.data.WorldSkillData;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import dev.nostalgiacraft.skills.entity.FallingStar;
import dev.nostalgiacraft.skills.graph.CommunityTier;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.CommunityProgress;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.RefundService;
import dev.nostalgiacraft.skills.progression.RejectReason;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import dev.nostalgiacraft.skills.progression.XpCurve;
import dev.nostalgiacraft.skills.progression.XpMath;
import dev.nostalgiacraft.skills.registry.FallenStarItem;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.registry.StarFragmentItem;
import dev.nostalgiacraft.skills.registry.StateCodecs;
import dev.nostalgiacraft.skills.server.CommunityTree;
import dev.nostalgiacraft.skills.server.FallenStars;
import dev.nostalgiacraft.skills.server.SkillService;
import dev.nostalgiacraft.skills.server.SkyEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@GameTestHolder(NostalgiaSkills.MODID)
@PrefixGameTestTemplate(false)
public final class SkillGameTests {
    private static final String NS = NostalgiaSkills.MODID + ":";
    private static final int NOON = 6_000;
    private static final double STAR_HEIGHT = 10.0;
    private static final Vec3 STAR_TRAJECTORY = new Vec3(0.4, -0.8, 0.4);

    private SkillGameTests() {
    }

    private static SkillGraphSnapshot graph(GameTestHelper h) {
        return SkillTreeManager.graph().orElseThrow(() -> new IllegalStateException("no skill tree loaded"));
    }

    private static PlayerSkillState build(GameTestHelper h, ServerPlayer player, String hub, String... targets) {
        SkillGraphSnapshot g = graph(h);
        PlayerSkillState state = ModAttachments.state(player);
        LedgerService.grant(state, 60, 120, false);
        h.assertTrue(AllocationService.confirm(g, state, List.of(NS + hub)).ok(), "hub " + hub);
        for (String t : targets) {
            PathFinder.Route route = PathFinder.route(g, state, List.of(), NS + t).orElseThrow();
            h.assertTrue(route.valid(), "route to " + t);
            h.assertTrue(AllocationService.confirm(g, state, route.newNodes()).ok(), "confirm " + t);
        }
        EffectEngine.refresh(player);
        return state;
    }

    private static void near(GameTestHelper h, double expected, double actual, String what) {
        h.assertTrue(Math.abs(expected - actual) < 1e-4, what + ": expected " + expected + " got " + actual);
    }

    @GameTest(template = "empty")
    public static void twoPlayersHaveIndependentBalancesAndBuilds(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        ServerPlayer b = h.makeMockServerPlayerInLevel();
        SkillService.setExperience(player, 1395);
        ProgressionService.PurchaseResult result = ProgressionService.purchasePoint(ModAttachments.state(player),
                XpMath.balance(player.experienceLevel, player.experienceProgress), ProgressionService.Settings.DEFAULT);
        h.assertTrue(result.ok(), "purchase");
        SkillService.setExperience(player, result.newXpTotal());
        build(h, player, "guardian/hub", "guardian/gatekeeper");
        near(h, 29.0, player.getAttributeValue(Attributes.MAX_HEALTH), "player A max health");
        near(h, 20.0, b.getAttributeValue(Attributes.MAX_HEALTH), "player B max health");
        h.assertTrue(ModAttachments.state(b).allocations().isEmpty(), "player B has no allocations");
        h.assertTrue(ModAttachments.state(b).purchasedPoints() == 0, "player B bought nothing");
        h.assertTrue(ModAttachments.state(player).purchasedPoints() == 1, "player A bought one point");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void xpDebitKeepsLevelBarAndTotalConsistent(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        for (long start : new long[]{100, 352, 1507, 5000}) {
            SkillService.setExperience(player, start);
            long balance = XpMath.balance(player.experienceLevel, player.experienceProgress);
            h.assertTrue(balance == start, "balance " + balance + " != " + start);
            PlayerSkillState state = new PlayerSkillState();
            ProgressionService.PurchaseResult result = ProgressionService.purchasePoint(state, balance, ProgressionService.Settings.DEFAULT);
            h.assertTrue(result.ok(), "purchase from " + start);
            SkillService.setExperience(player, result.newXpTotal());
            long after = XpMath.balance(player.experienceLevel, player.experienceProgress);
            h.assertTrue(after == start - 100 && player.totalExperience == start - 100,
                    "after debit: balance " + after + ", total " + player.totalExperience + ", expected " + (start - 100));
        }
        SkillService.setExperience(player, 99);
        h.assertFalse(ProgressionService.purchasePoint(new PlayerSkillState(), XpMath.balance(player.experienceLevel, player.experienceProgress),
                ProgressionService.Settings.DEFAULT).ok(), "99 XP must not buy a point");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everySectorAppliesRealEffects(GameTestHelper h) {
        record Case(String hub, String target, Holder<Attribute> attr, double expected) {
        }
        List<Case> cases = List.of(
                new Case("duelist/hub", "duelist/measured_cut", ModAttributes.CRIT_CHANCE, 0.03),
                new Case("guardian/hub", "guardian/gatekeeper", Attributes.MAX_HEALTH, 29.0),
                new Case("arcanist/hub", "arcanist/steady_hand", ModAttributes.POTION_DURATION, 0.05 + 0.10),
                new Case("marksman/hub", "marksman/eagle_eye", ModAttributes.LONG_RANGE_DAMAGE, 0.10),
                new Case("artificer/hub", "artificer/rich_seams", ModAttributes.MINING_SPEED, -1),
                new Case("berserker/hub", "berserker/skull_splitter", ModAttributes.HEAVY_WEAPON_DAMAGE, 0.10));
        List<String> failures = new ArrayList<>();
        for (Case c : cases) {
            ServerPlayer player = h.makeMockServerPlayerInLevel();
            build(h, player, c.hub(), c.target());
            double v = player.getAttributeValue(c.attr());
            if (c.expected() < 0 ? v <= 0.10 : v < c.expected() - 0.001) {
                failures.add(c.hub() + " -> " + c.target() + ": " + v);
            }
        }
        h.assertTrue(failures.isEmpty(), "sector effects: " + failures);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recklessFuryChangesDealtAndTakenDamage(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Cow baseline = h.spawn(EntityType.COW, 1, 1, 1);
        baseline.hurt(player.damageSources().playerAttack(player), 4f);
        float baseLoss = baseline.getMaxHealth() - baseline.getHealth();
        build(h, player, "berserker/hub", "berserker/reckless_fury");
        h.assertTrue(ModAttachments.state(player).isAllocated(NS + "berserker/reckless_fury"), "keystone allocated");
        Cow target = h.spawn(EntityType.COW, 1, 1, 1);
        target.hurt(player.damageSources().playerAttack(player), 4f);
        float loss = target.getMaxHealth() - target.getHealth();
        h.assertTrue(loss > baseLoss * 1.15f && loss < baseLoss * 1.6f, "dealt " + loss + " vs baseline " + baseLoss);
        player.getAbilities().invulnerable = false;
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(h.getLevel().damageSources().fellOutOfWorld(), 4f);
        float taken = before - player.getHealth();
        h.assertTrue(Math.abs(taken - 4.4f) < 0.05f, "taken " + taken + " (expected 4.4 = 4 x 1.10)");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refreshIsIdempotentAndResetRemovesEverything(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        SkillService.reconcileAndRefresh(player);
        int signModifiers = ownedModifiers(player);
        build(h, player, "guardian/hub", "guardian/bastion");
        double hp = player.getAttributeValue(Attributes.MAX_HEALTH);
        int owned = ownedModifiers(player);
        for (int i = 0; i < 5; i++) {
            EffectEngine.refresh(player);
        }
        near(h, hp, player.getAttributeValue(Attributes.MAX_HEALTH), "max health after repeated refresh");
        h.assertTrue(ownedModifiers(player) == owned, "modifier count changed after refresh: " + ownedModifiers(player) + " vs " + owned);
        player.setHealth(player.getMaxHealth());
        PlayerSkillState state = ModAttachments.state(player);
        int spent = state.activeSpentPoints();
        int available = state.availablePoints();
        RespecService.Preview preview = RespecService.resetAll(state);
        EffectEngine.refresh(player);
        h.assertTrue(preview.refund() == spent && state.availablePoints() == available + spent, "refund exact recorded cost");
        h.assertTrue(ownedModifiers(player) == signModifiers, "only the birth sign's modifiers remain after reset");
        h.assertTrue(state.sign().isPresent(), "the birth sign survives a reset");
        near(h, 20.0, player.getMaxHealth(), "max health after reset");
        h.assertTrue(player.getHealth() <= player.getMaxHealth(), "health clamped, not above new maximum");
        h.assertTrue(state.ledgerHolds(), "ledger invariant");
        h.assertTrue(XpCurve.DEFAULT.cost(state.purchasedPoints()) == XpCurve.DEFAULT.cost(0), "curve unchanged by reset");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void singleSkillRefundRemovesItsEffectsAndItsChain(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        PlayerSkillState state = build(h, player, "guardian/hub", "guardian/bastion");
        h.assertTrue(state.allocations().size() >= 3, "fixture route is long enough: " + state.allocations().size());
        double full = player.getAttributeValue(Attributes.MAX_HEALTH);
        int allocated = state.allocations().size();
        int available = state.availablePoints();
        int spent = state.activeSpentPoints();
        List<String> order = state.orderedAllocations().stream().map(a -> a.nodeId()).toList();
        String hub = order.get(0);
        String neighbour = order.get(1);
        String leaf = order.get(order.size() - 1);
        int leafCost = state.allocations().get(leaf).paidCost();

        RefundService.Plan one = RefundService.apply(graph(h), state, leaf, true);
        EffectEngine.refresh(player);
        h.assertTrue(one.ok() && one.removed().equals(List.of(leaf)), "the last node of the route goes alone: " + one.removed());
        h.assertTrue(one.refund() == leafCost, "refund " + one.refund() + " != recorded cost " + leafCost);
        h.assertTrue(state.allocations().size() == allocated - 1 && state.availablePoints() == available + leafCost,
                "one allocation removed and exactly its points returned");
        h.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) <= full, "its contribution is gone: "
                + player.getAttributeValue(Attributes.MAX_HEALTH) + " vs " + full);
        h.assertTrue(state.ledgerHolds(), "ledger invariant after a single refund");

        player.setHealth(player.getMaxHealth());
        RefundService.Plan chain = RefundService.apply(graph(h), state, neighbour, true);
        EffectEngine.refresh(player);
        h.assertTrue(chain.ok() && chain.chained(), "the hub-adjacent node takes the rest of the branch");
        h.assertTrue(state.allocations().keySet().equals(Set.of(hub)), "only the free hub is left: "
                + state.allocations().keySet());
        h.assertTrue(state.availablePoints() == available + spent, "every paid point is back: " + state.availablePoints()
                + " expected " + (available + spent));
        h.assertTrue(state.startingRoot().filter(hub::equals).isPresent(), "the starting hub is kept");
        ServerPlayer hubOnly = h.makeMockServerPlayerInLevel();
        build(h, hubOnly, "guardian/hub");
        h.assertTrue(ownedModifiers(player) == ownedModifiers(hubOnly), "only the hub's modifiers are left: "
                + ownedModifiers(player) + " vs " + ownedModifiers(hubOnly));
        near(h, hubOnly.getMaxHealth(), player.getMaxHealth(), "max health back to the hub-only value");
        h.assertTrue(player.getHealth() <= player.getMaxHealth(), "health clamped, not above new maximum");
        h.assertTrue(state.ledgerHolds(), "ledger invariant after a chained refund");
        h.assertFalse(RefundService.apply(graph(h), state, leaf, true).ok(), "a replayed refund credits nothing");
        h.assertTrue(state.availablePoints() == available + spent, "balance unchanged by the replay");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void statePersistsThroughSerialisation(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        PlayerSkillState state = build(h, player, "marksman/hub", "marksman/patient_aim");
        var tag = StateCodecs.STATE.encodeStart(NbtOps.INSTANCE, state)
                .getOrThrow();
        PlayerSkillState back = StateCodecs.STATE.parse(NbtOps.INSTANCE, tag)
                .getOrThrow();
        h.assertTrue(back.allocations().equals(state.allocations()), "allocations survive NBT round trip");
        h.assertTrue(back.availablePoints() == state.availablePoints() && back.stateRevision() == state.stateRevision()
                && back.startingRoot().equals(state.startingRoot()), "ledger fields survive NBT round trip");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void starFragmentGrantsOnePointUpToTheLimit(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        PlayerSkillState state = ModAttachments.state(player);
        int limit = ServerConfig.POINT_LIMIT.get();
        ItemStack fragments = new ItemStack(ModItems.STAR_FRAGMENT.get(), 2);
        helper.assertTrue(StarFragmentItem.redeem(player, fragments), "first fragment is redeemed");
        helper.assertTrue(state.availablePoints() == 1 && fragments.getCount() == 1 && state.ledgerHolds(),
                "one point for one fragment: available " + state.availablePoints() + ", left " + fragments.getCount());
        LedgerService.grant(state, limit - state.totalPoints(), limit, false);
        helper.assertFalse(StarFragmentItem.redeem(player, fragments), "no fragment is redeemed at the point limit");
        helper.assertTrue(state.totalPoints() == limit && fragments.getCount() == 1, "nothing consumed at the limit");
        FallingStar star = FallingStar.launch(helper.getLevel(), player.position().add(0, STAR_HEIGHT, 0), STAR_TRAJECTORY);
        helper.assertTrue(star.isAlive() && star.getDeltaMovement().y < 0, "a star falls from above");
        ItemEntity fragment = star.land();
        helper.assertTrue(star.isRemoved() && fragment.getItem().is(ModItems.STAR_FRAGMENT.get()), "the star lands as a fragment");
        fragment.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void constellatedMonsterIsStrongerAndMarked(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        double baseHealth = zombie.getMaxHealth();
        double baseDamage = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertFalse(EliteMobs.isElite(zombie), "an ordinary zombie is not elite");
        helper.assertTrue(EliteMobs.empower(zombie, RandomSource.create(42), 8), "empowered");
        helper.assertTrue(EliteMobs.isElite(zombie) && zombie.hasCustomName(), "marked and named");
        helper.assertTrue(zombie.getMaxHealth() >= baseHealth * 1.5 - 1e-4, "at least +50% max health: " + zombie.getMaxHealth());
        helper.assertTrue(zombie.getAttributeValue(Attributes.ATTACK_DAMAGE) >= baseDamage * 1.25 - 1e-4, "at least +25% damage");
        near(helper, zombie.getMaxHealth(), zombie.getHealth(), "healed to the new maximum");
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eclipseWaivesTheOrbAndEndsCleanly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerSkillState state = ModAttachments.state(player);
        boolean orbConfigured = ServerConfig.ORB_REQUIRED_FOR_RESET.get();
        helper.setDayTime(NOON);
        SkyEvents.force(helper.getLevel().getServer(), SkyEvent.ECLIPSE);
        helper.getLevel().updateSkyBrightness();
        helper.assertTrue(SkyEvents.isEclipse(), "forced eclipse is active");
        helper.assertFalse(helper.getLevel().isDay(), "the eclipse makes noon as dark as night");
        helper.assertFalse(SkillService.economy(state).orbRequired(), "no orb is asked for during an eclipse");
        SkyEvents.force(helper.getLevel().getServer(), SkyEvent.STAR_SHOWER);
        helper.assertTrue(SkyEvents.current() == SkyEvent.STAR_SHOWER && !SkyEvents.isEclipse(), "a star shower replaces the eclipse");
        SkyEvents.force(helper.getLevel().getServer(), SkyEvent.NONE);
        helper.getLevel().updateSkyBrightness();
        helper.assertTrue(SkyEvents.current() == SkyEvent.NONE && helper.getLevel().isDay(), "the sky event stopped and daylight is back");
        helper.assertTrue(SkillService.economy(state).orbRequired() == orbConfigured, "the orb rule returns after the eclipse");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fallenStarReturnsToItsOwnerOrPaysAThief(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer thief = helper.makeMockServerPlayerInLevel();
        PlayerSkillState ownerState = build(helper, owner, "guardian/hub", "guardian/gatekeeper");
        int learned = ownerState.allocations().size();
        int available = ownerState.availablePoints();
        ItemEntity first = FallenStars.drop(owner).orElseThrow();
        helper.assertTrue(ownerState.allocations().size() == learned - 1 && ownerState.availablePoints() == available
                && ownerState.ledgerHolds(), "the skill is lost without a refund and the ledger still holds");
        helper.assertTrue(FallenStarItem.redeem(owner, first.getItem()), "the owner takes the star back");
        helper.assertTrue(ownerState.allocations().size() == learned && ownerState.ledgerHolds(), "the skill is learned again");
        first.discard();
        ItemEntity second = FallenStars.drop(owner).orElseThrow();
        int paid = FallenStarItem.contents(second.getItem()).orElseThrow().paidCost();
        PlayerSkillState thiefState = ModAttachments.state(thief);
        helper.assertTrue(FallenStarItem.redeem(thief, second.getItem()), "another player claims the star");
        helper.assertTrue(thiefState.availablePoints() == paid && thiefState.ledgerHolds() && second.getItem().isEmpty(),
                "the thief gains exactly the paid points: " + thiefState.availablePoints() + " vs " + paid);
        second.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void donationsFillTheSharedConstellationAndRewardEveryone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer donor = helper.makeMockServerPlayerInLevel();
        ServerPlayer bystander = helper.makeMockServerPlayerInLevel();
        SkillService.reconcileAndRefresh(bystander);
        double healthBefore = bystander.getMaxHealth();
        long firstTier = SkillTreeManager.communityTiers().get(0).xpRequired();
        CommunityTree.setProgress(server, firstTier - 50);
        SkillService.setExperience(donor, 300);
        CommunityProgress.Donation donation = CommunityTree.donate(donor, 120);
        long left = XpMath.balance(donor.experienceLevel, donor.experienceProgress);
        helper.assertTrue(donation.ok() && donation.accepted() == 120 && left == 180, "120 XP donated exactly, left " + left);
        helper.assertTrue(WorldSkillData.of(server).communityXp() == firstTier + 70, "progress recorded");
        near(helper, healthBefore + 1, bystander.getMaxHealth(), "the first tier rewards a player who did not donate");
        CommunityTree.setProgress(server, CommunityProgress.goal(SkillTreeManager.communityTiers().stream()
                .map(CommunityTier::xpRequired).toList()));
        helper.assertTrue(CommunityTree.donate(donor, 10).reason().orElseThrow() == RejectReason.COMMUNITY_COMPLETE,
                "nothing is taken once the constellation is complete");
        CommunityTree.setProgress(server, 0);
        near(helper, healthBefore, bystander.getMaxHealth(), "bonuses leave when progress is reset");
        helper.succeed();
    }

    private static int ownedModifiers(ServerPlayer player) {
        int n = 0;
        for (var holder : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            var inst = player.getAttribute(holder);
            if (inst == null) {
                continue;
            }
            for (AttributeModifier modifier : inst.getModifiers()) {
                if (modifier.id().getNamespace().equals(NostalgiaSkills.MODID)) {
                    n++;
                }
            }
        }
        return n;
    }
}
