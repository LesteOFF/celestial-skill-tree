package dev.lesteoff.celestialskilltree.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.client.screen.SkillTreeScreen;
import dev.lesteoff.celestialskilltree.client.screen.TreeScreenQa;
import dev.lesteoff.celestialskilltree.compat.Reflection;
import dev.lesteoff.celestialskilltree.graph.NodeType;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.network.Payloads;
import dev.lesteoff.celestialskilltree.progression.Allocation;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.LedgerService;
import dev.lesteoff.celestialskilltree.progression.PathFinder;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class InWorldQa {
    private static final String WORLD = "celestialskilltree_qa";
    private static final long WORLD_SEED = 20261006L;
    private static final String SHOT_PREFIX = "celestialskilltree_qa_ingame_";
    private static final String SHOT_SUFFIX = "_1280x720.png";
    private static final String GUARDIAN = "celestialskilltree:guardian";
    private static final String HUB = GUARDIAN + "/hub";
    private static final String GATEKEEPER = GUARDIAN + "/gatekeeper";
    private static final String GATEKEEPER_THEME = "data/celestialskilltree/skill_themes/guardian/plating.json";
    private static final String RECONCILE_PACK = "qa_reconcile";
    private static final String PACK_META = "{\"pack\":{\"pack_format\":48,\"description\":\"QA: Gatekeeper leaves the catalog\"}}";
    private static final String ROOT_NEEDS_RESET = "rejected: ROOT_NEEDS_RESET";
    private static final String ACCEPTED = "ok";
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final int WORLD_LOAD_TICKS = 2400;
    private static final int SERVER_ANSWER_TICKS = 400;
    private static final int COMMAND_TICKS = 200;
    private static final int RELOAD_TICKS = 1200;
    private static final int SETTLE_TICKS = 10;
    private static final int PAUSE_SAVE_TICKS = 60;
    private static final int RETRY_TICKS = 40;
    private static final int SKY_FADE_TICKS = 140;
    private static final int STAR_FLIGHT_TICKS = 45;
    private static final int FALLING_STARS = 6;
    private static final String ASTRAL_RESEARCH = "hellfirepvp.astralsorcery.common.research.ResearchHelper";
    private static final String ASTRAL_TIER = "hellfirepvp.astralsorcery.common.research.ResearchTier";
    private static final String ASTRAL_CONSTELLATIONS = "hellfirepvp.astralsorcery.common.lib.ConstellationsAS";
    private static final long DAY_TICKS = 24_000L;
    private static final long MIDNIGHT = 18_000L;
    private static final int LUNAR_ECLIPSE_PERIOD_DAYS = 68;
    private static final int ACTION_BAR_TICKS = 30;
    private static final int FIRST_PURCHASE = 6;
    private static final int BULK_PURCHASE = 3;
    private static final int GREEDY_PURCHASE = 120;
    private static final int BURST_REQUESTS = 14;
    private static final int REPLAYED_REQUESTS = 2;
    private static final int GRANTED_POINTS = 10;
    private static final int MOST_EXPENSIVE_SKILL = 10;
    private static final int BULK_XP = 2000;
    private static final int DONATION = 120;
    private static final int LIT_COMMUNITY_XP = 9000;
    private static final float SKY_PITCH = -70f;

    record Snapshot(int allocations, int available, int spent, int purchased, double maxHealth, long xp, long communityXp,
                            int graphRevision, boolean orbRequired) {
        static Snapshot take() {
            PlayerSkillState state = state();
            return new Snapshot(state.allocations().size(), state.availablePoints(), state.activeSpentPoints(), state.purchasedPoints(),
                    InWorldQa.maxHealth(), ClientSkillState.xpBalance(), ClientSkillState.worldRules().communityXp(),
                    ClientSkillState.graphRevision(), ClientSkillState.economy().orbRequired());
        }
    }

    private final QaScript script;
    private final String shotPrefix;
    private final String shotSuffix;
    private final List<String> answers = new ArrayList<>();
    private Snapshot before;
    private List<String> route = List.of();
    private String target = "";
    private double healthAtJoin;
    private int expectedCost;
    private int expectedBought;

    InWorldQa(QaScript script) {
        this(script, SHOT_PREFIX, SHOT_SUFFIX);
    }

    InWorldQa(QaScript script, String shotPrefix, String shotSuffix) {
        this.script = script;
        this.shotPrefix = shotPrefix;
        this.shotSuffix = shotSuffix;
    }

    private void capture(String name) {
        script.capture(shotPrefix + name + shotSuffix);
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer player() {
        return minecraft().player;
    }

    private static PlayerSkillState state() {
        return ClientSkillState.state().orElseThrow();
    }

    private static SkillGraphSnapshot graph() {
        return ClientSkillState.graph().orElseThrow();
    }

    private static double maxHealth() {
        return player().getAttributeValue(Attributes.MAX_HEALTH);
    }

    private static boolean healthIs(double expected) {
        return Math.abs(maxHealth() - expected) < 1e-4;
    }

    private static void command(String command) {
        player().connection.sendCommand(command);
    }

    private static int orbs() {
        return player().getInventory().countItem(ModItems.ORB_OF_RECONFIGURATION.get());
    }

    private static boolean inDimension(ResourceKey<Level> dimension) {
        return minecraft().level != null && minecraft().level.dimension() == dimension;
    }

    private static SkyEvent skyEvent() {
        return ClientSkillState.worldRules().skyEvent();
    }

    private static SkillTreeScreen screen() {
        if (!(minecraft().screen instanceof SkillTreeScreen)) {
            minecraft().setScreen(new SkillTreeScreen());
        }
        return (SkillTreeScreen) minecraft().screen;
    }

    static String situation() {
        if (player() == null || ClientSkillState.state().isEmpty()) {
            return "not in a world";
        }
        PlayerSkillState state = state();
        return "skills=" + state.allocations().size() + ", available=" + state.availablePoints() + ", purchased=" + state.purchasedPoints()
                + ", max health=" + maxHealth() + ", xp=" + ClientSkillState.xpBalance() + ", ready=" + ClientSkillState.ready()
                + ", request in flight=" + ClientSkillState.requestInFlight() + ", last answer=" + ClientSkillState.lastAnswer();
    }

    static boolean joined() {
        return player() != null && ClientSkillState.ready() && !ClientSkillState.isFixture();
    }

    private void remember() {
        before = Snapshot.take();
    }

    private void expectUnchanged(String when) {
        PlayerSkillState state = state();
        script.expect("skills learned " + when, before.allocations(), state.allocations().size());
        script.expect("points available " + when, before.available(), state.availablePoints());
        script.expect("points purchased " + when, before.purchased(), state.purchasedPoints());
        script.expectNear("max health " + when, before.maxHealth(), maxHealth());
        script.expect("ledger holds " + when, state.ledgerHolds());
    }

    private void show(String name, Runnable arrange) {
        script.step("show " + name, arrange, () -> script.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
        script.then("capture " + name, () -> capture(name));
    }

    private static Optional<List<String>> validRouteTo(String targetId) {
        PlayerSkillState simulated = state().copy();
        List<String> nodes = new ArrayList<>();
        if (simulated.startingRoot().isEmpty()) {
            AllocationService.confirm(graph(), simulated, List.of(HUB));
            nodes.add(HUB);
        }
        return PathFinder.route(graph(), simulated, List.of(), targetId).filter(PathFinder.Route::valid).map(found -> {
            nodes.addAll(found.newNodes());
            return nodes;
        });
    }

    private static List<String> routeTo(String targetId) {
        return validRouteTo(targetId).orElseThrow(() -> new IllegalStateException("no valid route to " + targetId));
    }

    private static int costOf(List<String> nodes) {
        PlayerSkillState wealthy = state().copy();
        LedgerService.grant(wealthy, nodes.size() * MOST_EXPENSIVE_SKILL, Integer.MAX_VALUE, true);
        return AllocationService.confirm(graph(), wealthy, nodes).totalCost();
    }

    private static String cheapestGuardianNotable() {
        Map<String, Integer> costs = new TreeMap<>();
        graph().nodes().values().stream().filter(node -> node.type() == NodeType.NOTABLE && node.sector().equals(GUARDIAN))
                .forEach(notable -> validRouteTo(notable.id()).ifPresent(found -> costs.put(notable.id(), costOf(found))));
        return costs.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("no guardian notable can be reached"));
    }

    private void confirm(List<String> nodes) {
        TreeScreenQa.setup(screen(), nodes, false, false);
        TreeScreenQa.confirm(screen());
    }

    private boolean learnedRoute() {
        if (route.stream().allMatch(state()::isAllocated)) {
            return true;
        }
        if (!ClientSkillState.requestInFlight() && script.ticks() % RETRY_TICKS == 0) {
            confirm(route);
        }
        return false;
    }

    void enterWorld() {
        script.then("prepare the window", InWorldQa::prepareWindow);
        script.then("delete the previous QA world", InWorldQa::deleteWorld);
        script.step("create a flat world", InWorldQa::createWorld, InWorldQa::joined, WORLD_LOAD_TICKS);
        script.then("check the fresh player", () -> {
            healthAtJoin = maxHealth();
            script.note("joined: " + graph().nodes().size() + " skills in the tree, max health " + healthAtJoin);
            script.expect("points of a fresh player", 0, state().availablePoints());
            script.expect("skills of a fresh player", 0, state().allocations().size());
            script.expect("a birth sign is assigned at the first login", state().sign().isPresent());
        });
    }

    static void prepareWindow() {
        Minecraft minecraft = minecraft();
        minecraft.getWindow().setWindowed(WINDOW_WIDTH, WINDOW_HEIGHT);
        minecraft.options.guiScale().set(2);
        minecraft.options.pauseOnLostFocus = false;
        minecraft.resizeDisplay();
    }

    private static void deleteWorld() {
        try (LevelStorageSource.LevelStorageAccess previous = minecraft().getLevelSource().createAccess(WORLD)) {
            previous.deleteLevel();
        } catch (IOException undeletable) {
            throw new UncheckedIOException(undeletable);
        }
    }

    private static void createWorld() {
        ClientSkillState.reset();
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        minecraft().createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(WORLD_SEED, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(), new TitleScreen());
    }

    void addScenario() {
        script.step("grant 30 levels", () -> command("xp add @s 30 levels"), () -> player().experienceLevel >= 30, COMMAND_TICKS);
        addPurchaseSteps();
        addAllocationSteps();
        addDeathSteps();
        addDimensionSteps();
        addRequestBurstSteps();
        addWorldRestartSteps();
        addReconcileSteps();
        addResetSteps();
        addRefundSteps();
        addBulkPurchaseSteps();
        addPendingStatsSteps();
        addBirthSignSteps();
        addEclipseSteps();
        addCommunitySteps();
        addSkySteps();
        addShowcaseSteps();
        script.pause("let the screenshots reach the disk", 2 * SETTLE_TICKS);
    }

    private void addPurchaseSteps() {
        script.step("buy " + FIRST_PURCHASE + " points one at a time", this::remember, () -> {
            if (state().purchasedPoints() >= before.purchased() + FIRST_PURCHASE) {
                return true;
            }
            if (!ClientSkillState.requestInFlight()) {
                TreeScreenQa.buy(screen());
            }
            return false;
        }, 600);
        script.then("check the purchases", () -> {
            long cost = ClientSkillState.curve().totalCost(before.purchased(), FIRST_PURCHASE);
            script.expect("points purchased", before.purchased() + FIRST_PURCHASE, state().purchasedPoints());
            script.expect("points available", before.available() + FIRST_PURCHASE, state().availablePoints());
            script.expect("XP left after paying " + cost, before.xp() - cost, ClientSkillState.xpBalance());
            script.expect("price of the next point", ClientSkillState.curve().cost(state().purchasedPoints()),
                    ClientSkillState.economy().nextPointCost());
        });
    }

    private void addAllocationSteps() {
        script.step("receive the points the route still needs", () -> {
            route = routeTo(GATEKEEPER);
            expectedCost = costOf(route);
            command("celestialskilltree points add @s " + Math.max(0, expectedCost - state().availablePoints()));
        }, () -> state().availablePoints() >= expectedCost, COMMAND_TICKS);
        script.step("select the route without confirming", () -> {
            remember();
            TreeScreenQa.setup(screen(), route, false, false);
        }, () -> script.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
        script.then("cancel the selection", () -> {
            script.note("selection before cancelling: " + TreeScreenQa.pending(screen()));
            TreeScreenQa.setup(screen(), List.of(), false, false);
            expectUnchanged("after cancelling a selection");
        });
        script.step("confirm the route to Gatekeeper", () -> {
            remember();
            confirm(route);
            script.note("confirm sent for " + route);
        }, () -> state().isAllocated(GATEKEEPER) && maxHealth() > before.maxHealth(), SERVER_ANSWER_TICKS);
        script.then("check the build", () -> {
            script.note("max health " + before.maxHealth() + " -> " + maxHealth());
            script.expect("skills learned", before.allocations() + route.size(), state().allocations().size());
            script.expect("points available after paying " + expectedCost, before.available() - expectedCost, state().availablePoints());
            script.expect("the hub is the starting root", HUB, state().startingRoot().orElse(""));
            script.expect("ledger holds after confirming", state().ledgerHolds());
        });
        show("stats_panel", () -> {
            TreeScreenQa.setup(screen(), List.of(), true, false);
            TreeScreenQa.zoom(screen(), 1.0, "guardian/hub");
        });
    }

    private void addDeathSteps() {
        script.step("die", () -> {
            remember();
            minecraft().setScreen(null);
            command("kill @s");
        }, () -> player().isDeadOrDying(), COMMAND_TICKS);
        script.step("respawn", () -> player().respawn(), () -> player().isAlive() && player().getHealth() > 0, SERVER_ANSWER_TICKS);
        script.waitFor("effects return after the respawn", () -> healthIs(before.maxHealth()), COMMAND_TICKS);
        script.then("check the build after death", () -> expectUnchanged("after death and respawn"));
    }

    private void addDimensionSteps() {
        script.step("travel to the Nether", () -> {
            remember();
            command("execute in minecraft:the_nether run tp @s 0 120 0");
        }, () -> inDimension(Level.NETHER) && healthIs(before.maxHealth()), SERVER_ANSWER_TICKS);
        script.then("check the build in the Nether", () -> expectUnchanged("in the Nether"));
        script.step("return to the Overworld", () -> command("execute in minecraft:overworld run tp @s 0 -59 0"),
                () -> inDimension(Level.OVERWORLD) && healthIs(before.maxHealth()), SERVER_ANSWER_TICKS);
    }

    private void addRequestBurstSteps() {
        script.step("grant XP for the request burst", () -> command("xp add @s 1000 points"),
                () -> ClientSkillState.xpBalance() >= ClientSkillState.economy().nextPointCost(), COMMAND_TICKS);
        script.step("send " + BURST_REQUESTS + " buy requests in one tick", () -> {
            minecraft().setScreen(null);
            remember();
            answers.clear();
            ClientSkillState.setListeners(result -> answers.add(result.ok() ? ACCEPTED : result.messageKey()), () -> { });
            long revision = state().stateRevision();
            UUID replayed = UUID.randomUUID();
            for (int request = 0; request < BURST_REQUESTS; request++) {
                UUID id = request < REPLAYED_REQUESTS ? replayed : UUID.randomUUID();
                PacketDistributor.sendToServer(new Payloads.BuyPoint(id, revision, 1));
            }
        }, () -> answers.size() >= BURST_REQUESTS, SERVER_ANSWER_TICKS);
        script.then("check the burst", () -> {
            ClientSkillState.clearListeners();
            Map<String, Integer> counts = new TreeMap<>();
            answers.forEach(answer -> counts.merge(answer, 1, Integer::sum));
            script.note("answers to the burst: " + counts);
            script.expect("requests accepted out of " + BURST_REQUESTS, 1, counts.getOrDefault(ACCEPTED, 0).intValue());
            script.expect("points purchased by the burst", before.purchased() + 1, state().purchasedPoints());
            script.expect("ledger holds after the burst", state().ledgerHolds());
        });
    }

    private void addWorldRestartSteps() {
        script.step("pause like the vanilla menu does before quitting", () -> {
            minecraft().setScreen(null);
            minecraft().pauseGame(false);
        }, () -> minecraft().isPaused() && script.ticks() >= PAUSE_SAVE_TICKS, 4 * PAUSE_SAVE_TICKS);
        script.step("save and quit the world", () -> {
            remember();
            minecraft().level.disconnect();
            minecraft().disconnect(new GenericMessageScreen(Component.literal("Saving world")));
        }, () -> minecraft().level == null && minecraft().getSingleplayerServer() == null, RELOAD_TICKS);
        script.step("open the world again", () -> {
            ClientSkillState.reset();
            minecraft().createWorldOpenFlows().openWorld(WORLD, () -> script.note("the world could not be opened again"));
        }, () -> joined() && healthIs(before.maxHealth()), WORLD_LOAD_TICKS);
        script.then("check the build after the restart", () -> expectUnchanged("after saving, quitting and reopening the world"));
    }

    private void addReconcileSteps() {
        script.step("remove Gatekeeper from the catalog and reload", () -> {
            remember();
            expectedCost = state().allocations().get(GATEKEEPER).paidCost();
            writeCatalogWithoutGatekeeper();
            command("reload");
        }, () -> ClientSkillState.ready() && ClientSkillState.graphRevision() > before.graphRevision()
                && !state().isAllocated(GATEKEEPER) && maxHealth() < before.maxHealth(), RELOAD_TICKS);
        script.then("check the reconciliation", () -> {
            PlayerSkillState state = state();
            script.note("after the reload: skills " + before.allocations() + " -> " + state.allocations().size() + ", available "
                    + before.available() + " -> " + state.availablePoints() + ", max health " + before.maxHealth() + " -> " + maxHealth());
            script.expect("Gatekeeper left the tree", !graph().contains(GATEKEEPER));
            script.expect("its recorded price of " + expectedCost + " came back", state.availablePoints() >= before.available() + expectedCost);
            script.expect("no point was created or lost", before.available() + before.spent(),
                    state.availablePoints() + state.activeSpentPoints());
            script.expect("points purchased after the reload", before.purchased(), state.purchasedPoints());
            script.expect("ledger holds after the reload", state.ledgerHolds());
        });
    }

    private static void writeCatalogWithoutGatekeeper() {
        Path pack = minecraft().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(RECONCILE_PACK);
        Path theme = pack.resolve(GATEKEEPER_THEME);
        try (InputStream bundled = InWorldQa.class.getResourceAsStream("/" + GATEKEEPER_THEME)) {
            if (bundled == null) {
                throw new IllegalStateException("the mod no longer ships " + GATEKEEPER_THEME);
            }
            JsonObject json = JsonParser.parseString(new String(bundled.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.getAsJsonArray("notables").asList().removeIf(notable -> GATEKEEPER.endsWith("/" + notable.getAsJsonObject().get("id").getAsString()));
            Files.createDirectories(theme.getParent());
            Files.writeString(pack.resolve("pack.mcmeta"), PACK_META);
            Files.writeString(theme, json.toString());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void addResetSteps() {
        script.step("receive an Orb of Reconfiguration", () -> command("give @s celestialskilltree:orb_of_reconfiguration 1"),
                () -> orbs() == 1, COMMAND_TICKS);
        script.step("reset the build with the orb", () -> {
            remember();
            screen().openResetPanel();
            TreeScreenQa.reset(screen());
        }, () -> state().allocations().isEmpty(), SERVER_ANSWER_TICKS);
        script.waitFor("the orb is consumed and the effects leave", () -> orbs() == 0 && healthIs(healthAtJoin), COMMAND_TICKS);
        script.then("check the reset", () -> {
            PlayerSkillState state = state();
            script.expect("points available after the reset", before.available() + before.spent(), state.availablePoints());
            script.expect("points purchased after the reset", before.purchased(), state.purchasedPoints());
            script.expect("the price curve does not restart", ClientSkillState.curve().cost(before.purchased()),
                    ClientSkillState.economy().nextPointCost());
            script.expect("a new starting hub may be chosen", state.startingRoot().isEmpty());
            script.expect("ledger holds after the reset", state.ledgerHolds());
        });
    }

    private void addRefundSteps() {
        script.step("receive points from an operator", () -> {
            remember();
            command("celestialskilltree points add @s " + GRANTED_POINTS);
        }, () -> state().availablePoints() == before.available() + GRANTED_POINTS, COMMAND_TICKS);
        script.step("learn the cheapest guardian notable", () -> {
            remember();
            route = routeTo(cheapestGuardianNotable());
            expectedCost = costOf(route);
            confirm(route);
            script.note("confirm sent for " + route);
        }, this::learnedRoute, SERVER_ANSWER_TICKS);
        script.then("check the new build", () -> script.expect("points available after paying " + expectedCost,
                before.available() - expectedCost, state().availablePoints()));
        script.step("refund the newest skill", () -> {
            remember();
            List<Allocation> learned = state().orderedAllocations();
            target = learned.get(learned.size() - 1).nodeId();
            expectedCost = state().allocations().get(target).paidCost();
            script.note("refund preview for " + target + ": " + TreeScreenQa.refundPreview(target));
            TreeScreenQa.refund(screen(), target);
            TreeScreenQa.confirmRefund(screen());
        }, () -> !state().isAllocated(target), SERVER_ANSWER_TICKS);
        script.then("check the single refund", () -> {
            PlayerSkillState state = state();
            script.expect("skills learned after one refund", before.allocations() - 1, state.allocations().size());
            script.expect("points available after refunding " + expectedCost, before.available() + expectedCost, state.availablePoints());
            script.expect("points purchased after one refund", before.purchased(), state.purchasedPoints());
            script.expect("its bonus is gone", maxHealth() <= before.maxHealth());
            script.expect("ledger holds after one refund", state.ledgerHolds());
        });
        script.step("refund the skill next to the hub", () -> {
            remember();
            target = state().orderedAllocations().get(1).nodeId();
            script.note("refund preview for " + target + ": " + TreeScreenQa.refundPreview(target));
            TreeScreenQa.refund(screen(), target);
            TreeScreenQa.confirmRefund(screen());
        }, () -> state().allocations().size() == 1, SERVER_ANSWER_TICKS);
        script.then("check the chained refund", () -> {
            PlayerSkillState state = state();
            script.expect("points available after the whole branch came back", before.available() + before.spent(), state.availablePoints());
            script.expect("only the free hub is left", HUB, state.startingRoot().orElse(""));
            script.expect("the starting hub cannot be refunded alone", ROOT_NEEDS_RESET, TreeScreenQa.refundPreview(HUB));
            script.expect("ledger holds after the chained refund", state.ledgerHolds());
        });
    }

    private void addBulkPurchaseSteps() {
        script.step("empty the experience levels", () -> command("xp set @s 0 levels"), () -> player().experienceLevel == 0, COMMAND_TICKS);
        script.step("empty the experience bar", () -> command("xp set @s 0 points"), () -> ClientSkillState.xpBalance() == 0, COMMAND_TICKS);
        script.step("receive " + BULK_XP + " XP", () -> command("xp add @s " + BULK_XP + " points"),
                () -> ClientSkillState.xpBalance() == BULK_XP, COMMAND_TICKS);
        script.step("buy " + BULK_PURCHASE + " points in one request", () -> {
            remember();
            TreeScreenQa.buyMany(screen(), BULK_PURCHASE);
        }, () -> state().purchasedPoints() == before.purchased() + BULK_PURCHASE && !ClientSkillState.requestInFlight(), SERVER_ANSWER_TICKS);
        script.then("check the bulk purchase", () -> {
            long cost = ClientSkillState.curve().totalCost(before.purchased(), BULK_PURCHASE);
            script.expect("XP left after paying " + cost + " at once", before.xp() - cost, ClientSkillState.xpBalance());
            script.expect("ledger holds after the bulk purchase", state().ledgerHolds());
        });
        script.step("ask for " + GREEDY_PURCHASE + " points with too little XP", () -> {
            remember();
            expectedBought = ClientSkillState.curve().affordableCount(before.purchased(), before.xp(), GREEDY_PURCHASE);
            TreeScreenQa.buyMany(screen(), GREEDY_PURCHASE);
        }, () -> state().purchasedPoints() == before.purchased() + expectedBought && !ClientSkillState.requestInFlight(), SERVER_ANSWER_TICKS);
        script.then("check the partial purchase", () -> {
            long cost = ClientSkillState.curve().totalCost(before.purchased(), expectedBought);
            script.note("the balance of " + before.xp() + " XP affords " + expectedBought + " point(s)");
            script.expect("XP left after buying what the balance allows", before.xp() - cost, ClientSkillState.xpBalance());
            script.expect("the next point is out of reach", ClientSkillState.xpBalance() < ClientSkillState.economy().nextPointCost());
            script.expect("ledger holds after the partial purchase", state().ledgerHolds());
        });
    }

    private void addPendingStatsSteps() {
        show("stats_pending", () -> {
            TreeScreenQa.setup(screen(), routeTo(cheapestGuardianNotable()), false, false);
            TreeScreenQa.showStats(screen());
            TreeScreenQa.zoom(screen(), 0.6, "guardian/hub");
        });
        script.then("check the stats preview", () -> {
            List<String> previews = TreeScreenQa.statsText(screen()).stream().filter(line -> line.contains("→")).map(String::trim).toList();
            script.note("selection " + TreeScreenQa.pending(screen()) + " previews " + previews);
            script.expect("the stats panel previews the selected route", !previews.isEmpty());
            TreeScreenQa.setup(screen(), List.of(), false, false);
        });
    }

    private void addBirthSignSteps() {
        script.then("find the birth sign in the stats panel", () -> {
            String signName = state().sign().map(signId -> I18n.get(SkillSign.nameKey(signId))).orElse("no sign");
            script.expect("the stats panel names the birth sign " + signName,
                    TreeScreenQa.statsText(screen()).stream().anyMatch(line -> line.contains(signName)));
            script.expect("signs known to the client", !ClientSkillState.worldRules().signIds().isEmpty());
        });
        show("birth_chart", () -> TreeScreenQa.openSign(screen()));
    }

    private void addEclipseSteps() {
        script.step("start an eclipse", () -> {
            remember();
            command("celestialskilltree eclipse start");
        }, () -> skyEvent() == SkyEvent.ECLIPSE, COMMAND_TICKS);
        show("eclipse_reset", () -> {
            script.expect("no orb is asked for during an eclipse", !ClientSkillState.economy().orbRequired());
            TreeScreenQa.setup(screen(), List.of(), false, true);
        });
        script.step("stop the eclipse", () -> {
            TreeScreenQa.setup(screen(), List.of(), false, false);
            command("celestialskilltree eclipse stop");
        }, () -> skyEvent() == SkyEvent.NONE, COMMAND_TICKS);
        script.then("check the orb rule", () -> script.expect("the orb rule returns after the eclipse", before.orbRequired(),
                ClientSkillState.economy().orbRequired()));
    }

    private void addCommunitySteps() {
        script.step("receive XP to donate", () -> command("xp add @s " + 2 * DONATION + " points"),
                () -> ClientSkillState.xpBalance() >= DONATION, COMMAND_TICKS);
        script.step("donate to the shared constellation", () -> {
            remember();
            TreeScreenQa.donate(TreeScreenQa.openCommunity(screen()), DONATION);
        }, () -> ClientSkillState.worldRules().communityXp() == before.communityXp() + DONATION && !ClientSkillState.requestInFlight(),
                SERVER_ANSWER_TICKS);
        script.then("check the donation", () -> {
            script.expect("XP left after donating", before.xp() - DONATION, ClientSkillState.xpBalance());
            script.expect("community tiers known to the client", !ClientSkillState.worldRules().communityTiers().isEmpty());
        });
        script.pause("let the community panel draw", SETTLE_TICKS);
        script.then("capture community", () -> capture("community"));
        script.step("light the first tiers", () -> command("celestialskilltree community set " + LIT_COMMUNITY_XP),
                () -> ClientSkillState.worldRules().communityXp() == LIT_COMMUNITY_XP, COMMAND_TICKS);
        script.pause("let the lit tiers draw", SETTLE_TICKS);
        script.then("capture community_lit", () -> capture("community_lit"));
    }

    void addSkySteps() {
        script.step("eclipse at noon", () -> {
            minecraft().setScreen(null);
            player().setXRot(SKY_PITCH);
            command("time set noon");
            command("weather clear");
            command("celestialskilltree eclipse start");
        }, () -> skyEvent() == SkyEvent.ECLIPSE && script.ticks() >= SKY_FADE_TICKS, 2 * SKY_FADE_TICKS);
        script.then("capture eclipse_sky", () -> capture("eclipse_sky"));
        script.step("star shower at midnight", () -> {
            command("celestialskilltree eclipse stop");
            command("time set midnight");
            command("celestialskilltree shower start");
        }, () -> skyEvent() == SkyEvent.STAR_SHOWER && script.ticks() >= SKY_FADE_TICKS, 2 * SKY_FADE_TICKS);
        script.step("call falling stars", () -> {
            for (int star = 0; star < FALLING_STARS; star++) {
                command("celestialskilltree star @s");
            }
        }, () -> script.ticks() >= STAR_FLIGHT_TICKS, 2 * STAR_FLIGHT_TICKS);
        script.then("capture falling_stars", () -> capture("falling_stars"));
        script.step("stop the star shower", () -> command("celestialskilltree shower stop"), () -> skyEvent() == SkyEvent.NONE, COMMAND_TICKS);
    }

    void addAstralSteps() {
        script.step("reach Luminance in Astral Sorcery", () -> {
            minecraft().setScreen(null);
            minecraft().options.hideGui = false;
            command("gamerule sendCommandFeedback false");
            command("time set noon");
            command("tp @s ~ ~ ~ 180 0");
            minecraft().gui.getChat().clearMessages(false);
            onServerPlayer(player -> {
                Reflection.callStatic(ASTRAL_RESEARCH, "wipeProgress", player);
                Reflection.callStatic(ASTRAL_RESEARCH, "setResearchProgress", player, Reflection.staticField(ASTRAL_TIER, "LUMINANCE"));
            });
        }, () -> script.ticks() >= ACTION_BAR_TICKS, 2 * ACTION_BAR_TICKS);
        script.then("capture astral_tier_reward", () -> capture("astral_tier_reward"));
        script.step("memorize Armara", () -> onServerPlayer(player -> Reflection.callStatic(ASTRAL_RESEARCH, "memorizeConstellation", player,
                ((Supplier<?>) Reflection.staticField(ASTRAL_CONSTELLATIONS, "ARMARA")).get())),
                () -> script.ticks() >= ACTION_BAR_TICKS, 2 * ACTION_BAR_TICKS);
        script.then("capture astral_constellation_reward", () -> capture("astral_constellation_reward"));
        script.step("midnight of Astral Sorcery's lunar eclipse", () -> {
            minecraft().options.hideGui = true;
            player().setXRot(SKY_PITCH);
            command("weather clear");
            long lunarEclipseDay = (minecraft().level.getDayTime() / DAY_TICKS / LUNAR_ECLIPSE_PERIOD_DAYS + 1) * LUNAR_ECLIPSE_PERIOD_DAYS;
            command("time set " + (lunarEclipseDay * DAY_TICKS + MIDNIGHT));
        }, () -> skyEvent() == SkyEvent.STAR_SHOWER && script.ticks() >= SKY_FADE_TICKS, 2 * SKY_FADE_TICKS);
        script.step("call falling stars", () -> {
            for (int star = 0; star < FALLING_STARS; star++) {
                command("celestialskilltree star @s");
            }
        }, () -> script.ticks() >= STAR_FLIGHT_TICKS, 2 * STAR_FLIGHT_TICKS);
        script.then("capture astral_lunar_shower", () -> capture("astral_lunar_shower"));
    }

    private static void onServerPlayer(Consumer<ServerPlayer> action) {
        MinecraftServer server = minecraft().getSingleplayerServer();
        UUID id = player().getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    void addShowcaseSteps() {
        String small = ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]}}";
        script.step("lay out the blocks and items", () -> {
            command("time set noon");
            command("weather clear");
            command("tp @s ~ ~ ~ 180 12");
            command("setblock ~-2 ~ ~-3 celestialskilltree:stellar_condenser");
            command("setblock ~ ~ ~-3 celestialskilltree:community_basin");
            command("setblock ~2 ~ ~-3 celestialskilltree:telescope[facing=north]");
            command("summon minecraft:item_display ~-1 ~2.3 ~-3 {item:{id:\"celestialskilltree:living_fragment\",count:1},billboard:\"center\"" + small);
            command("summon minecraft:item_display ~1 ~2.3 ~-3 {item:{id:\"celestialskilltree:stellar_ammo\",count:1},billboard:\"center\"" + small);
        }, () -> script.ticks() >= SKY_FADE_TICKS, 2 * SKY_FADE_TICKS);
        script.then("capture showcase", () -> capture("showcase"));
        script.step("look at the telescope from the side", () -> command("tp @s ~4.5 ~0.5 ~-3 90 15"),
                () -> script.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
        script.then("capture showcase_side", () -> capture("showcase_side"));
        script.step("look into the community basin", () -> command("tp @s 0.5 -58.2 -1 180 60"),
                () -> script.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
        script.then("capture showcase_basin", () -> capture("showcase_basin"));
    }
}
