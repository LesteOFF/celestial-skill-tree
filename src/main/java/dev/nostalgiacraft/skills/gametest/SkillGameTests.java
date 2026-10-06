package dev.nostalgiacraft.skills.gametest;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.ProgressionService;
import dev.nostalgiacraft.skills.progression.RespecService;
import dev.nostalgiacraft.skills.progression.XpCurve;
import dev.nostalgiacraft.skills.progression.XpMath;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import dev.nostalgiacraft.skills.server.SkillService;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game tests run by {@code ./gradlew runGameTestServer} on a real server with mock players. They exercise the
 * Minecraft-facing layer (attachments, attribute modifiers, damage events, XP fields) on top of the unit-tested domain.
 */
@GameTestHolder(NostalgiaSkills.MODID)
@PrefixGameTestTemplate(false)
public final class SkillGameTests {
    private static final String NS = NostalgiaSkills.MODID + ":";

    private SkillGameTests() {
    }

    private static SkillGraphSnapshot graph(GameTestHelper h) {
        return SkillTreeManager.graph().orElseThrow(() -> new IllegalStateException("no skill tree loaded"));
    }

    /** Gives points administratively and allocates hub + cheapest routes to the targets. */
    private static PlayerSkillState build(GameTestHelper h, ServerPlayer p, String hub, String... targets) {
        SkillGraphSnapshot g = graph(h);
        PlayerSkillState s = ModAttachments.state(p);
        LedgerService.grant(s, 60, 120, false);
        h.assertTrue(AllocationService.confirm(g, s, List.of(NS + hub)).ok(), "hub " + hub);
        for (String t : targets) {
            PathFinder.Route r = PathFinder.route(g, s, List.of(), NS + t).orElseThrow();
            h.assertTrue(r.valid(), "route to " + t);
            h.assertTrue(AllocationService.confirm(g, s, r.newNodes()).ok(), "confirm " + t);
        }
        EffectEngine.refresh(p);
        return s;
    }

    private static void near(GameTestHelper h, double expected, double actual, String what) {
        h.assertTrue(Math.abs(expected - actual) < 1e-4, what + ": expected " + expected + " got " + actual);
    }

