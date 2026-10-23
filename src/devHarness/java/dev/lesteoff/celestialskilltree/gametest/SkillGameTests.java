package dev.lesteoff.celestialskilltree.gametest;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.data.GeneratedTree;
import dev.lesteoff.celestialskilltree.effects.CompiledBuild;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.effects.StolenNodes;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.progression.Allocation;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.PathFinder;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.ProgressionService;
import dev.lesteoff.celestialskilltree.progression.RefundService;
import dev.lesteoff.celestialskilltree.progression.RejectReason;
import dev.lesteoff.celestialskilltree.progression.RespecService;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.registry.ModAttributes;
import dev.lesteoff.celestialskilltree.registry.StateCodecs;
import dev.lesteoff.celestialskilltree.server.ExtraSign;
import dev.lesteoff.celestialskilltree.server.PlayerExperience;
import dev.lesteoff.celestialskilltree.server.SkillService;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@GameTestHolder(CelestialSkillTree.MODID)
@PrefixGameTestTemplate(false)
public final class SkillGameTests {
    private static final String GUARDIAN_HUB = "guardian/hub";
    private static final String GATEKEEPER = "guardian/gatekeeper";
    private static final String BASTION = "guardian/bastion";
    private static final String LAYERED_PLATES = "guardian/layered_plates";
    private static final String BERSERKER_HUB = "berserker/hub";
    private static final String CONTROLLED_RAGE = "berserker/controlled_rage";
    private static final String BLIND_RAGE = "berserker/blind_rage";
    private static final String EYE_OF_THE_STORM = "berserker/eye_of_the_storm";
    private static final double ARMOR_PER_PLATE_RANK = 1.0;
    private static final double BASE_HEALTH = 20.0;
    private static final int FIRST_POINT_COST = 100;
    private static final int STOLEN_SECONDS = 30;
    private static final int REPEATED_REFRESHES = 5;

    private record ClassRoute(String hub, String target, Holder<Attribute> attribute, double minimumGain) {
    }

    private static final List<ClassRoute> CLASS_ROUTES = List.of(
            new ClassRoute("duelist/hub", "duelist/measured_cut", ModAttributes.CRIT_CHANCE, 0.03),
            new ClassRoute(GUARDIAN_HUB, GATEKEEPER, Attributes.MAX_HEALTH, 6.0),
            new ClassRoute("arcanist/hub", "arcanist/steady_hand", ModAttributes.POTION_DURATION, 0.15),
            new ClassRoute("marksman/hub", "marksman/eagle_eye", ModAttributes.LONG_RANGE_DAMAGE, 0.10),
            new ClassRoute("artificer/hub", "artificer/rich_seams", ModAttributes.MINING_SPEED, 0.15),
            new ClassRoute("berserker/hub", "berserker/skull_splitter", ModAttributes.HEAVY_WEAPON_DAMAGE, 0.10));

    private SkillGameTests() {
    }

    private static ProgressionService.PurchaseResult buyPoint(ServerPlayer player, PlayerSkillState state) {
        ProgressionService.PurchaseResult result = ProgressionService.purchasePoint(state, Scenario.experience(player),
                ProgressionService.Settings.DEFAULT);
        if (result.ok()) {
            PlayerExperience.set(player, result.newXpTotal());
        }
        return result;
    }

    private static List<String> learnedOrder(PlayerSkillState state) {
        return state.orderedAllocations().stream().map(Allocation::nodeId).toList();
    }

    private static RefundService.Plan refund(Scenario scenario, ServerPlayer player, String nodeId) {
        RefundService.Plan plan = RefundService.apply(scenario.graph(), ModAttachments.state(player), nodeId, true);
        EffectEngine.refresh(player);
        return plan;
    }

    private static double totalBonus(ServerPlayer player) {
        return EffectEngine.build(player).attributeSums().values().stream().mapToDouble(Math::abs).sum();
    }

