package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.screen.SkillTreeScreen;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Development-only end-to-end scenario run after the fixture screenshots (same -Dnostalgiaskills.visualqa switch).
 * Uses a real singleplayer world, so every action goes through the payloads and the integrated server's
 * authoritative services. Results are written to run/nostalgiaskills_qa_ingame.txt.
 */
@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class InWorldQa {
    private record Step(String name, Runnable action, BooleanSupplier done, int timeoutTicks) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static boolean active;
    private static int step = -1;
    private static int ticks;
    private static boolean started;
    private static double healthBefore;
    private static int boughtTarget;
    private static int allocatedBeforeDeath;
    private static int purchasedBeforeSpam;
    private static int availableBeforeReload;
    private static double healthBeforeRefund;
    private static String refundTarget;
    private static int refundCost;
    private static final List<String> RESULTS = new ArrayList<>();

    private InWorldQa() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void log(String line) {
        REPORT.add(line);
        NostalgiaSkills.LOGGER.info("In-world QA: {}", line);
    }

    private static PlayerSkillState st() {
        return ClientSkillState.state().orElse(null);
    }

    private static SkillTreeScreen screen() {
        if (!(mc().screen instanceof SkillTreeScreen)) {
            mc().setScreen(new SkillTreeScreen());
        }
        return (SkillTreeScreen) mc().screen;
    }

    private static int orbs() {
        int n = 0;
        for (ItemStack s : mc().player.getInventory().items) {
            if (s.is(ModItems.ORB_OF_RECONFIGURATION.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    static void start() {
        active = true;
        ClientSkillState.reset();
        Minecraft mc = mc();
        mc.getWindow().setWindowed(1280, 720);
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        STEPS.add(new Step("create world", () -> mc().createWorldOpenFlows().createFreshLevel("nostalgiaskills_qa",
                new LevelSettings("nostalgiaskills_qa", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT),
                new WorldOptions(20261006L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen()),
                () -> mc().player != null && ClientSkillState.ready() && !ClientSkillState.isFixture(), 2400));
        STEPS.add(new Step("settle", () -> {
            healthBefore = mc().player.getAttributeValue(Attributes.MAX_HEALTH);
            log("joined world; graph nodes=" + ClientSkillState.graph().map(g -> g.nodes().size()).orElse(0)
                    + ", max health=" + healthBefore + ", available points=" + st().availablePoints());
            mc().player.connection.sendCommand("xp add @s 30 levels");
        }, () -> mc().player.experienceLevel >= 30, 200));
        STEPS.add(new Step("buy 6 points with XP", () -> {
            log("xp before purchases: level " + mc().player.experienceLevel + " progress " + mc().player.experienceProgress
                    + " (raw " + ClientSkillState.xpBalance() + ")");
            boughtTarget = 6;
        }, () -> {
            PlayerSkillState s = st();
            if (s.purchasedPoints() >= boughtTarget) {
                return true;
            }
            if (!ClientSkillState.requestInFlight()) {
                screen().qaBuy();
            }
            return false;
        }, 600));
        STEPS.add(new Step("check purchase", () -> log("after buying: purchased=" + st().purchasedPoints() + ", available="
                + st().availablePoints() + ", xp raw " + ClientSkillState.xpBalance() + " (level " + mc().player.experienceLevel
                + "), expected XP spent 100+122+148+178+212+250=1010, next cost=" + ClientSkillState.economy().nextPointCost()),
                () -> true, 5));
        STEPS.add(new Step("pending selection then cancel", () -> {
            List<String> pending = guardianRoute();
            screen().qaSetup(pending, false, false);
            log("pending selection of " + pending.size() + " nodes created (not confirmed)");
        }, () -> true, 20));
        STEPS.add(new Step("verify cancel applies nothing", () -> {
            screen().qaSetup(List.of(), false, false);
            log("after cancel: allocations=" + st().allocations().size() + ", available=" + st().availablePoints()
                    + ", max health=" + mc().player.getAttributeValue(Attributes.MAX_HEALTH));
        }, () -> true, 20));
        STEPS.add(new Step("confirm guardian route", () -> {
            List<String> pending = guardianRoute();
            screen().qaSetup(pending, false, false);
            screen().qaConfirm();
            log("confirm sent for " + pending);
        }, () -> st().isAllocated("nostalgiaskills:guardian/gatekeeper"), 400));
        STEPS.add(new Step("effects applied", () -> { }, () -> mc().player.getAttributeValue(Attributes.MAX_HEALTH) > healthBefore, 200));
        STEPS.add(new Step("report allocation", () -> log("after confirm: allocations=" + st().allocations().size()
                + ", available=" + st().availablePoints() + ", spent=" + st().activeSpentPoints() + ", max health "
                + healthBefore + " -> " + mc().player.getAttributeValue(Attributes.MAX_HEALTH) + " (expected +9: hub +2, 3x Vitality +1, Gatekeeper +4)"
                + ", ledger ok=" + st().ledgerHolds()), () -> true, 5));
        STEPS.add(new Step("screenshot stats", () -> {
            screen().qaSetup(List.of(), true, false);
            screen().qaZoom(1.0, "guardian/hub");
        }, () -> true, 30));
        STEPS.add(new Step("capture stats", () -> Screenshot.grab(mc().gameDirectory, "nostalgiaskills_qa_ingame_stats_panel_1280x720.png",
                mc().getMainRenderTarget(), m -> { }), () -> true, 10));
        STEPS.add(new Step("die", () -> {
            allocatedBeforeDeath = st().allocations().size();
            mc().setScreen(null);
            mc().player.connection.sendCommand("kill @s");
        }, () -> mc().player.isDeadOrDying(), 200));
        STEPS.add(new Step("respawn", () -> mc().player.respawn(), () -> mc().player.isAlive() && mc().player.getHealth() > 0, 400));
        STEPS.add(new Step("after respawn", () -> { }, () -> mc().player.getAttributeValue(Attributes.MAX_HEALTH) > healthBefore, 200));
        STEPS.add(new Step("report respawn", () -> log("after death+respawn: allocations " + allocatedBeforeDeath + " -> "
                + st().allocations().size() + ", max health=" + mc().player.getAttributeValue(Attributes.MAX_HEALTH)
                + ", available=" + st().availablePoints()), () -> true, 5));
        STEPS.add(new Step("dimension change", () -> mc().player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0 120 0"),
                () -> mc().level.dimension() == net.minecraft.world.level.Level.NETHER
                        && mc().player.getAttributeValue(Attributes.MAX_HEALTH) > healthBefore, 400));
        STEPS.add(new Step("report dimension", () -> {
            log("after moving to the Nether: max health=" + mc().player.getAttributeValue(Attributes.MAX_HEALTH)
                    + ", allocations=" + st().allocations().size());
            mc().player.connection.sendCommand("execute in minecraft:overworld run tp @s 0 -59 0");
        }, () -> mc().level.dimension() == net.minecraft.world.level.Level.OVERWORLD, 400));
        STEPS.add(new Step("xp for spam test", () -> mc().player.connection.sendCommand("xp add @s 1000 points"),
                () -> ClientSkillState.xpBalance() >= ClientSkillState.economy().nextPointCost(), 200));
        STEPS.add(new Step("replay and spam requests", () -> {
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.ok() ? "ok" : r.messageKey()), () -> { });
            long rev = st().stateRevision();
            purchasedBeforeSpam = st().purchasedPoints();
            java.util.UUID same = java.util.UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.nostalgiacraft.skills.network.Payloads.BuyPoint(same, rev));
            }
            for (int i = 0; i < 12; i++) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new dev.nostalgiacraft.skills.network.Payloads.BuyPoint(java.util.UUID.randomUUID(), rev));
            }
        }, () -> RESULTS.size() >= 14, 400));
        STEPS.add(new Step("report spam", () -> {
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            RESULTS.forEach(r -> counts.merge(r, 1, Integer::sum));
            log("14 rapid buy requests (2 share one request id, all expect the same state revision): results " + counts
                    + "; purchased " + purchasedBeforeSpam + " -> " + st().purchasedPoints() + " (exactly one purchase expected)"
                    + ", ledger ok=" + st().ledgerHolds());
            ClientSkillState.clearListeners();
        }, () -> true, 5));
        STEPS.add(new Step("save and quit world", () -> {
            allocatedBeforeDeath = st().allocations().size();
            purchasedBeforeSpam = st().purchasedPoints();
            // same sequence as the vanilla pause menu: tell the integrated server the owner left, then tear down
            mc().level.disconnect();
            mc().disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(
                    net.minecraft.network.chat.Component.literal("Saving world")));
        }, () -> mc().level == null && mc().getSingleplayerServer() == null, 1200));
        STEPS.add(new Step("reopen world", () -> {
            ClientSkillState.reset();
            mc().createWorldOpenFlows().openWorld("nostalgiaskills_qa", () -> log("FAILED to reopen world"));
        }, () -> mc().player != null && ClientSkillState.ready() && mc().player.getAttributeValue(Attributes.MAX_HEALTH) > healthBefore, 2400));
        STEPS.add(new Step("report restart", () -> log("after saving, quitting and reloading the world (integrated server restart): allocations "
                + allocatedBeforeDeath + " -> " + st().allocations().size() + ", purchased " + purchasedBeforeSpam + " -> "
                + st().purchasedPoints() + ", max health=" + mc().player.getAttributeValue(Attributes.MAX_HEALTH)
                + ", ledger ok=" + st().ledgerHolds()), () -> true, 5));
        STEPS.add(new Step("live prerequisite change via datapack + /reload", () -> {
            availableBeforeReload = st().availablePoints();
            allocatedBeforeDeath = st().allocations().size();
            try {
                java.nio.file.Path pack = mc().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                        .resolve("qa_reconcile");
                java.nio.file.Path node = pack.resolve("data/nostalgiaskills/skill_nodes/guardian/gatekeeper.json");
                Files.createDirectories(node.getParent());
                Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"QA: gatekeeper now requires bastion\"}}");
                com.google.gson.JsonObject json;
                try (var in = InWorldQa.class.getResourceAsStream("/data/nostalgiaskills/skill_nodes/guardian/gatekeeper.json")) {
                    json = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
                com.google.gson.JsonArray all = new com.google.gson.JsonArray();
                all.add("nostalgiaskills:guardian/bastion");
                json.getAsJsonObject("requirements").add("all_of", all);
                Files.writeString(node, json.toString());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            mc().player.connection.sendCommand("reload");
        }, () -> !st().isAllocated("nostalgiaskills:guardian/gatekeeper")
                && mc().player.getAttributeValue(Attributes.MAX_HEALTH) < 29, 1200));
        STEPS.add(new Step("report reconcile", () -> log("after /reload with Gatekeeper now requiring Bastion (graph revision "
                + ClientSkillState.graphRevision() + "): allocations " + allocatedBeforeDeath + " -> " + st().allocations().size()
                + ", available " + availableBeforeReload + " -> " + st().availablePoints() + " (recorded price 2 refunded once)"
                + ", max health=" + mc().player.getAttributeValue(Attributes.MAX_HEALTH) + " (expected 25), ledger ok=" + st().ledgerHolds()),
                () -> true, 5));
        STEPS.add(new Step("give orb", () -> mc().player.connection.sendCommand("give @s nostalgiaskills:orb_of_reconfiguration 1"),
                () -> orbs() == 1, 200));
        STEPS.add(new Step("reset with orb", () -> {
            log("before reset: orbs=" + orbs() + ", allocations=" + st().allocations().size() + ", available=" + st().availablePoints()
                    + ", purchased=" + st().purchasedPoints());
            screen().openResetPanel();
            screen().qaReset();
        }, () -> st().allocations().isEmpty(), 400));
        STEPS.add(new Step("after reset", () -> { }, () -> orbs() == 0 && mc().player.getAttributeValue(Attributes.MAX_HEALTH) == healthBefore, 200));
        STEPS.add(new Step("report reset", () -> log("after reset: orbs=" + orbs() + ", allocations=" + st().allocations().size()
                + ", available=" + st().availablePoints() + ", purchased=" + st().purchasedPoints() + ", next cost="
                + ClientSkillState.economy().nextPointCost() + " (curve not restarted: purchased stays " + purchasedBeforeSpam + "), max health="
                + mc().player.getAttributeValue(Attributes.MAX_HEALTH) + ", starting root free again=" + st().startingRoot().isEmpty()
                + ", ledger ok=" + st().ledgerHolds()), () -> true, 5));
        // ---- single-skill refunds, after the reset, on a fresh build paid for with an administrative grant
        STEPS.add(new Step("grant points for the refund test",
                () -> mc().player.connection.sendCommand("nostalgiaskills points add @s 10"),
                () -> st().availablePoints() >= 10, 200));
        STEPS.add(new Step("rebuild a guardian route", () -> {
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.messageKey() + " " + r.detail()), () -> { });
            List<String> pending = routeTo("guardian/vitality_3");
            screen().qaSetup(pending, false, false);
            screen().qaConfirm();
            log("refund test: confirm sent for " + pending.size() + " nodes: " + pending);
        }, () -> {
            if (st().allocations().size() >= 4) {
                ClientSkillState.clearListeners();
                log("refund test: build ready with " + st().allocations().size() + " allocations, server said " + RESULTS);
                return true;
            }
            if (RESULTS.stream().anyMatch(r -> r.startsWith("error.")) && !ClientSkillState.requestInFlight()) {
                log("refund test: confirm rejected with " + RESULTS + "; retrying");
                RESULTS.clear();
                screen().qaSetup(routeTo("guardian/vitality_3"), false, false);
                screen().qaConfirm();
            }
            return false;
        }, 400));
        STEPS.add(new Step("refund one leaf skill", () -> {
            availableBeforeReload = st().availablePoints();
            allocatedBeforeDeath = st().allocations().size();
            healthBeforeRefund = mc().player.getAttributeValue(Attributes.MAX_HEALTH);
            refundTarget = st().orderedAllocations().get(allocatedBeforeDeath - 1).nodeId();
            refundCost = st().allocations().get(refundTarget).paidCost();
            log("refunding the last-acquired skill " + refundTarget + " (paid " + refundCost + "); preview says "
                    + screen().qaRefundPreview(refundTarget));
            screen().qaRefund(refundTarget);
            screen().qaConfirmRefund();
        }, () -> !st().isAllocated(refundTarget), 400));
        STEPS.add(new Step("report leaf refund", () -> log("after refunding one skill: allocations "
                + allocatedBeforeDeath + " -> " + st().allocations().size() + ", available " + availableBeforeReload
                + " -> " + st().availablePoints() + " (recorded cost " + refundCost + " returned), max health "
                + healthBeforeRefund + " -> " + mc().player.getAttributeValue(Attributes.MAX_HEALTH)
                + ", purchased stays " + st().purchasedPoints() + ", ledger ok=" + st().ledgerHolds()), () -> true, 5));
        STEPS.add(new Step("refund a skill the rest of the branch depends on", () -> {
            availableBeforeReload = st().availablePoints();
            allocatedBeforeDeath = st().allocations().size();
            refundTarget = st().orderedAllocations().get(1).nodeId();
            refundCost = st().activeSpentPoints();
            log("refunding " + refundTarget + " next to the hub; preview says " + screen().qaRefundPreview(refundTarget));
            screen().qaRefund(refundTarget);
            screen().qaConfirmRefund();
        }, () -> st().allocations().size() == 1, 400));
        STEPS.add(new Step("report chain refund", () -> log("after refunding the hub-adjacent skill: allocations "
                + allocatedBeforeDeath + " -> " + st().allocations().size() + " (only the free hub "
                + st().startingRoot().orElse("?") + " left), available " + availableBeforeReload + " -> "
                + st().availablePoints() + " (all " + refundCost + " paid points back), max health="
                + mc().player.getAttributeValue(Attributes.MAX_HEALTH) + ", ledger ok=" + st().ledgerHolds()),
                () -> true, 5));
        STEPS.add(new Step("the starting hub itself is not refundable", () -> {
            String hub = st().startingRoot().orElseThrow();
            log("asking to refund the starting hub " + hub + ": " + screen().qaRefundPreview(hub));
        }, () -> true, 5));
        STEPS.add(new Step("finish", InWorldQa::finish, () -> true, 5));
        next();
    }

    private static List<String> guardianRoute() {
        SkillGraphSnapshot g = ClientSkillState.graph().orElseThrow();
        PlayerSkillState tmp = st().copy();
        String hub = "nostalgiaskills:guardian/hub";
        AllocationService.confirm(g, tmp, List.of(hub));
        List<String> out = new ArrayList<>();
        out.add(hub);
        PathFinder.route(g, tmp, List.of(), "nostalgiaskills:guardian/gatekeeper").ifPresent(r -> out.addAll(r.newNodes()));
        return out;
    }

    /** Guardian hub plus the cheapest valid routes to each target, skipping any route the rules cannot resolve. */
    private static List<String> routeTo(String... targets) {
        SkillGraphSnapshot g = ClientSkillState.graph().orElseThrow();
        PlayerSkillState tmp = st().copy();
        String hub = "nostalgiaskills:guardian/hub";
        List<String> out = new ArrayList<>();
        if (tmp.startingRoot().isEmpty()) {
            AllocationService.confirm(g, tmp, List.of(hub));
            out.add(hub);
        }
        for (String target : targets) {
            PathFinder.route(g, tmp, List.of(), "nostalgiaskills:" + target)
                    .filter(PathFinder.Route::valid)
                    .ifPresentOrElse(r -> {
                        out.addAll(r.newNodes());
                        AllocationService.confirm(g, tmp, r.newNodes());
                    }, () -> log("refund test: no valid route to " + target + " (skipped)"));
        }
        return out;
    }

    private static void next() {
        step++;
        ticks = 0;
        started = false;
    }

    private static void finish() {
        try {
            Files.write(mc().gameDirectory.toPath().resolve("nostalgiaskills_qa_ingame.txt"), REPORT);
        } catch (IOException e) {
            NostalgiaSkills.LOGGER.error("could not write QA report", e);
        }
        NostalgiaSkills.LOGGER.info("In-world QA: finished");
        active = false;
        mc().stop();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!active || step >= STEPS.size()) {
            return;
        }
        Step s = STEPS.get(step);
        try {
            if (!started) {
                started = true;
                s.action().run();
                return;
            }
            ticks++;
            if (s.done().getAsBoolean()) {
                next();
            } else if (ticks > s.timeoutTicks()) {
                log("FAILED (timeout): " + s.name());
                step = STEPS.size() - 2; // jump to finish
                next();
            }
        } catch (Exception ex) {
            log("FAILED (exception) in " + s.name() + ": " + ex);
            NostalgiaSkills.LOGGER.error("In-world QA step failed", ex);
            step = STEPS.size() - 2;
            next();
        }
    }
}
