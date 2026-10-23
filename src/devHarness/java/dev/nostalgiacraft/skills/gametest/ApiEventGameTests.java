package dev.nostalgiacraft.skills.gametest;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.effects.EliteMobs;
import dev.nostalgiacraft.skills.entity.FallingStar;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.registry.StarFragmentItem;
import dev.nostalgiacraft.skills.server.FallenStars;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(NostalgiaSkills.MODID)
@PrefixGameTestTemplate(false)
public final class ApiEventGameTests {
    private static final String GUARDIAN_HUB = "guardian/hub";
    private static final String GATEKEEPER = "guardian/gatekeeper";
    private static final int ELITE_NODES = 8;
    private static final long ELITE_SEED = 42;
    private static final int ADMIN = 4;

    private ApiEventGameTests() {
    }

    private static <E extends Event> void whileListening(Class<E> type, Consumer<E> listener, Runnable body) {
        NeoForge.EVENT_BUS.addListener(type, listener);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    private static <E extends Event & ICancellableEvent> void whileCancelling(Class<E> type, Runnable body) {
        whileListening(type, event -> event.setCanceled(true), body);
    }

    @GameTest(template = Scenario.EMPTY)
    public static void cancellingEliteSpawningKeepsTheMonsterOrdinary(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            Zombie zombie = scenario.mob(EntityType.ZOMBIE);
            whileCancelling(SkillEvents.EliteSpawning.class, () -> scenario.check(
                    !EliteMobs.empower(zombie, RandomSource.create(ELITE_SEED), ELITE_NODES), "the empowering is refused"));
            scenario.check(!EliteMobs.isElite(zombie), "the zombie stays ordinary");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void cancellingStarFallingSpawnsNoStar(GameTestHelper helper) {
        Scenario.run(helper, scenario -> whileCancelling(SkillEvents.StarFalling.class,
                () -> scenario.check(FallingStar.spawnNear(scenario.player()).isEmpty(), "no star falls")));
    }

    @GameTest(template = Scenario.EMPTY)
    public static void aFallenStarCanBeKeptAndOtherwiseReportsTheLostSkill(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer owner = scenario.blankPlayer();
            PlayerSkillState state = scenario.learn(owner, GUARDIAN_HUB, GATEKEEPER);
            int learned = state.allocations().size();
            whileCancelling(SkillEvents.FallenStarDropping.class,
                    () -> scenario.check(FallenStars.drop(owner).isEmpty(), "no star drops while a listener cancels it"));
            scenario.same(learned, state.allocations().size(), "skills kept");
            List<String> lost = new ArrayList<>();
            whileListening(SkillEvents.NodesLost.class, event -> lost.addAll(event.getNodes()),
                    () -> scenario.track(FallenStars.drop(owner).orElseThrow()));
            scenario.same(1, lost.size(), "skills reported as lost");
            scenario.check(!state.isAllocated(lost.get(0)), "the reported skill is gone");
        });
    }

    @GameTest(template = Scenario.EMPTY)
    public static void redeemingAFragmentReportsTheGrantedPoint(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.player();
            List<SkillEvents.PointsGranted> grants = new ArrayList<>();
            whileListening(SkillEvents.PointsGranted.class, grants::add,
                    () -> StarFragmentItem.redeem(player, new ItemStack(ModItems.STAR_FRAGMENT.get())));
            scenario.same(1, grants.size(), "grants reported");
            scenario.same(1, grants.get(0).getPoints(), "points in the report");
            scenario.check(grants.get(0).getSource() == SkillEvents.GrantSource.ITEM, "the source is an item");
        });
    }

    private static void command(ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(ADMIN).withSuppressedOutput();
        player.server.getCommands().performPrefixedCommand(source, command);
    }

    @GameTest(template = Scenario.EMPTY)
    public static void adminCommandsSetSignsForgetDiscoveriesAndGrantPoints(GameTestHelper helper) {
        Scenario.run(helper, scenario -> {
            ServerPlayer player = scenario.blankPlayer();
            PlayerSkillState state = ModAttachments.state(player);
            String target = "@s";
            List<String> signs = SkillTreeManager.sortedSignIds();
            command(player, "nostalgiaskills sign set " + target + " " + signs.get(0));
            scenario.same(signs.get(0), state.sign().orElse(""), "sign after sign set");
            command(player, "nostalgiaskills sign reroll " + target);
            scenario.check(state.sign().filter(signs.get(0)::equals).isEmpty(), "sign reroll picks another sign");
            List<SkillEvents.PointsGranted> grants = new ArrayList<>();
            whileListening(SkillEvents.PointsGranted.class, grants::add,
                    () -> command(player, "nostalgiaskills points add " + target + " 3"));
            scenario.same(3, state.availablePoints(), "points after points add");
            scenario.check(grants.size() == 1 && grants.get(0).getSource() == SkillEvents.GrantSource.COMMAND, "the grant is reported");
            command(player, "nostalgiaskills discoveries clear " + target);
            scenario.check(state.discoveries().isEmpty(), "discoveries after discoveries clear");
        });
    }
}