    @GameTest(template = "empty")
    public static void twoPlayersHaveIndependentBalancesAndBuilds(GameTestHelper h) {
        ServerPlayer a = h.makeMockServerPlayerInLevel();
        ServerPlayer b = h.makeMockServerPlayerInLevel();
        SkillService.setExperience(a, 1395);
        ProgressionService.PurchaseResult r = ProgressionService.purchasePoint(ModAttachments.state(a),
                XpMath.balance(a.experienceLevel, a.experienceProgress), ProgressionService.Settings.DEFAULT);
        h.assertTrue(r.ok(), "purchase");
        SkillService.setExperience(a, r.newXpTotal());
        build(h, a, "guardian/hub", "guardian/gatekeeper");
        near(h, 29.0, a.getAttributeValue(Attributes.MAX_HEALTH), "player A max health");
        near(h, 20.0, b.getAttributeValue(Attributes.MAX_HEALTH), "player B max health");
        h.assertTrue(ModAttachments.state(b).allocations().isEmpty(), "player B has no allocations");
        h.assertTrue(ModAttachments.state(b).purchasedPoints() == 0, "player B bought nothing");
        h.assertTrue(ModAttachments.state(a).purchasedPoints() == 1, "player A bought one point");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void xpDebitKeepsLevelBarAndTotalConsistent(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        for (long start : new long[]{100, 352, 1507, 5000}) {
            SkillService.setExperience(p, start);
            long balance = XpMath.balance(p.experienceLevel, p.experienceProgress);
            h.assertTrue(balance == start, "balance " + balance + " != " + start);
            PlayerSkillState s = new PlayerSkillState();
            ProgressionService.PurchaseResult r = ProgressionService.purchasePoint(s, balance, ProgressionService.Settings.DEFAULT);
            h.assertTrue(r.ok(), "purchase from " + start);
            SkillService.setExperience(p, r.newXpTotal());
            long after = XpMath.balance(p.experienceLevel, p.experienceProgress);
            h.assertTrue(after == start - 100 && p.totalExperience == start - 100,
                    "after debit: balance " + after + ", total " + p.totalExperience + ", expected " + (start - 100));
        }
        SkillService.setExperience(p, 99);
        h.assertFalse(ProgressionService.purchasePoint(new PlayerSkillState(), XpMath.balance(p.experienceLevel, p.experienceProgress),
                ProgressionService.Settings.DEFAULT).ok(), "99 XP must not buy a point");
        h.succeed();
    }

    /** At least one real, measurable effect per sector (hub + first notable or keystone). */
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
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            build(h, p, c.hub(), c.target());
            double v = p.getAttributeValue(c.attr());
            // at least the hub + target contribution (route minors may add more of the same stat)
            if (c.expected() < 0 ? v <= 0.10 : v < c.expected() - 0.001) {
                failures.add(c.hub() + " -> " + c.target() + ": " + v);
            }
        }
        h.assertTrue(failures.isEmpty(), "sector effects: " + failures);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recklessFuryChangesDealtAndTakenDamage(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Cow baseline = h.spawn(EntityType.COW, 1, 1, 1);
        baseline.hurt(p.damageSources().playerAttack(p), 4f);
        float baseLoss = baseline.getMaxHealth() - baseline.getHealth();
        build(h, p, "berserker/hub", "berserker/reckless_fury");
        h.assertTrue(ModAttachments.state(p).isAllocated(NS + "berserker/reckless_fury"), "keystone allocated");
        Cow target = h.spawn(EntityType.COW, 1, 1, 1);
        target.hurt(p.damageSources().playerAttack(p), 4f);
        float loss = target.getMaxHealth() - target.getHealth();
        // +20% from the keystone plus any melee/weapon bonuses on the route (none apply to an unarmed hit)
        h.assertTrue(loss > baseLoss * 1.15f && loss < baseLoss * 1.6f, "dealt " + loss + " vs baseline " + baseLoss);
        p.getAbilities().invulnerable = false;   // mock players are creative; the drawback must be observable
        p.invulnerableTime = 0;
        float before = p.getHealth();
        p.hurt(h.getLevel().damageSources().fellOutOfWorld(), 4f);   // bypasses spawn invulnerability
        float taken = before - p.getHealth();
        h.assertTrue(Math.abs(taken - 4.4f) < 0.05f, "taken " + taken + " (expected 4.4 = 4 x 1.10)");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refreshIsIdempotentAndResetRemovesEverything(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        build(h, p, "guardian/hub", "guardian/bastion");
        double hp = p.getAttributeValue(Attributes.MAX_HEALTH);
        int owned = ownedModifiers(p);
        for (int i = 0; i < 5; i++) {
            EffectEngine.refresh(p);
        }
        near(h, hp, p.getAttributeValue(Attributes.MAX_HEALTH), "max health after repeated refresh");
        h.assertTrue(ownedModifiers(p) == owned, "modifier count changed after refresh: " + ownedModifiers(p) + " vs " + owned);
        p.setHealth(p.getMaxHealth());
        PlayerSkillState s = ModAttachments.state(p);
        int spent = s.activeSpentPoints();
        int available = s.availablePoints();
        RespecService.Preview pv = RespecService.resetAll(s);
        EffectEngine.refresh(p);
        h.assertTrue(pv.refund() == spent && s.availablePoints() == available + spent, "refund exact recorded cost");
        h.assertTrue(ownedModifiers(p) == 0, "no tree modifiers after reset");
        near(h, 20.0, p.getMaxHealth(), "max health after reset");
        h.assertTrue(p.getHealth() <= p.getMaxHealth(), "health clamped, not above new maximum");
        h.assertTrue(s.ledgerHolds(), "ledger invariant");
        h.assertTrue(XpCurve.DEFAULT.cost(s.purchasedPoints()) == XpCurve.DEFAULT.cost(0), "curve unchanged by reset");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void statePersistsThroughSerialisation(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        PlayerSkillState s = build(h, p, "marksman/hub", "marksman/patient_aim");
        var tag = dev.nostalgiacraft.skills.registry.StateCodecs.STATE.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s)
                .getOrThrow();
        PlayerSkillState back = dev.nostalgiacraft.skills.registry.StateCodecs.STATE.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag)
                .getOrThrow();
        h.assertTrue(back.allocations().equals(s.allocations()), "allocations survive NBT round trip");
        h.assertTrue(back.availablePoints() == s.availablePoints() && back.stateRevision() == s.stateRevision()
                && back.startingRoot().equals(s.startingRoot()), "ledger fields survive NBT round trip");
        h.succeed();
    }

    private static int ownedModifiers(ServerPlayer p) {
        int n = 0;
        for (var holder : net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            var inst = p.getAttribute(holder);
            if (inst == null) {
                continue;
            }
            for (AttributeModifier m : inst.getModifiers()) {
                if (m.id().getNamespace().equals(NostalgiaSkills.MODID)) {
                    n++;
                }
            }
        }
        return n;
    }
}
