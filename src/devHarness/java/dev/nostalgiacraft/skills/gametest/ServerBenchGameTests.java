package dev.nostalgiacraft.skills.gametest;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.effects.CompiledBuild;
import dev.nostalgiacraft.skills.effects.EffectEngine;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import dev.nostalgiacraft.skills.effects.GameplayEvents;
import dev.nostalgiacraft.skills.effects.Triggers;
import dev.nostalgiacraft.skills.effects.WeaponClasses;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.DiscoveryService;
import dev.nostalgiacraft.skills.progression.LedgerService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public final class ServerBenchGameTests {
    private static final boolean ENABLED = Boolean.getBoolean("nostalgiaskills.bench");
    private static final String REPORT_FILE = "nostalgiaskills_bench_server.txt";
    private static final int ROUNDS = 7;
    private static volatile Object sink;
    private static final long ROUND_BUDGET_NANOS = 100_000_000L;
    private static final int GRANTED_POINTS = 1_000_000;
    private static final int ELITE_NODES = 8;

    private ServerBenchGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (ENABLED) {
            event.register(ServerBenchGameTests.class);
        }
    }

    @GameTest(templateNamespace = NostalgiaSkills.MODID, template = Scenario.EMPTY, timeoutTicks = 400)
    public static void timeTheServerHotPaths(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            SkillGraphSnapshot graph = scenario.graph();
            int learned = learnEverything(graph, ModAttachments.state(player));
            EffectEngine.refresh(player);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            player.setItemSlot(EquipmentSlot.MAINHAND, sword);
            ItemStack worn = sword.copy();
            worn.setDamageValue(1);
            List<String> report = new ArrayList<>();
            report.add("full build: " + learned + " of " + graph.nodes().size() + " skills, " + EffectEngine.build(player).triggers().size() + " triggers");
            report.add(measure("Triggers.fire (on_hurt)", () -> { Triggers.fire(player, "on_hurt", null); return player; }));
            report.add(measure("CompiledBuild.compile", () -> CompiledBuild.compile(graph, ModAttachments.state(player))));
            report.add(measure("EffectEngine.refresh", () -> { EffectEngine.refresh(player); return player; }));
            report.add(measure("equipment change (durability only)", () -> {
                GameplayEvents.onEquipmentChange(new LivingEquipmentChangeEvent(player, EquipmentSlot.MAINHAND, sword, worn));
                return player;
            }));
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
            report.add(measure("BreakSpeed (pickaxe on stone)", () -> {
                PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 1f, BlockPos.ZERO);
                GameplayEvents.onBreakSpeed(event);
                return event.getNewSpeed();
            }));
            report.add(measure("WeaponClasses.isWeapon", () -> WeaponClasses.isWeapon(sword)));
            report.add(measure("DiscoveryService.keys", () -> DiscoveryService.keys(graph)));
            report.add(measure("Reflection.call", () -> Reflection.call("bench", "length")));
            report.add(measure("EliteMobs.empower (incl. zombie creation)", () -> {
                Zombie zombie = EntityType.ZOMBIE.create(scenario.level());
                return EliteMobs.empower(zombie, zombie.getRandom(), ELITE_NODES);
            }));
            report.forEach(NostalgiaSkills.LOGGER::info);
            write(report);
        });
    }

    private static int learnEverything(SkillGraphSnapshot graph, PlayerSkillState state) {
        LedgerService.grant(state, GRANTED_POINTS, GRANTED_POINTS, true);
        Deque<String> frontier = new ArrayDeque<>(List.of(graph.roots().get(0)));
        Set<String> seen = new HashSet<>(frontier);
        int learned = 0;
        while (!frontier.isEmpty()) {
            String id = frontier.poll();
            if (AllocationService.confirm(graph, state, List.of(id)).ok()) {
                learned++;
                graph.neighbours(id).stream().filter(seen::add).forEach(frontier::add);
            }
        }
        return learned;
    }

    private static String measure(String label, Supplier<?> work) {
        int iterations = warmUp(work);
        double[] perCall = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            long roundStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                sink = work.get();
            }
            perCall[round] = (System.nanoTime() - roundStart) / 1_000.0 / iterations;
        }
        Arrays.sort(perCall);
        return String.format("%-45s %10.2f us/call (median of %d x %d)", label, perCall[ROUNDS / 2], ROUNDS, iterations);
    }

    private static int warmUp(Supplier<?> work) {
        long start = System.nanoTime();
        int calls = 0;
        while (System.nanoTime() - start < ROUND_BUDGET_NANOS) {
            sink = work.get();
            calls++;
        }
        return calls;
    }

    private static void write(List<String> report) {
        try {
            Files.write(Path.of(REPORT_FILE), report);
        } catch (IOException unwritable) {
            NostalgiaSkills.LOGGER.error("could not write the server bench report", unwritable);
        }
    }
}
