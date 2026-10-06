package dev.nostalgiacraft.skills.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.screen.SkillTreeScreen;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillSign;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class InWorldQa {
    private record Step(String name, Runnable action, BooleanSupplier done, int timeoutTicks) {
    }

    private static final int PAUSE_SAVE_TICKS = 60;
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

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static void log(String line) {
        REPORT.add(line);
        NostalgiaSkills.LOGGER.info("In-world QA: {}", line);
    }

    private static PlayerSkillState state() {
        return ClientSkillState.state().orElse(null);
    }

    private static SkillTreeScreen screen() {
        if (!(minecraft().screen instanceof SkillTreeScreen)) {
            minecraft().setScreen(new SkillTreeScreen());
        }
        return (SkillTreeScreen) minecraft().screen;
    }

    private static int orbs() {
        int n = 0;
        for (ItemStack stack : minecraft().player.getInventory().items) {
            if (stack.is(ModItems.ORB_OF_RECONFIGURATION.get())) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void step(String name, Runnable action, BooleanSupplier done, int timeoutTicks) {
        STEPS.add(new Step(name, action, done, timeoutTicks));
    }

    private static double maxHealth() {
        return minecraft().player.getAttributeValue(Attributes.MAX_HEALTH);
    }

    private static void command(String command) {
        minecraft().player.connection.sendCommand(command);
    }

    private static void writeGatekeeperOverride() {
        String resource = "/data/nostalgiaskills/skill_nodes/guardian/gatekeeper.json";
        Path pack = minecraft().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("qa_reconcile");
        Path node = pack.resolve("data/nostalgiaskills/skill_nodes/guardian/gatekeeper.json");
        try (InputStream bundled = InWorldQa.class.getResourceAsStream(resource)) {
            JsonObject json = JsonParser.parseString(new String(bundled.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray requiresBastion = new JsonArray();
            requiresBastion.add("nostalgiaskills:guardian/bastion");
            json.getAsJsonObject("requirements").add("all_of", requiresBastion);
            Files.createDirectories(node.getParent());
            Files.writeString(pack.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":48,\"description\":\"QA: gatekeeper now requires bastion\"}}");
            Files.writeString(node, json.toString());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    static void start() {
        active = true;
        ClientSkillState.reset();
        Minecraft minecraft = minecraft();
        minecraft.getWindow().setWindowed(1280, 720);
        minecraft.options.guiScale().set(2);
        minecraft.options.pauseOnLostFocus = false;
        minecraft.resizeDisplay();
        step("create world", () -> minecraft().createWorldOpenFlows().createFreshLevel("nostalgiaskills_qa",
                new LevelSettings("nostalgiaskills_qa", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT),
                new WorldOptions(20261006L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen()),
                () -> minecraft().player != null && ClientSkillState.ready() && !ClientSkillState.isFixture(), 2400);
        step("settle", () -> {
            healthBefore = maxHealth();
            log("joined world; graph nodes=" + ClientSkillState.graph().map(g -> g.nodes().size()).orElse(0)
                    + ", max health=" + healthBefore + ", available points=" + state().availablePoints());
            command("xp add @s 30 levels");
        }, () -> minecraft().player.experienceLevel >= 30, 200);
        runAllSteps();
    }

    private static void addPurchaseSteps() {
        step("buy 6 points with XP", () -> {
            log("xp before purchases: level " + minecraft().player.experienceLevel + " progress " + minecraft().player.experienceProgress
                    + " (raw " + ClientSkillState.xpBalance() + ")");
            boughtTarget = 6;
        }, () -> {
            PlayerSkillState state = state();
            if (state.purchasedPoints() >= boughtTarget) {
                return true;
            }
            if (!ClientSkillState.requestInFlight()) {
                screen().qaBuy();
            }
            return false;
        }, 600);
        step("check purchase", () -> log("after buying: purchased=" + state().purchasedPoints() + ", available="
                + state().availablePoints() + ", xp raw " + ClientSkillState.xpBalance() + " (level " + minecraft().player.experienceLevel
                + "), expected XP spent 100+122+148+178+212+250=1010, next cost=" + ClientSkillState.economy().nextPointCost()),
                () -> true, 5);
    }

    private static void addAllocationSteps() {
        step("pending selection then cancel", () -> {
            List<String> pending = guardianRoute();
            screen().qaSetup(pending, false, false);
            log("pending selection of " + pending.size() + " nodes created (not confirmed)");
        }, () -> true, 20);
        step("verify cancel applies nothing", () -> {
            screen().qaSetup(List.of(), false, false);
            log("after cancel: allocations=" + state().allocations().size() + ", available=" + state().availablePoints()
                    + ", max health=" + maxHealth());
        }, () -> true, 20);
        step("confirm guardian route", () -> {
            List<String> pending = guardianRoute();
            screen().qaSetup(pending, false, false);
            screen().qaConfirm();
            log("confirm sent for " + pending);
        }, () -> state().isAllocated("nostalgiaskills:guardian/gatekeeper"), 400);
        step("effects applied", () -> { }, () -> maxHealth() > healthBefore, 200);
        step("report allocation", () -> log("after confirm: allocations=" + state().allocations().size()
                + ", available=" + state().availablePoints() + ", spent=" + state().activeSpentPoints() + ", max health "
                + healthBefore + " -> " + maxHealth() + " (expected +9: hub +2, 3x Vitality +1, Gatekeeper +4)"
                + ", ledger ok=" + state().ledgerHolds()), () -> true, 5);
        step("screenshot stats", () -> {
            screen().qaSetup(List.of(), true, false);
            screen().qaZoom(1.0, "guardian/hub");
        }, () -> true, 30);
        step("capture stats", () -> Screenshot.grab(minecraft().gameDirectory, "nostalgiaskills_qa_ingame_stats_panel_1280x720.png",
                minecraft().getMainRenderTarget(), m -> { }), () -> true, 10);
    }

    private static void addDeathAndDimensionSteps() {
        step("die", () -> {
            allocatedBeforeDeath = state().allocations().size();
            minecraft().setScreen(null);
            command("kill @s");
        }, () -> minecraft().player.isDeadOrDying(), 200);
        step("respawn", () -> minecraft().player.respawn(), () -> minecraft().player.isAlive() && minecraft().player.getHealth() > 0, 400);
        step("after respawn", () -> { }, () -> maxHealth() > healthBefore, 200);
        step("report respawn", () -> log("after death+respawn: allocations " + allocatedBeforeDeath + " -> "
                + state().allocations().size() + ", max health=" + maxHealth()
                + ", available=" + state().availablePoints()), () -> true, 5);
        step("dimension change", () -> command("execute in minecraft:the_nether run tp @s 0 120 0"),
                () -> minecraft().level.dimension() == Level.NETHER
                        && maxHealth() > healthBefore, 400);
        step("report dimension", () -> {
            log("after moving to the Nether: max health=" + maxHealth()
                    + ", allocations=" + state().allocations().size());
            command("execute in minecraft:overworld run tp @s 0 -59 0");
        }, () -> minecraft().level.dimension() == Level.OVERWORLD, 400);
    }

    private static void addRequestSpamSteps() {
        step("xp for spam test", () -> command("xp add @s 1000 points"),
                () -> ClientSkillState.xpBalance() >= ClientSkillState.economy().nextPointCost(), 200);
        step("replay and spam requests", () -> {
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.ok() ? "ok" : r.messageKey()), () -> { });
            long rev = state().stateRevision();
            purchasedBeforeSpam = state().purchasedPoints();
            UUID same = UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                PacketDistributor.sendToServer(new Payloads.BuyPoint(same, rev, 1));
            }
            for (int i = 0; i < 12; i++) {
                PacketDistributor.sendToServer(
                        new Payloads.BuyPoint(UUID.randomUUID(), rev, 1));
            }
        }, () -> RESULTS.size() >= 14, 400);
        step("report spam", () -> {
            Map<String, Integer> counts = new TreeMap<>();
            RESULTS.forEach(r -> counts.merge(r, 1, Integer::sum));
            log("14 rapid buy requests (2 share one request id, all expect the same state revision): results " + counts
                    + "; purchased " + purchasedBeforeSpam + " -> " + state().purchasedPoints() + " (exactly one purchase expected)"
                    + ", ledger ok=" + state().ledgerHolds());
            ClientSkillState.clearListeners();
        }, () -> true, 5);
    }

    private static void addWorldRestartSteps() {
        step("pause like the vanilla menu does before quitting", () -> {
            minecraft().setScreen(null);
            minecraft().pauseGame(false);
        }, () -> minecraft().isPaused() && ticks >= PAUSE_SAVE_TICKS, 4 * PAUSE_SAVE_TICKS);
        step("save and quit world", () -> {
            allocatedBeforeDeath = state().allocations().size();
            purchasedBeforeSpam = state().purchasedPoints();
            minecraft().level.disconnect();
            minecraft().disconnect(new GenericMessageScreen(
                    Component.literal("Saving world")));
        }, () -> minecraft().level == null && minecraft().getSingleplayerServer() == null, 1200);
        step("reopen world", () -> {
            ClientSkillState.reset();
            minecraft().createWorldOpenFlows().openWorld("nostalgiaskills_qa", () -> log("FAILED to reopen world"));
        }, () -> minecraft().player != null && ClientSkillState.ready() && maxHealth() > healthBefore, 2400);
        step("report restart", () -> log("after saving, quitting and reloading the world (integrated server restart): allocations "
                + allocatedBeforeDeath + " -> " + state().allocations().size() + ", purchased " + purchasedBeforeSpam + " -> "
                + state().purchasedPoints() + ", max health=" + maxHealth()
                + ", ledger ok=" + state().ledgerHolds()), () -> true, 5);
    }

    private static void addReloadSteps() {
        step("live prerequisite change via datapack + /reload", () -> {
            availableBeforeReload = state().availablePoints();
            allocatedBeforeDeath = state().allocations().size();
            writeGatekeeperOverride();
            command("reload");
        }, () -> !state().isAllocated("nostalgiaskills:guardian/gatekeeper") && maxHealth() < 29, 1200);
        step("report reconcile", () -> log("after /reload with Gatekeeper now requiring Bastion (graph revision "
                + ClientSkillState.graphRevision() + "): allocations " + allocatedBeforeDeath + " -> " + state().allocations().size()
                + ", available " + availableBeforeReload + " -> " + state().availablePoints() + " (recorded price 2 refunded once)"
                + ", max health=" + maxHealth() + " (expected 25), ledger ok=" + state().ledgerHolds()),
                () -> true, 5);
    }

    private static void addResetSteps() {
        step("give orb", () -> command("give @s nostalgiaskills:orb_of_reconfiguration 1"),
                () -> orbs() == 1, 200);
        step("reset with orb", () -> {
            log("before reset: orbs=" + orbs() + ", allocations=" + state().allocations().size() + ", available=" + state().availablePoints()
                    + ", purchased=" + state().purchasedPoints());
            screen().openResetPanel();
            screen().qaReset();
        }, () -> state().allocations().isEmpty(), 400);
        step("after reset", () -> { }, () -> orbs() == 0 && maxHealth() == healthBefore, 200);
        step("report reset", () -> log("after reset: orbs=" + orbs() + ", allocations=" + state().allocations().size()
                + ", available=" + state().availablePoints() + ", purchased=" + state().purchasedPoints() + ", next cost="
                + ClientSkillState.economy().nextPointCost() + " (curve not restarted: purchased stays " + purchasedBeforeSpam + "), max health="
                + maxHealth() + ", starting root free again=" + state().startingRoot().isEmpty()
                + ", ledger ok=" + state().ledgerHolds()), () -> true, 5);
    }

    private static void addRefundSteps() {
        step("grant points for the refund test",
                () -> command("nostalgiaskills points add @s 10"),
                () -> state().availablePoints() >= 10, 200);
        step("rebuild a guardian route", () -> {
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.messageKey() + " " + r.detail()), () -> { });
            List<String> pending = routeTo("guardian/vitality_3");
            screen().qaSetup(pending, false, false);
            screen().qaConfirm();
            log("refund test: confirm sent for " + pending.size() + " nodes: " + pending);
        }, () -> {
            if (state().allocations().size() >= 4) {
                ClientSkillState.clearListeners();
                log("refund test: build ready with " + state().allocations().size() + " allocations, server said " + RESULTS);
                return true;
            }
            if (RESULTS.stream().anyMatch(r -> r.startsWith("error.")) && !ClientSkillState.requestInFlight()) {
                log("refund test: confirm rejected with " + RESULTS + "; retrying");
                RESULTS.clear();
                screen().qaSetup(routeTo("guardian/vitality_3"), false, false);
                screen().qaConfirm();
            }
            return false;
        }, 400);
        step("refund one leaf skill", () -> {
            availableBeforeReload = state().availablePoints();
            allocatedBeforeDeath = state().allocations().size();
            healthBeforeRefund = maxHealth();
            refundTarget = state().orderedAllocations().get(allocatedBeforeDeath - 1).nodeId();
            refundCost = state().allocations().get(refundTarget).paidCost();
            log("refunding the last-acquired skill " + refundTarget + " (paid " + refundCost + "); preview says "
                    + screen().qaRefundPreview(refundTarget));
            screen().qaRefund(refundTarget);
            screen().qaConfirmRefund();
        }, () -> !state().isAllocated(refundTarget), 400);
        step("report leaf refund", () -> log("after refunding one skill: allocations "
                + allocatedBeforeDeath + " -> " + state().allocations().size() + ", available " + availableBeforeReload
                + " -> " + state().availablePoints() + " (recorded cost " + refundCost + " returned), max health "
                + healthBeforeRefund + " -> " + maxHealth()
                + ", purchased stays " + state().purchasedPoints() + ", ledger ok=" + state().ledgerHolds()), () -> true, 5);
        step("refund a skill the rest of the branch depends on", () -> {
            availableBeforeReload = state().availablePoints();
            allocatedBeforeDeath = state().allocations().size();
            refundTarget = state().orderedAllocations().get(1).nodeId();
            refundCost = state().activeSpentPoints();
            log("refunding " + refundTarget + " next to the hub; preview says " + screen().qaRefundPreview(refundTarget));
            screen().qaRefund(refundTarget);
            screen().qaConfirmRefund();
        }, () -> state().allocations().size() == 1, 400);
        step("report chain refund", () -> log("after refunding the hub-adjacent skill: allocations "
                + allocatedBeforeDeath + " -> " + state().allocations().size() + " (only the free hub "
                + state().startingRoot().orElse("?") + " left), available " + availableBeforeReload + " -> "
                + state().availablePoints() + " (all " + refundCost + " paid points back), max health="
                + maxHealth() + ", ledger ok=" + state().ledgerHolds()),
                () -> true, 5);
        step("the starting hub itself is not refundable", () -> {
            String hub = state().startingRoot().orElseThrow();
            log("asking to refund the starting hub " + hub + ": " + screen().qaRefundPreview(hub));
        }, () -> true, 5);
    }

    private static void addBulkPurchaseSteps() {
        step("xp for the bulk purchase", () -> command("xp set @s 0 levels"),
                () -> minecraft().player.experienceLevel == 0, 200);
        step("xp for the bulk purchase (points)", () -> command("xp set @s 0 points"),
                () -> ClientSkillState.xpBalance() == 0, 200);
        step("xp for the bulk purchase (grant)", () -> command("xp add @s 2000 points"),
                () -> ClientSkillState.xpBalance() == 2000, 200);
        step("buy 3 points in one request", () -> {
            RESULTS.clear();
            ClientSkillState.setListeners(r -> RESULTS.add(r.messageKey() + " " + r.detail()), () -> { });
            purchasedBeforeSpam = state().purchasedPoints();
            log("bulk buy: purchased " + purchasedBeforeSpam + ", xp " + ClientSkillState.xpBalance() + ", expected cost of 3 = "
                    + ClientSkillState.curve().totalCost(purchasedBeforeSpam, 3));
            screen().qaBuyMany(3);
        }, () -> !RESULTS.isEmpty() && !ClientSkillState.requestInFlight(), 400);
        step("report bulk buy", () -> {
            log("bulk buy of 3: purchased " + purchasedBeforeSpam + " -> " + state().purchasedPoints() + ", xp left "
                    + ClientSkillState.xpBalance() + ", server said " + RESULTS + ", ledger ok=" + state().ledgerHolds());
            RESULTS.clear();
            purchasedBeforeSpam = state().purchasedPoints();
            log("bulk buy beyond the balance: asking for 120 with xp " + ClientSkillState.xpBalance() + ", affordable = "
                    + ClientSkillState.curve().affordableCount(purchasedBeforeSpam, ClientSkillState.xpBalance(), 120));
            screen().qaBuyMany(120);
        }, () -> !RESULTS.isEmpty() && !ClientSkillState.requestInFlight(), 400);
        step("report partial bulk buy", () -> {
            ClientSkillState.clearListeners();
            log("bulk buy of 120: purchased " + purchasedBeforeSpam + " -> " + state().purchasedPoints() + ", xp left "
                    + ClientSkillState.xpBalance() + " (next cost " + ClientSkillState.economy().nextPointCost() + "), server said "
                    + RESULTS + ", ledger ok=" + state().ledgerHolds());
        }, () -> true, 5);
    }

    private static void addPendingStatsSteps() {
        step("pending route with the stats panel open", () -> {
            screen().qaSetup(routeTo("guardian/vitality_3"), false, false);
            screen().qaShowStats();
            screen().qaZoom(0.6, "guardian/hub");
        }, () -> true, 30);
        step("capture pending stats", () -> {
            log("pending " + screen().qaPending() + ", available " + state().availablePoints());
            screen().qaStatsText().stream().filter(l -> l.contains("→")).forEach(l -> log("stats before -> after: " + l.trim()));
            Screenshot.grab(minecraft().gameDirectory, "nostalgiaskills_qa_ingame_stats_pending_1280x720.png", minecraft().getMainRenderTarget(), m -> { });
        }, () -> true, 10);
    }

    private static void addWorldEventSteps() {
        step("birth sign and shooting star", () -> {
            String signName = state().sign().map(signId -> I18n.get(SkillSign.nameKey(signId))).orElse("none");
            log("birth sign: " + state().sign().orElse("none") + "; stats line: "
                    + screen().qaStatsText().stream().filter(line -> line.contains(signName)).findFirst().orElse("missing"));
            command("nostalgiaskills star @s");
        }, () -> true, 40);
        step("start an eclipse", () -> command("nostalgiaskills eclipse start"),
                () -> ClientSkillState.worldRules().eclipseActive(), 200);
        step("reset panel during the eclipse", () -> {
            log("eclipse active=" + ClientSkillState.worldRules().eclipseActive() + ", orb required for reset="
                    + ClientSkillState.economy().orbRequired());
            screen().qaSetup(List.of(), false, true);
        }, () -> true, 30);
        step("capture eclipse reset panel", () -> Screenshot.grab(minecraft().gameDirectory,
                "nostalgiaskills_qa_ingame_eclipse_reset_1280x720.png", minecraft().getMainRenderTarget(), message -> { }), () -> true, 10);
        step("stop the eclipse", () -> {
            screen().qaSetup(List.of(), false, false);
            command("nostalgiaskills eclipse stop");
        }, () -> !ClientSkillState.worldRules().eclipseActive(), 200);
        step("xp for the donation", () -> command("xp add @s 400 points"), () -> ClientSkillState.xpBalance() >= 400, 200);
        step("donate to the shared constellation", () -> {
            log("orb required again=" + ClientSkillState.economy().orbRequired() + "; community tiers known to the client: "
                    + ClientSkillState.worldRules().communityTiers().size() + ", xp before donating " + ClientSkillState.xpBalance());
            screen().qaOpenCommunity().qaDonate(120);
        }, () -> ClientSkillState.worldRules().communityXp() == 120 && !ClientSkillState.requestInFlight(), 400);
        step("community panel", () -> {
            log("donated: community progress " + ClientSkillState.worldRules().communityXp() + ", xp left " + ClientSkillState.xpBalance()
                    + ", signs known to the client: " + ClientSkillState.worldRules().signIds().size());
        }, () -> true, 30);
        step("capture community panel", () -> Screenshot.grab(minecraft().gameDirectory,
                "nostalgiaskills_qa_ingame_community_1280x720.png", minecraft().getMainRenderTarget(), message -> { }), () -> true, 10);
        step("light two tiers", () -> command("nostalgiaskills community set 9000"),
                () -> ClientSkillState.worldRules().communityXp() == 9000, 200);
        step("capture lit community", () -> Screenshot.grab(minecraft().gameDirectory,
                "nostalgiaskills_qa_ingame_community_lit_1280x720.png", minecraft().getMainRenderTarget(), message -> { }), () -> true, 10);
        step("birth chart", () -> screen().qaOpenSign(), () -> true, 30);
        step("capture birth chart", () -> Screenshot.grab(minecraft().gameDirectory,
                "nostalgiaskills_qa_ingame_birth_chart_1280x720.png", minecraft().getMainRenderTarget(), message -> { }), () -> true, 10);
    }

    private static void runAllSteps() {
        addPurchaseSteps();
        addAllocationSteps();
        addDeathAndDimensionSteps();
        addRequestSpamSteps();
        addWorldRestartSteps();
        addReloadSteps();
        addResetSteps();
        addRefundSteps();
        addBulkPurchaseSteps();
        addPendingStatsSteps();
        addWorldEventSteps();
        step("finish", InWorldQa::finish, () -> true, 5);
        next();
    }

    private static List<String> guardianRoute() {
        SkillGraphSnapshot graph = ClientSkillState.graph().orElseThrow();
        PlayerSkillState state = state().copy();
        String hub = "nostalgiaskills:guardian/hub";
        AllocationService.confirm(graph, state, List.of(hub));
        List<String> out = new ArrayList<>();
        out.add(hub);
        PathFinder.route(graph, state, List.of(), "nostalgiaskills:guardian/gatekeeper").ifPresent(r -> out.addAll(r.newNodes()));
        return out;
    }

    private static List<String> routeTo(String... targets) {
        SkillGraphSnapshot graph = ClientSkillState.graph().orElseThrow();
        PlayerSkillState state = state().copy();
        String hub = "nostalgiaskills:guardian/hub";
        List<String> out = new ArrayList<>();
        if (state.startingRoot().isEmpty()) {
            AllocationService.confirm(graph, state, List.of(hub));
            out.add(hub);
        }
        for (String target : targets) {
            PathFinder.route(graph, state, List.of(), "nostalgiaskills:" + target)
                    .filter(PathFinder.Route::valid)
                    .ifPresentOrElse(r -> {
                        out.addAll(r.newNodes());
                        AllocationService.confirm(graph, state, r.newNodes());
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
            Files.write(minecraft().gameDirectory.toPath().resolve("nostalgiaskills_qa_ingame.txt"), REPORT);
        } catch (IOException e) {
            NostalgiaSkills.LOGGER.error("could not write QA report", e);
        }
        NostalgiaSkills.LOGGER.info("In-world QA: finished");
        active = false;
        minecraft().stop();
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
                step = STEPS.size() - 2;
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