    @GameTest(template = Scenario.EMPTY)
    public static void playersHaveIndependentBalancesAndBuilds(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer buyer = scenario.blankPlayer();
            ServerPlayer bystander = scenario.blankPlayer();
            PlayerExperience.set(buyer, 1395);
            scenario.check(buyPoint(buyer, ModAttachments.state(buyer)).ok(), "the buyer affords a point");
            scenario.learn(buyer, GUARDIAN_HUB, GATEKEEPER);
            scenario.atLeast(26.0, buyer.getMaxHealth(), "buyer max health with the hub and Gatekeeper");
            scenario.same(1, ModAttachments.state(buyer).purchasedPoints(), "points bought by the buyer");
            scenario.near(BASE_HEALTH, bystander.getMaxHealth(), "bystander max health");
            scenario.check(ModAttachments.state(bystander).allocations().isEmpty(), "the bystander learned nothing");
            scenario.same(0, ModAttachments.state(bystander).purchasedPoints(), "points bought by the bystander");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void buyingAPointDebitsLevelBarAndTotalTogether(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            for (long start : new long[]{100, 352, 1507, 5000}) {
                PlayerExperience.set(player, start);
                scenario.same(start, Scenario.experience(player), "balance after setting " + start);
                scenario.check(buyPoint(player, new PlayerSkillState()).ok(), "purchase from " + start);
                scenario.same(start - FIRST_POINT_COST, Scenario.experience(player), "balance after buying from " + start);
                scenario.same(start - FIRST_POINT_COST, player.totalExperience, "total experience after buying from " + start);
            }
            PlayerExperience.set(player, FIRST_POINT_COST - 1);
            scenario.check(!buyPoint(player, new PlayerSkillState()).ok(), "one XP short must not buy a point");
            scenario.same(FIRST_POINT_COST - 1, Scenario.experience(player), "a refused purchase takes nothing");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void everyClassRouteAppliesItsAttribute(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            for (ClassRoute route : CLASS_ROUTES) {
                ServerPlayer player = scenario.blankPlayer();
                double before = Scenario.treeValue(player, route.attribute());
                scenario.learn(player, route.hub(), route.target());
                scenario.atLeast(before + route.minimumGain(), Scenario.treeValue(player, route.attribute()),
                        route.hub() + " -> " + route.target());
            }
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aSecondRankOfARankedNotableAppliesItsEffectAgain(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(player, GUARDIAN_HUB, LAYERED_PLATES);
            double oneRank = player.getAttributeValue(Attributes.ARMOR);
            double flatArmorBefore = flatArmor(scenario, state);
            scenario.check(AllocationService.confirm(scenario.graph(), state, List.of(Scenario.id(LAYERED_PLATES))).ok(),
                    "the second rank of Layered Plates is accepted");
            EffectEngine.refresh(player);
            scenario.same(2, state.rank(Scenario.id(LAYERED_PLATES)), "rank of Layered Plates");
            scenario.near(flatArmorBefore + ARMOR_PER_PLATE_RANK, flatArmor(scenario, state), "flat armor from the tree after the second rank");
            scenario.atLeast(oneRank + ARMOR_PER_PLATE_RANK, player.getAttributeValue(Attributes.ARMOR), "armor after the second rank");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void learningOneKeystoneOfAnExclusivePairShutsOutTheOther(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(player, BERSERKER_HUB, CONTROLLED_RAGE);
            AllocationService.BatchResult partner = AllocationService.confirm(scenario.graph(), state, List.of(Scenario.id(BLIND_RAGE)));
            scenario.check(!partner.ok(), "Blind Rage is refused once Controlled Rage is learned");
            scenario.same(Optional.of(RejectReason.EXCLUSIVE_CONFLICT), partner.rejection().map(AllocationService.Rejection::reason),
                    "why Blind Rage is refused");
            scenario.check(!state.isAllocated(Scenario.id(BLIND_RAGE)), "Blind Rage stays unlearned");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aBlackHoleMakesTheNeighboursItsOwnerHasCountHalfAgain(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            SkillGraphSnapshot graph = scenario.graph();
            String blackHole = Scenario.id(EYE_OF_THE_STORM);
            PlayerSkillState state = scenario.learn(scenario.blankPlayer(), BERSERKER_HUB);
            List<String> way = PathFinder.route(graph, state, List.of(), blackHole).orElseThrow().newNodes().stream()
                    .filter(id -> !id.equals(blackHole)).toList();
            scenario.confirm(state, way, "the way to the black hole");
            Map<CompiledBuild.AttributeKey, Double> expected = new HashMap<>(CompiledBuild.compile(graph, state).attributeSums());
            scenario.confirm(state, List.of(blackHole), EYE_OF_THE_STORM);
            addAttributes(expected, graph.nodes().get(blackHole), 1, 1);
            double extraShare = graph.nodes().get(blackHole).traits().neighbourMultiplier() - 1;
            graph.neighbours(blackHole).stream().filter(state::isAllocated)
                    .forEach(neighbour -> addAttributes(expected, graph.nodes().get(neighbour), extraShare, state.rank(neighbour)));
            Map<CompiledBuild.AttributeKey, Double> after = CompiledBuild.compile(graph, state).attributeSums();
            expected.forEach((key, value) -> scenario.near(value, after.getOrDefault(key, 0.0), key.id() + " with the black hole"));
        });
    }

    private static void addAttributes(Map<CompiledBuild.AttributeKey, Double> sums, SkillNodeDefinition node, double share, int ranks) {
        node.effects().stream().filter(SkillEffect.Attribute.class::isInstance).map(SkillEffect.Attribute.class::cast)
                .forEach(effect -> sums.merge(CompiledBuild.sumKey(effect.attribute(), effect.operation()), effect.amount() * share * ranks,
                        Double::sum));
    }

    @GameTest(template = Scenario.EMPTY)
    public static void regeneratingTheTreeKeepsItValidAndKeepsLearnedSkillsThatStayInIt(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(player, GUARDIAN_HUB, LAYERED_PLATES);
            GeneratedTree.regenerateFor(helper.getLevel().getServer());
            SkillService.resync(player);
            scenario.check(scenario.graph().contains(Scenario.id(LAYERED_PLATES)), "the regenerated tree offers Layered Plates");
            scenario.check(state.isAllocated(Scenario.id(LAYERED_PLATES)), "a learned skill that stays in the tree is kept");
            scenario.check(state.ledgerHolds(), "points still add up after regenerating");
        });
    }

    private static double flatArmor(Scenario scenario, PlayerSkillState state) {
        return CompiledBuild.compile(scenario.graph(), state).attributeSums()
                .getOrDefault(CompiledBuild.sumKey("minecraft:generic.armor", "add_value"), 0.0);
    }

    @GameTest(template = Scenario.EMPTY)
    public static void loginAssignsABirthSignThatNeverChanges(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.player();
            PlayerSkillState state = ModAttachments.state(player);
            Optional<String> sign = state.sign();
            long revision = state.stateRevision();
            scenario.check(sign.isPresent(), "a birth sign is assigned at login");
            scenario.check(Scenario.treeModifiers(player) > 0, "the sign already changes an attribute");
            SkillService.reconcileAndRefresh(player);
            scenario.same(sign, state.sign(), "birth sign after another reconciliation");
            scenario.same(revision, state.stateRevision(), "state revision after another reconciliation");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void theExtraSignAddsTheOppositeSignWhileGranted(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.player();
            double bornWith = totalBonus(player);
            ExtraSign.setGranted(player, true);
            scenario.check(ExtraSign.isActive(player), "the extra sign is active once granted");
            scenario.check(totalBonus(player) > bornWith, "a second sign adds bonuses: " + totalBonus(player) + " vs " + bornWith);
            ExtraSign.setGranted(player, false);
            scenario.near(bornWith, totalBonus(player), "bonuses after the extra sign is taken away");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void refreshingAgainChangesNothing(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            scenario.learn(player, GUARDIAN_HUB, BASTION);
            double health = player.getMaxHealth();
            int modifiers = Scenario.treeModifiers(player);
            for (int refresh = 0; refresh < REPEATED_REFRESHES; refresh++) {
                EffectEngine.refresh(player);
            }
            scenario.near(health, player.getMaxHealth(), "max health after repeated refreshes");
            scenario.same(modifiers, Scenario.treeModifiers(player), "modifiers after repeated refreshes");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aFullResetRefundsEverythingAndKeepsTheBirthSign(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.player();
            double healthBefore = player.getMaxHealth();
            int signModifiers = Scenario.treeModifiers(player);
            PlayerSkillState state = scenario.learn(player, GUARDIAN_HUB, BASTION);
            player.setHealth(player.getMaxHealth());
            int spent = state.activeSpentPoints();
            int available = state.availablePoints();
            int purchased = state.purchasedPoints();
            RespecService.Preview reset = RespecService.resetAll(state);
            EffectEngine.refresh(player);
            scenario.same(spent, reset.refund(), "points refunded by the reset");
            scenario.same(available + spent, state.availablePoints(), "available points after the reset");
            scenario.same(purchased, state.purchasedPoints(), "the price curve does not restart");
            scenario.check(state.allocations().isEmpty() && state.startingRoot().isEmpty(), "the build is empty");
            scenario.check(state.sign().isPresent(), "the birth sign survives the reset");
            scenario.same(signModifiers, Scenario.treeModifiers(player), "only the birth sign's modifiers remain");
            scenario.near(healthBefore, player.getMaxHealth(), "max health after the reset");
            scenario.check(player.getHealth() <= player.getMaxHealth(), "health is clamped to the new maximum");
            scenario.check(state.ledgerHolds(), "ledger invariant");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void refundingTheNewestSkillRemovesOnlyThatSkill(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(player, GUARDIAN_HUB, BASTION);
            List<String> order = learnedOrder(state);
            String leaf = order.get(order.size() - 1);
            int leafCost = state.allocations().get(leaf).paidCost();
            int available = state.availablePoints();
            double health = player.getMaxHealth();
            RefundService.Plan plan = refund(scenario, player, leaf);
            scenario.check(plan.ok(), "the refund is accepted");
            scenario.same(List.of(leaf), plan.removed(), "skills removed");
            scenario.same(leafCost, plan.refund(), "points refunded");
            scenario.same(order.subList(0, order.size() - 1), learnedOrder(state), "skills still learned");
            scenario.same(available + leafCost, state.availablePoints(), "available points");
            scenario.check(player.getMaxHealth() <= health, "its bonus is gone: " + player.getMaxHealth() + " vs " + health);
            scenario.check(state.ledgerHolds(), "ledger invariant");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void refundingNextToTheHubTakesTheWholeBranch(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(player, GUARDIAN_HUB, BASTION);
            List<String> order = learnedOrder(state);
            scenario.check(order.size() >= 3, "the fixture route is long enough: " + order);
            String hub = order.get(0);
            int refundable = state.availablePoints() + state.activeSpentPoints();
            RefundService.Plan plan = refund(scenario, player, order.get(1));
            scenario.check(plan.ok() && plan.chained(), "the hub-adjacent skill takes its branch along");
            scenario.same(Set.of(hub), state.allocations().keySet(), "skills still learned");
            scenario.same(refundable, state.availablePoints(), "every paid point is back");
            scenario.same(Optional.of(hub), state.startingRoot(), "the starting hub is kept");
            ServerPlayer hubOnly = scenario.blankPlayer();
            scenario.learn(hubOnly, GUARDIAN_HUB);
            scenario.same(Scenario.treeModifiers(hubOnly), Scenario.treeModifiers(player), "modifiers left");
            scenario.near(hubOnly.getMaxHealth(), player.getMaxHealth(), "max health back to the hub-only value");
            scenario.check(player.getHealth() <= player.getMaxHealth(), "health is clamped to the new maximum");
            scenario.check(!refund(scenario, player, order.get(order.size() - 1)).ok(), "a replayed refund is rejected");
            scenario.same(refundable, state.availablePoints(), "balance after the replay");
            scenario.check(state.ledgerHolds(), "ledger invariant");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aStolenSkillGoesSilentButStaysLearned(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(player, GUARDIAN_HUB, GATEKEEPER);
            List<String> order = learnedOrder(state);
            String newest = order.get(order.size() - 1);
            double bonus = totalBonus(player);
            int available = state.availablePoints();
            scenario.check(StolenNodes.stealNewest(player, STOLEN_SECONDS), "a skill is stolen");
            scenario.same(Set.of(newest), StolenNodes.of(player), "stolen skills");
            scenario.check(totalBonus(player) < bonus, "its bonus is silenced: " + totalBonus(player) + " vs " + bonus);
            scenario.check(state.isAllocated(newest), "the stolen skill is still learned");
            scenario.same(available, state.availablePoints(), "no points move");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void theBuildSurvivesBeingSaved(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.player();
            PlayerSkillState state = scenario.learn(player, "marksman/hub", "marksman/patient_aim");
            Tag saved = StateCodecs.STATE.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
            PlayerSkillState loaded = StateCodecs.STATE.parse(NbtOps.INSTANCE, saved).getOrThrow();
            scenario.same(state.allocations(), loaded.allocations(), "allocations");
            scenario.same(state.availablePoints(), loaded.availablePoints(), "available points");
            scenario.same(state.stateRevision(), loaded.stateRevision(), "state revision");
            scenario.same(state.startingRoot(), loaded.startingRoot(), "starting root");
            scenario.same(state.sign(), loaded.sign(), "birth sign");
        });
    }
}
