package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.screen.SkillTreeScreen;
import dev.nostalgiacraft.skills.client.screen.TreeScreenQa;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.network.Payloads;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, value = Dist.CLIENT)
public final class RenderBench {
    private static final boolean ENABLED = Boolean.getBoolean("nostalgiaskills.renderbench");
    private static final String REPORT_FILE = "nostalgiaskills_bench_client.txt";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int GUI_SCALE = 2;
    private static final int UNLIMITED_FPS = 260;
    private static final int SETTLE_TICKS = 40;
    private static final int FRAMES = 600;
    private static final int MEASURE_TIMEOUT_TICKS = 2_000;
    private static final int GRANTED_POINTS = 1_000_000;
    private static final int FIXTURE_LEVEL = 30;
    private static final float FIXTURE_PROGRESS = 0.5f;
    private static final Payloads.Economy ECONOMY = new Payloads.Economy(100, 120, true, true, true, false);
    private static final double CLOSEUP_ZOOM = 1.0;
    private static final double OVERVIEW_ZOOM = 0.3;

    private record Scene(String name, Consumer<SkillTreeScreen> frame) {
    }

    private static QaScript script;
    private static final List<Long> renderNanos = new ArrayList<>();
    private static final List<Long> frameNanos = new ArrayList<>();
    private static boolean recording;
    private static long renderStart;
    private static long lastFrameEnd;

    private RenderBench() {
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

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        renderStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void afterRender(ScreenEvent.Render.Post event) {
        long now = System.nanoTime();
        if (recording && event.getScreen() instanceof SkillTreeScreen) {
            renderNanos.add(now - renderStart);
            if (lastFrameEnd != 0) {
                frameNanos.add(now - lastFrameEnd);
            }
        }
        lastFrameEnd = now;
    }

    private static QaScript plan() {
        QaScript qa = new QaScript("Render bench", REPORT_FILE, InWorldQa::situation);
        new InWorldQa(qa).enterWorld();
        qa.then("unlock the frame rate", RenderBench::unlockFrameRate);
        for (Scene scene : scenes()) {
            qa.step("open " + scene.name(), () -> open(scene), () -> qa.ticks() >= SETTLE_TICKS, 2 * SETTLE_TICKS);
            qa.step("measure " + scene.name(), RenderBench::startRecording, () -> renderNanos.size() >= FRAMES, MEASURE_TIMEOUT_TICKS);
            qa.then("report " + scene.name(), () -> report(qa, scene));
        }
        return qa;
    }

    private static List<Scene> scenes() {
        return List.of(
                new Scene("whole tree (fit)", TreeScreenQa::fit),
                new Scene("closeup at a hub", screen -> TreeScreenQa.zoom(screen, CLOSEUP_ZOOM, ClientSkillState.graph().orElseThrow().roots().get(0))),
                new Scene("zoomed out with a tooltip", screen -> {
                    String hub = ClientSkillState.graph().orElseThrow().roots().get(0);
                    TreeScreenQa.zoom(screen, OVERVIEW_ZOOM, hub);
                    TreeScreenQa.hover(screen, hub);
                }),
                new Scene("whole tree with the stats panel", screen -> {
                    TreeScreenQa.fit(screen);
                    TreeScreenQa.setup(screen, List.of(), true, false);
                }));
    }

    private static void unlockFrameRate() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.enableVsync().set(false);
        minecraft.getWindow().updateVsync(false);
        minecraft.options.framerateLimit().set(UNLIMITED_FPS);
        minecraft.getWindow().setWindowed(WIDTH, HEIGHT);
        minecraft.options.guiScale().set(GUI_SCALE);
        minecraft.resizeDisplay();
    }

    private static void open(Scene scene) {
        SkillGraphSnapshot graph = ClientSkillState.graph().orElseThrow();
        ClientSkillState.loadFixture(graph, fullBuild(graph), ECONOMY, FIXTURE_LEVEL, FIXTURE_PROGRESS);
        SkillTreeScreen screen = new SkillTreeScreen();
        Minecraft.getInstance().setScreen(screen);
        scene.frame().accept(screen);
    }

    private static PlayerSkillState fullBuild(SkillGraphSnapshot graph) {
        PlayerSkillState state = new PlayerSkillState();
        LedgerService.grant(state, GRANTED_POINTS, GRANTED_POINTS, true);
        Deque<String> frontier = new ArrayDeque<>(List.of(graph.roots().get(0)));
        Set<String> seen = new HashSet<>(frontier);
        while (!frontier.isEmpty()) {
            String id = frontier.poll();
            if (AllocationService.confirm(graph, state, List.of(id)).ok()) {
                graph.neighbours(id).stream().filter(seen::add).forEach(frontier::add);
            }
        }
        return state;
    }

    private static void startRecording() {
        renderNanos.clear();
        frameNanos.clear();
        lastFrameEnd = 0;
        recording = true;
    }

    private static void report(QaScript qa, Scene scene) {
        recording = false;
        qa.note(String.format("%-34s screen render %6.2f ms median, %6.2f ms p95 | frame %6.2f ms median (%5.0f fps)",
                scene.name(), millis(renderNanos, 50), millis(renderNanos, 95), millis(frameNanos, 50), 1000 / millis(frameNanos, 50)));
    }

    private static double millis(List<Long> samples, int percentile) {
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return sorted.get(Math.min(sorted.size() - 1, sorted.size() * percentile / 100)) / 1_000_000.0;
    }
}
