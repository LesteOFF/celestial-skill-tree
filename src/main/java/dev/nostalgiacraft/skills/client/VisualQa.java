package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.screen.SkillTreeScreen;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.data.McKnownIds;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class VisualQa {
    private static final boolean ENABLED = Boolean.getBoolean("nostalgiaskills.visualqa");
    private static final int SETTLE_TICKS = 14;

    private record Shot(String name, int w, int h, int gui, String fixture, Consumer<SkillTreeScreen> setup) {
    }

    private static List<Shot> shots;
    private static int index = -1;
    private static int wait;
    private static SkillGraphSnapshot graph;
    private static boolean done;

    private VisualQa() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED || done) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (shots == null) {
            if (minecraft.getOverlay() != null || !(minecraft.screen instanceof TitleScreen || minecraft.screen != null)) {
                return;
            }
            if (SkillTreeManager.graph().isEmpty()) {
                return;
            }
            graph = SkillTreeManager.graph().get();
            shots = plan();
            NostalgiaSkills.LOGGER.info("Visual QA: {} shots planned", shots.size());
            next(minecraft);
            return;
        }
        if (--wait > 0) {
            if (wait == SETTLE_TICKS - 4 && minecraft.screen instanceof SkillTreeScreen screen) {
                shots.get(index).setup().accept(screen);
            }
            return;
        }
        Shot s = shots.get(index);
        String file = "nostalgiaskills_qa_" + s.name() + ".png";
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.getMainRenderTarget(), msg -> { });
        NostalgiaSkills.LOGGER.info("Visual QA: captured {} ({}x{} window, gui scale {}, framebuffer {}x{}, gui {}x{}, {} fps)",
                file, s.w(), s.h(), s.gui(), minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(),
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), minecraft.getFps());
        next(minecraft);
    }

    private static void next(Minecraft minecraft) {
        index++;
        if (index >= shots.size()) {
            NostalgiaSkills.LOGGER.info("Visual QA: fixture shots finished; starting in-world scenario");
            InWorldQa.start();
            done = true;
            return;
        }
        Shot s = shots.get(index);
        if (minecraft.getWindow().getWidth() != s.w() || minecraft.getWindow().getHeight() != s.h()) {
            minecraft.getWindow().setWindowed(s.w(), s.h());
        }
        minecraft.options.guiScale().set(s.gui());
        minecraft.resizeDisplay();
        ClientConfig.REDUCED_MOTION.set(s.name().contains("reduced_motion"));
        ClientConfig.HIGH_CONTRAST.set(s.name().contains("high_contrast"));
        loadFixture(s.fixture());
        minecraft.setScreen(new SkillTreeScreen());
        wait = SETTLE_TICKS;
    }

    private static String id(String path) {
        return NostalgiaSkills.MODID + ":" + path;
    }

    private static PlayerSkillState build(int points, String start, String... targets) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, points, 10_000, true);
        if (start != null) {
            AllocationService.confirm(graph, state, List.of(id(start)));
        }
        for (String t : targets) {
            PathFinder.route(graph, state, List.of(), id(t)).filter(PathFinder.Route::valid)
                    .ifPresent(r -> AllocationService.confirm(graph, state, r.newNodes()));
        }
        return state;
    }

    private static void loadFixture(String name) {
        PlayerSkillState state = switch (name) {
            case "fresh" -> build(0, null);
            case "focused" -> build(60, "guardian/hub", "guardian/bastion", "guardian/warden", "guardian/gatekeeper",
                    "guardian/shield_drill");
            case "hybrid" -> build(110, "duelist/hub", "duelist/swift_hands", "duelist/parry_and_return",
                    "berserker/hub", "berserker/rising_rage", "berserker/battle_cry");
            case "conflict" -> build(40, "duelist/hub", "duelist/swift_hands");
            case "insufficient" -> build(14, "marksman/hub", "marksman/eagle_eye");
            default -> build(0, null);
        };
        int level = name.equals("insufficient") ? 3 : 24;
        Payloads.Economy economy = new Payloads.Economy(100 + 20 * 0, 120, true, true, true, false);
        ClientSkillState.loadFixture(graph, state, economy, level, 0.4f);
    }

    private static List<String> routeTo(String fixtureTarget, PlayerSkillState state) {
        return PathFinder.route(graph, state, List.of(), id(fixtureTarget)).map(PathFinder.Route::newNodes).orElse(List.of());
    }

    private static List<Shot> plan() {
        List<Shot> l = new ArrayList<>();
        int[][] sizes = {{1000, 563}, {1280, 720}, {1920, 1080}};
        for (int[] sz : sizes) {
            for (int gui : new int[]{2, 3}) {
                l.add(new Shot("overview_" + sz[0] + "x" + sz[1] + "_gui" + gui, sz[0], sz[1], gui, "hybrid",
                        SkillTreeScreen::qaFit));
            }
        }
        l.add(new Shot("fresh_character_1280x720", 1280, 720, 2, "fresh", SkillTreeScreen::qaFit));
        l.add(new Shot("focused_guardian_1280x720", 1280, 720, 2, "focused", sc -> sc.qaZoom(1.1, "guardian/hub")));
        l.add(new Shot("hybrid_closeup_1280x720", 1280, 720, 2, "hybrid", sc -> sc.qaZoom(1.0, "duelist/hub")));
        l.add(new Shot("pending_changes_1280x720", 1280, 720, 2, "hybrid", sc -> {
            PlayerSkillState state = ClientSkillState.state().orElseThrow();
            sc.qaSetup(routeTo("berserker/frenzy", state), false, false);
            sc.qaZoom(0.9, "berserker/frenzy");
        }));
        l.add(new Shot("route_preview_tooltip_1280x720", 1280, 720, 2, "hybrid", sc -> {
            sc.qaZoom(0.9, "berserker/juggernaut");
            sc.qaHover(id("berserker/juggernaut"));
        }));
        l.add(new Shot("conflict_tooltip_1280x720", 1280, 720, 2, "conflict", sc -> {
            sc.qaZoom(1.4, "duelist/thousand_cuts");
            sc.qaHover(id("duelist/thousand_cuts"));
        }));
        l.add(new Shot("insufficient_points_1280x720", 1280, 720, 2, "insufficient", sc -> {
            PlayerSkillState state = ClientSkillState.state().orElseThrow();
            sc.qaSetup(routeTo("marksman/long_shot", state), false, false);
            sc.qaZoom(1.2, "marksman/nocking_point");
            sc.qaHover(id("marksman/nocking_point"));
        }));
        l.add(new Shot("stats_panel_1280x720", 1280, 720, 2, "hybrid", sc -> {
            sc.qaFit();
            sc.qaSetup(List.of(), true, false);
        }));
        l.add(new Shot("portal_tooltip_1280x720", 1280, 720, 2, "hybrid", sc -> {
            sc.qaZoom(0.6, "guardian/portal");
            sc.qaHover(id("guardian/portal"));
        }));
        l.add(new Shot("reset_panel_1280x720", 1280, 720, 2, "hybrid", sc -> {
            sc.qaFit();
            sc.qaSetup(List.of(), false, true);
        }));
        l.add(new Shot("refund_panel_1280x720", 1280, 720, 2, "focused", sc -> {
            PlayerSkillState state = ClientSkillState.state().orElseThrow();
            String target = state.orderedAllocations().get(1).nodeId();
            sc.qaZoom(1.1, target);
            sc.qaRefund(target);
        }));
        String[][] edges = {{"left", "0.03", "0.5"}, {"right", "0.97", "0.5"}, {"top", "0.5", "0.16"}, {"bottom", "0.5", "0.9"}};
        for (String[] e : edges) {
            l.add(new Shot("tooltip_edge_" + e[0] + "_1280x720", 1280, 720, 2, "hybrid", sc -> {
                sc.qaZoom(1.0, "duelist/en_garde");
                sc.qaHoverAt(id("duelist/en_garde"), Double.parseDouble(e[1]), Double.parseDouble(e[2]));
            }));
        }
        l.add(new Shot("reduced_motion_1280x720", 1280, 720, 2, "hybrid", SkillTreeScreen::qaFit));
        l.add(new Shot("high_contrast_1280x720", 1280, 720, 2, "hybrid", SkillTreeScreen::qaFit));
        l.add(new Shot("ultrawide_2560x1080_gui3", 2560, 1080, 3, "hybrid", SkillTreeScreen::qaFit));
        return l;
    }
}
