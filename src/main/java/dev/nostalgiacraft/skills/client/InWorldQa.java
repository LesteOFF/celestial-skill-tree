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
                + ClientSkillState.economy().nextPointCost() + " (curve not restarted: expected 292), max health="
                + mc().player.getAttributeValue(Attributes.MAX_HEALTH) + ", starting root free again=" + st().startingRoot().isEmpty()
                + ", ledger ok=" + st().ledgerHolds()), () -> true, 5));
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
