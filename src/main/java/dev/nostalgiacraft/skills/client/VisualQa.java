package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.screen.SkillTreeScreen;
import dev.nostalgiacraft.skills.config.ClientConfig;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class VisualQa {
    private static final boolean ENABLED = Boolean.getBoolean("nostalgiaskills.visualqa");
    private static final String REPORT_FILE = "nostalgiaskills_qa_ingame.txt";
    private static final String SHOT_PREFIX = "nostalgiaskills_qa_";
    private static final int OPEN_TICKS = 4;
    private static final int FRAME_TICKS = 10;
    private static final int SYNC_TICKS = 400;
    private static final int FIXTURE_POINT_CAP = 10_000;
    private static final float FIXTURE_XP_PROGRESS = 0.4f;
    private static final Payloads.Economy FIXTURE_ECONOMY = new Payloads.Economy(100, 120, true, true, true, false);
    private static final Resolution HD = new Resolution(1280, 720, 2);
    private static final Resolution ULTRAWIDE = new Resolution(2560, 1080, 3);
    private static final List<Resolution> OVERVIEWS = List.of(new Resolution(1000, 563, 2), new Resolution(1000, 563, 3),
            HD, new Resolution(1280, 720, 3), new Resolution(1920, 1080, 2), new Resolution(1920, 1080, 3));
    private static final List<Edge> SCREEN_EDGES = List.of(new Edge("left", 0.03, 0.5), new Edge("right", 0.97, 0.5),
            new Edge("top", 0.5, 0.16), new Edge("bottom", 0.5, 0.9));

    private record Resolution(int width, int height, int guiScale) {
        String size() {
            return width + "x" + height;
        }
    }

    private record Edge(String name, double widthFraction, double heightFraction) {
    }

    private enum Look {
        NORMAL, REDUCED_MOTION, HIGH_CONTRAST
    }

    private enum Fixture {
        FRESH(0, 24),
        FOCUSED(60, 24, "guardian/hub", "guardian/bastion", "guardian/warden", "guardian/gatekeeper", "guardian/shield_drill"),
        HYBRID(110, 24, "duelist/hub", "duelist/swift_hands", "duelist/parry_and_return", "berserker/hub", "berserker/rising_rage",
                "berserker/battle_cry"),
        CONFLICT(40, 24, "duelist/hub", "duelist/swift_hands"),
        INSUFFICIENT(14, 3, "marksman/hub", "marksman/eagle_eye");

        private final int points;
        private final int level;
        private final List<String> skills;

        Fixture(int points, int level, String... skills) {
            this.points = points;
            this.level = level;
            this.skills = List.of(skills);
        }

        PlayerSkillState build() {
            PlayerSkillState state = new PlayerSkillState();
            LedgerService.grant(state, points, FIXTURE_POINT_CAP, true);
            skills.forEach(skill -> learn(state, id(skill)));
            return state;
        }

        private static void learn(PlayerSkillState state, String skillId) {
            if (state.startingRoot().isEmpty()) {
                AllocationService.confirm(graph, state, List.of(skillId));
                return;
            }
            PathFinder.route(graph, state, List.of(), skillId).filter(PathFinder.Route::valid)
                    .ifPresent(route -> AllocationService.confirm(graph, state, route.newNodes()));
        }
    }

    private record Shot(String name, Resolution resolution, Fixture fixture, Look look, Consumer<SkillTreeScreen> frame) {
        Shot(String name, Fixture fixture, Consumer<SkillTreeScreen> frame) {
            this(name + "_" + HD.size(), HD, fixture, Look.NORMAL, frame);
        }
    }

    private static QaScript script;
    private static SkillGraphSnapshot graph;

    private VisualQa() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        if (script == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getOverlay() != null || minecraft.screen == null) {
                return;
            }
            script = plan();
        }
        script.tick();
    }

    private static QaScript plan() {
        QaScript qa = new QaScript("Visual QA", REPORT_FILE, InWorldQa::situation);
        InWorldQa world = new InWorldQa(qa);
        world.enterWorld();
        qa.then("check the fixture builds", () -> checkFixtures(qa));
        shots().forEach(shot -> addShot(qa, shot));
        qa.step("return to the live build", VisualQa::leaveFixtures, InWorldQa::joined, SYNC_TICKS);
        world.addScenario();
        return qa;
    }

    private static void checkFixtures(QaScript qa) {
        graph = ClientSkillState.graph().orElseThrow();
        for (Fixture fixture : Fixture.values()) {
            PlayerSkillState state = fixture.build();
            fixture.skills.forEach(skill -> qa.expect("fixture " + fixture + " learns " + skill, state.isAllocated(id(skill))));
        }
    }

    private static void addShot(QaScript qa, Shot shot) {
        qa.step("open " + shot.name(), () -> open(shot), () -> qa.ticks() >= OPEN_TICKS, 2 * OPEN_TICKS);
        qa.step("frame " + shot.name(), () -> shot.frame().accept(screen()), () -> qa.ticks() >= FRAME_TICKS, 2 * FRAME_TICKS);
        qa.then("capture " + shot.name(), () -> qa.capture(SHOT_PREFIX + shot.name() + ".png"));
    }

    private static void open(Shot shot) {
        resize(shot.resolution());
        ClientConfig.REDUCED_MOTION.set(shot.look() == Look.REDUCED_MOTION);
        ClientConfig.HIGH_CONTRAST.set(shot.look() == Look.HIGH_CONTRAST);
        ClientSkillState.loadFixture(graph, shot.fixture().build(), FIXTURE_ECONOMY, shot.fixture().level, FIXTURE_XP_PROGRESS);
        Minecraft.getInstance().setScreen(new SkillTreeScreen());
    }

    private static void resize(Resolution resolution) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow().getWidth() != resolution.width() || minecraft.getWindow().getHeight() != resolution.height()) {
            minecraft.getWindow().setWindowed(resolution.width(), resolution.height());
        }
        minecraft.options.guiScale().set(resolution.guiScale());
        minecraft.resizeDisplay();
    }

    private static void leaveFixtures() {
        Minecraft.getInstance().setScreen(null);
        ClientConfig.REDUCED_MOTION.set(false);
        ClientConfig.HIGH_CONTRAST.set(false);
        resize(HD);
        ClientSkillState.reset();
        PacketDistributor.sendToServer(new Payloads.RequestSync(""));
    }

    private static SkillTreeScreen screen() {
        return (SkillTreeScreen) Minecraft.getInstance().screen;
    }

    private static String id(String path) {
        return NostalgiaSkills.MODID + ":" + path;
    }

    private static String inTree(String path) {
        script.expect("the tree has " + path, graph.contains(id(path)));
        return id(path);
    }

    private static void select(SkillTreeScreen screen, String targetPath) {
        PlayerSkillState state = ClientSkillState.state().orElseThrow();
        List<String> route = PathFinder.route(graph, state, List.of(), inTree(targetPath)).map(PathFinder.Route::newNodes).orElse(List.of());
        script.expect("a route to " + targetPath + " is selected", !route.isEmpty());
        screen.qaSetup(route, false, false);
    }

    private static void hover(SkillTreeScreen screen, double zoom, String path) {
        screen.qaZoom(zoom, path);
        screen.qaHover(inTree(path));
    }

    private static List<Shot> shots() {
        List<Shot> shots = new ArrayList<>();
        OVERVIEWS.forEach(resolution -> shots.add(new Shot("overview_" + resolution.size() + "_gui" + resolution.guiScale(), resolution,
                Fixture.HYBRID, Look.NORMAL, SkillTreeScreen::qaFit)));
        shots.add(new Shot("fresh_character", Fixture.FRESH, SkillTreeScreen::qaFit));
        shots.add(new Shot("focused_guardian", Fixture.FOCUSED, screen -> screen.qaZoom(1.1, "guardian/hub")));
        shots.add(new Shot("hybrid_closeup", Fixture.HYBRID, screen -> screen.qaZoom(1.0, "duelist/hub")));
        shots.add(new Shot("pending_changes", Fixture.HYBRID, screen -> {
            select(screen, "berserker/frenzy");
            screen.qaZoom(0.9, "berserker/frenzy");
        }));
        shots.add(new Shot("route_preview_tooltip", Fixture.HYBRID, screen -> hover(screen, 0.9, "berserker/juggernaut")));
        shots.add(new Shot("conflict_tooltip", Fixture.CONFLICT, screen -> hover(screen, 1.4, "duelist/thousand_cuts")));
        shots.add(new Shot("insufficient_points", Fixture.INSUFFICIENT, screen -> {
            select(screen, "marksman/long_shot");
            hover(screen, 1.2, "marksman/nocking_point");
        }));
        shots.add(new Shot("stats_panel", Fixture.HYBRID, screen -> {
            screen.qaFit();
            screen.qaSetup(List.of(), true, false);
        }));
        shots.add(new Shot("portal_tooltip", Fixture.HYBRID, screen -> hover(screen, 0.6, "guardian/portal")));
        shots.add(new Shot("reset_panel", Fixture.HYBRID, screen -> {
            screen.qaFit();
            screen.qaSetup(List.of(), false, true);
        }));
        shots.add(new Shot("refund_panel", Fixture.FOCUSED, screen -> {
            String nextToHub = ClientSkillState.state().orElseThrow().orderedAllocations().get(1).nodeId();
            screen.qaZoom(1.1, nextToHub);
            screen.qaRefund(nextToHub);
        }));
        SCREEN_EDGES.forEach(edge -> shots.add(new Shot("tooltip_edge_" + edge.name(), Fixture.HYBRID, screen -> {
            screen.qaZoom(1.0, "duelist/en_garde");
            screen.qaHoverAt(inTree("duelist/en_garde"), edge.widthFraction(), edge.heightFraction());
        })));
        shots.add(new Shot("reduced_motion_" + HD.size(), HD, Fixture.HYBRID, Look.REDUCED_MOTION, SkillTreeScreen::qaFit));
        shots.add(new Shot("high_contrast_" + HD.size(), HD, Fixture.HYBRID, Look.HIGH_CONTRAST, SkillTreeScreen::qaFit));
        shots.add(new Shot("ultrawide_" + ULTRAWIDE.size() + "_gui" + ULTRAWIDE.guiScale(), ULTRAWIDE, Fixture.HYBRID, Look.NORMAL,
                SkillTreeScreen::qaFit));
        return shots;
    }
}
