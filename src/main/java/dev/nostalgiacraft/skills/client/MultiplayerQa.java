package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.InWorldQa.Snapshot;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.Allocation;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class MultiplayerQa {
    private static final String ROLE = System.getProperty("nostalgiaskills.mpqa", "");
    private static final String ADDRESS = System.getProperty("nostalgiaskills.mpqa.address", "127.0.0.1:25565");
    private static final String GUARDIAN = "nostalgiaskills:guardian/";
    private static final String GUARDIAN_HUB = GUARDIAN + "hub";
    private static final String GUARDIAN_TARGET = GUARDIAN + "gatekeeper";
    private static final String MARKSMAN_HUB = "nostalgiaskills:marksman/hub";
    private static final String MARKSMAN_TARGET = "nostalgiaskills:marksman/eagle_eye";
    private static final String ACCEPTED = "ok";
    private static final int GUARDIAN_SKILLS = 4;
    private static final int MARKSMAN_SKILLS = 5;
    private static final int ALPHA_POINTS = 3;
    private static final int BETA_POINTS = 4;
    private static final int BUY_BURST = 12;
    private static final int REFUND_BURST = 5;
    private static final int REPLAYED_REQUESTS = 2;
    private static final int CONNECT_TICKS = 6000;
    private static final int SERVER_ANSWER_TICKS = 1200;
    private static final int PORTAL_TICKS = 2400;
    private static final int COMMAND_TICKS = 400;
    private static final int COOL_DOWN_TICKS = 60;

    private static final List<String> ANSWERS = new ArrayList<>();
    private static QaScript script;
    private static Snapshot before;
    private static List<String> route = List.of();
    private static String target = "";
    private static double projectileBefore;

    private MultiplayerQa() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (ROLE.isEmpty()) {
            return;
        }
        if (script == null) {
            if (!(minecraft().screen instanceof TitleScreen)) {
                return;
            }
            script = plan();
        }
        script.tick();
    }

    private static QaScript plan() {
        script = new QaScript("Multiplayer QA [" + ROLE + "]", "nostalgiaskills_mpqa_" + ROLE + ".txt", InWorldQa::situation);
        addConnectStep();
        switch (ROLE) {
            case "alpha" -> addAlphaSteps();
            case "beta" -> addBetaSteps();
            case "alpha_verify", "beta_verify" -> addVerifySteps();
            default -> script.then("reject the role", () -> script.expect("role " + ROLE + " is known", false));
        }
        return script;
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static PlayerSkillState state() {
        return ClientSkillState.state().orElseThrow();
    }

    private static double maxHealth() {
        return minecraft().player.getAttributeValue(Attributes.MAX_HEALTH);
    }

    private static double projectileDamage() {
        return minecraft().player.getAttributeValue(ModAttributes.PROJECTILE_DAMAGE);
    }

    private static boolean healthIs(double expected) {
        return Math.abs(maxHealth() - expected) < 1e-4;
    }

    private static void command(String command) {
        minecraft().player.connection.sendCommand(command);
    }

    private static void remember() {
        before = Snapshot.take();
        projectileBefore = projectileDamage();
    }

    private static String summary() {
        PlayerSkillState state = state();
        return "player=" + minecraft().player.getGameProfile().getName() + ", purchased=" + state.purchasedPoints() + ", available="
                + state.availablePoints() + ", skills=" + new TreeSet<>(state.allocations().keySet()) + ", max health=" + maxHealth()
                + ", projectile damage=" + projectileDamage();
    }

    private static void expectUnchanged(String when) {
        script.note(when + ": " + summary());
        script.expect("skills learned " + when, before.allocations(), state().allocations().size());
        script.expect("points available " + when, before.available(), state().availablePoints());
        script.expectNear("max health " + when, before.maxHealth(), maxHealth());
        script.expect("ledger holds " + when, state().ledgerHolds());
    }

    private static void send(Function<UUID, CustomPacketPayload> request) {
        UUID id = UUID.randomUUID();
        ClientSkillState.markInFlight(id);
        PacketDistributor.sendToServer(request.apply(id));
    }

    private static void burst(int requests, Function<UUID, CustomPacketPayload> request) {
        ANSWERS.clear();
        ClientSkillState.setListeners(result -> ANSWERS.add(result.ok() ? ACCEPTED : result.messageKey()), () -> { });
        UUID replayed = UUID.randomUUID();
        for (int sent = 0; sent < requests; sent++) {
            PacketDistributor.sendToServer(request.apply(sent < REPLAYED_REQUESTS ? replayed : UUID.randomUUID()));
        }
    }

    private static void expectOneAccepted(String what, int requests) {
        ClientSkillState.clearListeners();
        Map<String, Integer> counts = new TreeMap<>();
        ANSWERS.forEach(answer -> counts.merge(answer, 1, Integer::sum));
        script.note("answers to " + what + ": " + counts);
        script.expect(what + " accepted out of " + requests, 1, counts.getOrDefault(ACCEPTED, 0).intValue());
    }

    private static List<String> routeFrom(String hub, String targetId, int limit) {
        SkillGraphSnapshot graph = ClientSkillState.graph().orElseThrow();
        PlayerSkillState simulated = state().copy();
        AllocationService.confirm(graph, simulated, List.of(hub));
        List<String> nodes = new ArrayList<>(List.of(hub));
        PathFinder.route(graph, simulated, List.of(), targetId).ifPresent(found -> nodes.addAll(found.newNodes()));
        return List.copyOf(nodes.subList(0, Math.min(limit, nodes.size())));
    }

    private static boolean learnedRoute() {
        return route.stream().allMatch(state()::isAllocated);
    }

    private static void confirmRoute() {
        send(id -> new Payloads.ConfirmAllocation(id, state().stateRevision(), ClientSkillState.graphRevision(), route));
        script.note("confirm sent for " + route);
    }

    private static void addConnectStep() {
        script.step("connect to " + ADDRESS, () -> {
            ClientSkillState.reset();
            ServerData server = new ServerData("nostalgiaskills qa", ADDRESS, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), minecraft(), ServerAddress.parseString(ADDRESS), server, false, null);
        }, () -> InWorldQa.joined() && minecraft().getConnection() != null, CONNECT_TICKS);
    }

    private static void addPeerSteps() {
        script.waitFor("wait for the other player", () -> minecraft().getConnection().getOnlinePlayers().size() >= 2, CONNECT_TICKS);
        script.then("report the join", () -> script.note("joined the dedicated server: " + summary()));
        script.step("grant 30 levels", () -> command("xp add @s 30 levels"), () -> minecraft().player.experienceLevel >= 30, COMMAND_TICKS);
    }

    private static void addPurchaseSteps(int points) {
        script.step("buy " + points + " points one at a time", MultiplayerQa::remember, () -> {
            if (state().purchasedPoints() >= before.purchased() + points) {
                return true;
            }
            if (!ClientSkillState.requestInFlight()) {
                send(id -> new Payloads.BuyPoint(id, state().stateRevision(), 1));
            }
            return false;
        }, SERVER_ANSWER_TICKS);
        script.then("check the purchases", () -> {
            long cost = ClientSkillState.curve().totalCost(before.purchased(), points);
            script.expect("points available", before.available() + points, state().availablePoints());
            script.expect("XP left after paying " + cost, before.xp() - cost, ClientSkillState.xpBalance());
        });
    }

    private static void addVerifySteps() {
        script.pause("let the state settle", COOL_DOWN_TICKS);
        script.then("report after the server restart", () -> {
            script.note("after the dedicated server restart: " + summary());
            script.expect("ledger holds after the server restart", state().ledgerHolds());
        });
    }

    private static void addAlphaSteps() {
        addPeerSteps();
        addPurchaseSteps(ALPHA_POINTS);
        script.step("learn a guardian route", () -> {
            remember();
            route = routeFrom(GUARDIAN_HUB, GUARDIAN_TARGET, GUARDIAN_SKILLS);
            confirmRoute();
        }, () -> learnedRoute() && maxHealth() > before.maxHealth(), SERVER_ANSWER_TICKS);
        script.then("check the guardian build", () -> {
            script.note("after confirming: " + summary());
            script.expect("skills learned", route.size(), state().allocations().size());
            script.expect("ledger holds after confirming", state().ledgerHolds());
        });
        script.step("log out", () -> {
            remember();
            minecraft().level.disconnect();
            minecraft().disconnect(new TitleScreen());
        }, () -> minecraft().level == null && minecraft().getConnection() == null, SERVER_ANSWER_TICKS);
        addConnectStep();
        script.waitFor("effects return after the login", () -> healthIs(before.maxHealth()), COMMAND_TICKS);
        script.then("check the build after logging in again", () -> expectUnchanged("after logging out and in"));
        script.step("go to the End", () -> command("execute in minecraft:the_end run tp @s 100 52 0"),
                () -> minecraft().level.dimension() == Level.END && healthIs(before.maxHealth()), SERVER_ANSWER_TICKS);
        script.then("check the build in the End", () -> expectUnchanged("in the End"));
        script.step("leave the End through an exit portal", () -> command("execute at @s run fill ~-1 ~-4 ~-1 ~1 ~1 ~1 minecraft:end_portal"),
                MultiplayerQa::returnedFromTheEnd, PORTAL_TICKS);
        script.then("check the build after the credits", () -> expectUnchanged("after the End exit portal"));
        script.step("refund the newest skill", () -> {
            remember();
            List<Allocation> learned = state().orderedAllocations();
            target = learned.get(learned.size() - 1).nodeId();
            send(id -> new Payloads.RequestRefund(id, state().stateRevision(), ClientSkillState.graphRevision(), target));
        }, () -> !state().isAllocated(target) && maxHealth() < before.maxHealth(), SERVER_ANSWER_TICKS);
        script.then("check the refund", () -> {
            script.note("after refunding " + target + ": " + summary());
            script.expect("skills learned after the refund", before.allocations() - 1, state().allocations().size());
            script.expect("ledger holds after the refund", state().ledgerHolds());
        });
    }

    private static boolean returnedFromTheEnd() {
        if (minecraft().screen instanceof WinScreen credits) {
            credits.onClose();
        }
        return minecraft().level != null && minecraft().level.dimension() == Level.OVERWORLD && minecraft().player.isAlive()
                && ClientSkillState.ready() && healthIs(before.maxHealth());
    }

    private static void addBetaSteps() {
        addPeerSteps();
        addPurchaseSteps(BETA_POINTS);
        script.step("send " + BUY_BURST + " buy requests in one tick", () -> {
            remember();
            long revision = state().stateRevision();
            burst(BUY_BURST, id -> new Payloads.BuyPoint(id, revision, 1));
        }, () -> ANSWERS.size() >= BUY_BURST, SERVER_ANSWER_TICKS);
        script.then("check the buy burst", () -> {
            expectOneAccepted("buy requests", BUY_BURST);
            script.expect("points purchased by the burst", before.purchased() + 1, state().purchasedPoints());
            script.expect("ledger holds after the buy burst", state().ledgerHolds());
        });
        script.pause("cool down after the buy burst", COOL_DOWN_TICKS);
        script.step("learn a marksman route", () -> {
            remember();
            route = routeFrom(MARKSMAN_HUB, MARKSMAN_TARGET, MARKSMAN_SKILLS);
            confirmRoute();
        }, () -> learnedRoute() && projectileDamage() > 0, SERVER_ANSWER_TICKS);
        script.then("check the marksman build", () -> {
            script.note("after confirming: " + summary());
            script.expect("builds are per player: no guardian skill here",
                    state().allocations().keySet().stream().noneMatch(skill -> skill.startsWith(GUARDIAN)));
            script.expect("ledger holds after confirming", state().ledgerHolds());
        });
        script.pause("cool down after confirming", COOL_DOWN_TICKS);
        script.step("send " + REFUND_BURST + " refund requests for one mid-branch skill", () -> {
            remember();
            target = state().orderedAllocations().get(2).nodeId();
            long revision = state().stateRevision();
            int graphRevision = ClientSkillState.graphRevision();
            burst(REFUND_BURST, id -> new Payloads.RequestRefund(id, revision, graphRevision, target));
        }, () -> ANSWERS.size() >= REFUND_BURST, SERVER_ANSWER_TICKS);
        script.then("check the refund burst", () -> {
            expectOneAccepted("refund requests", REFUND_BURST);
            script.note("after refunding " + target + ": " + summary());
            script.expect("the refunded skill is gone", !state().isAllocated(target));
            script.expect("no point was created or lost", before.available() + before.spent(),
                    state().availablePoints() + state().activeSpentPoints());
            script.expect("its bonus is gone", projectileDamage() <= projectileBefore);
            script.expect("ledger holds after the refund burst", state().ledgerHolds());
        });
        script.pause("cool down after the refund burst", COOL_DOWN_TICKS);
        script.step("learn the refunded branch again", MultiplayerQa::confirmMissingRoute, MultiplayerQa::learnedRoute, SERVER_ANSWER_TICKS);
        script.then("check the build learned again", () -> {
            script.note("after learning the branch again: " + summary());
            script.expect("ledger holds after learning again", state().ledgerHolds());
        });
    }

    private static void confirmMissingRoute() {
        List<String> missing = route.stream().filter(skill -> !state().isAllocated(skill)).toList();
        send(id -> new Payloads.ConfirmAllocation(id, state().stateRevision(), ClientSkillState.graphRevision(), missing));
        script.note("confirm sent for " + missing);
    }
}
