package dev.nostalgiacraft.skills.gametest;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.compat.Integrations;
import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = NostalgiaSkills.MODID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public final class IntegrationGameTests {
    private static final List<String> PACK_MODS = List.of("apotheosis", "irons_spellbooks", "curios", "kubejs");
    private static final String KUBEJS_OBJECTIVE = "nostalgiaskills_kubejs";
    private static final String KUBEJS_PROBE_NOTABLE = "nostalgiatest:duelist/kubejs_probe_notable";
    private static final List<String> KUBEJS_EVENTS = List.of("pointsBought", "nodesLearned", "nodesRefunded", "buildReset",
            "skyEventChanged", "starLanded", "eliteSpawned");
    private static final String RELIC_EXPERIENCE_EVENT = "it.hurts.sskirillss.relics.api.events.leveling.ExperienceAddEvent";
    private static final int RELIC_EXPERIENCE = 400;

    private IntegrationGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (PACK_MODS.stream().allMatch(ModList.get()::isLoaded)) {
            event.register(IntegrationGameTests.class);
        }
    }

    @GameTest(templateNamespace = NostalgiaSkills.MODID, template = Scenario.EMPTY)
    public static void everyInstalledIntegrationMatchesTheInstalledVersion(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            Set<String> installed = Integrations.modIds().stream().filter(ModList.get()::isLoaded).collect(Collectors.toSet());
            scenario.check(!installed.isEmpty(), "no integrated mod is installed in this run");
            Set<String> disabled = OptionalEvents.disabled().stream().filter(installed::contains).collect(Collectors.toSet());
            scenario.check(disabled.isEmpty(), "integrations disabled against the installed versions: " + disabled);
        });
    }

    @GameTest(templateNamespace = NostalgiaSkills.MODID, template = Scenario.EMPTY)
    public static void experienceARelicGainsAlsoReachesItsBearer(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            if (!ModList.get().isLoaded("relics")) {
                return;
            }
            ServerPlayer bearer = scenario.blankPlayer();
            long before = Scenario.experience(bearer);
            NeoForge.EVENT_BUS.post(relicExperience(bearer, RELIC_EXPERIENCE));
            long shared = Math.round(RELIC_EXPERIENCE * ServerConfig.RELIC_XP_SHARE.get());
            scenario.same(before + shared, Scenario.experience(bearer), "experience after a relic gained " + RELIC_EXPERIENCE);
            scenario.check(!OptionalEvents.disabled().contains("relics"), "the relics integration stays enabled");
        });
    }

    @GameTest(templateNamespace = NostalgiaSkills.MODID, template = Scenario.EMPTY)
    public static void kubeJsScriptsReceiveEverySkillEvent(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            MinecraftServer server = helper.getLevel().getServer();
            Scoreboard scoreboard = server.getScoreboard();
            Objective objective = scoreboard.getObjective(KUBEJS_OBJECTIVE) != null ? scoreboard.getObjective(KUBEJS_OBJECTIVE)
                    : scoreboard.addObjective(KUBEJS_OBJECTIVE, ObjectiveCriteria.DUMMY, Component.literal(KUBEJS_OBJECTIVE),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
            ServerPlayer player = scenario.blankPlayer();
            List<String> nodes = List.of(Scenario.id("duelist/hub"));
            SkillEvents.post(new SkillEvents.PointsBought(player, 1, 100));
            SkillEvents.post(new SkillEvents.NodesLearned(player, nodes));
            SkillEvents.post(new SkillEvents.NodesRefunded(player, nodes, 1));
            SkillEvents.post(new SkillEvents.BuildReset(player, nodes, 1));
            SkillEvents.post(new SkillEvents.SkyEventChanged(server, SkyEvent.NONE, SkyEvent.NONE));
            SkillEvents.post(new SkillEvents.StarLanded(helper.getLevel(), new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.STICK))));
            SkillEvents.post(new SkillEvents.EliteSpawned(scenario.mob(EntityType.ZOMBIE)));
            for (String name : KUBEJS_EVENTS) {
                ReadOnlyScoreInfo seen = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), objective);
                scenario.check(seen != null && seen.value() > 0, "the KubeJS script did not receive " + name);
            }
            scenario.check(scenario.graph().contains(KUBEJS_PROBE_NOTABLE), "the KubeJS catalog event did not add its theme");
        });
    }

    private static Event relicExperience(ServerPlayer bearer, int amount) {
        try {
            return (Event) Class.forName(RELIC_EXPERIENCE_EVENT)
                    .getConstructor(LivingEntity.class, ItemStack.class, int.class)
                    .newInstance(bearer, ItemStack.EMPTY, amount);
        } catch (ReflectiveOperationException missing) {
            throw new IllegalStateException("the installed Relics has no " + RELIC_EXPERIENCE_EVENT, missing);
        }
    }
}
