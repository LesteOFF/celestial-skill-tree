package dev.nostalgiacraft.skills.client;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class QaScript {
    private record Step(String name, Runnable action, BooleanSupplier done, int timeoutTicks) {
    }

    private static final int INSTANT_TICKS = 5;
    private static final double TOLERANCE = 1e-4;
    private static final String SCREENSHOT_DIRECTORY = "screenshots";

    private final String title;
    private final String reportFile;
    private final Supplier<String> situation;
    private final long startedAt = System.currentTimeMillis();
    private final List<Step> steps = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private int index;
    private int ticks;
    private int checks;
    private int failures;
    private boolean started;
    private boolean finished;

    QaScript(String title, String reportFile, Supplier<String> situation) {
        this.title = title;
        this.reportFile = reportFile;
        this.situation = situation;
    }

    void step(String name, Runnable action, BooleanSupplier done, int timeoutTicks) {
        steps.add(new Step(name, action, done, timeoutTicks));
    }

    void then(String name, Runnable action) {
        step(name, action, () -> true, INSTANT_TICKS);
    }

    void waitFor(String name, BooleanSupplier done, int timeoutTicks) {
        step(name, () -> { }, done, timeoutTicks);
    }

    void pause(String name, int forTicks) {
        step(name, () -> { }, () -> ticks >= forTicks, 2 * forTicks);
    }

    int ticks() {
        return ticks;
    }

    void note(String line) {
        report.add(line);
        NostalgiaSkills.LOGGER.info("{}: {}", title, line);
    }

    void expect(String what, boolean holds) {
        record(what, holds, "");
    }

    void expect(String what, long expected, long actual) {
        record(what, expected == actual, ": expected " + expected + " but was " + actual);
    }

    void expect(String what, Object expected, Object actual) {
        record(what, Objects.equals(expected, actual), ": expected " + expected + " but was " + actual);
    }

    void expectNear(String what, double expected, double actual) {
        record(what, Math.abs(expected - actual) < TOLERANCE, ": expected " + expected + " but was " + actual);
    }

    private void record(String what, boolean passed, String difference) {
        checks++;
        if (passed) {
            note("PASS " + what);
            return;
        }
        failures++;
        note("FAIL " + what + difference);
    }

    void capture(String fileName) {
        Minecraft minecraft = Minecraft.getInstance();
        screenshots.add(fileName);
        Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(), message -> { });
    }

    void tick() {
        if (finished) {
            return;
        }
        if (index >= steps.size()) {
            finish();
            return;
        }
        Step current = steps.get(index);
        try {
            advance(current);
        } catch (RuntimeException crash) {
            NostalgiaSkills.LOGGER.error("{} step failed", title, crash);
            abort(current, "exception " + crash);
        }
    }

    private void advance(Step current) {
        if (!started) {
            started = true;
            current.action().run();
            return;
        }
        ticks++;
        if (current.done().getAsBoolean()) {
            next(index + 1);
        } else if (ticks > current.timeoutTicks()) {
            abort(current, "timeout after " + current.timeoutTicks() + " ticks");
        }
    }

    private void next(int nextIndex) {
        index = nextIndex;
        ticks = 0;
        started = false;
    }

    private void abort(Step current, String reason) {
        failures++;
        note("ABORTED at step '" + current.name() + "': " + reason + "; " + situation.get());
        next(steps.size());
    }

    private void finish() {
        finished = true;
        screenshots.forEach(fileName -> expect("screenshot " + fileName + " was written", writtenDuringThisRun(fileName)));
        String verdict = failures == 0 ? "PASSED" : "FAILED";
        report.add(0, "RESULT: " + verdict + " (" + checks + " checks, " + failures + " failures)");
        NostalgiaSkills.LOGGER.info("{}: {}", title, report.get(0));
        write();
        Minecraft.getInstance().stop();
    }

    private boolean writtenDuringThisRun(String fileName) {
        Path file = gameDirectory().resolve(SCREENSHOT_DIRECTORY).resolve(fileName);
        return file.toFile().lastModified() >= startedAt;
    }

    private void write() {
        try {
            Files.write(gameDirectory().resolve(reportFile), report);
        } catch (IOException unwritable) {
            NostalgiaSkills.LOGGER.error("could not write {} report", title, unwritable);
        }
    }

    private static Path gameDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }
}
