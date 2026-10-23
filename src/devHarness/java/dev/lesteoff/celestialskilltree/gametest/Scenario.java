package dev.lesteoff.celestialskilltree.gametest;

import com.mojang.authlib.GameProfile;
import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.data.WorldSkillData;
import dev.lesteoff.celestialskilltree.effects.AttributeAliases;
import dev.lesteoff.celestialskilltree.effects.EffectEngine;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.LedgerService;
import dev.lesteoff.celestialskilltree.progression.PathFinder;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.progression.XpMath;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import dev.lesteoff.celestialskilltree.server.CommunityTree;
import dev.lesteoff.celestialskilltree.server.LifecycleEvents;
import dev.lesteoff.celestialskilltree.server.SkyEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;

final class Scenario {
    static final String EMPTY = "empty";
    static final BlockPos CENTER = new BlockPos(1, 2, 1);
    private static final String NS = CelestialSkillTree.MODID + ":";
    private static final String MOCK_NAME = "test-mock-player";
    private static final double TOLERANCE = 1e-4;
    private static final double DROP_RADIUS = 3.0;
    private static final int STARTING_POINTS = 60;
    private static final int POINT_CAP = 120;
    private static final int SPAWN_PROTECTION_TICKS = 61;
    private static final List<String> OTHER_MOD_CRIT_CHANCES = List.of("apothic_attributes:crit_chance", "l2damagetracker:crit_rate",
            "puffish_attributes:crit_chance");
    private static final List<String> DAMAGE_CHANGING_MODS = List.of("l2hostility", "apotheosis", "champions");

    private final GameTestHelper helper;
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Entity> tracked = new ArrayList<>();
    private OptionalLong communityBefore = OptionalLong.empty();
    private boolean skyForced;
    private int pendingSteps;

    private Scenario(GameTestHelper helper) {
        this.helper = helper;
    }

    static void run(GameTestHelper helper, Consumer<Scenario> body) {
        Scenario scenario = new Scenario(helper);
        scenario.step(() -> body.accept(scenario));
    }

    void afterSpawnProtection(Runnable next) {
        pendingSteps++;
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> {
            pendingSteps--;
            step(next);
        });
    }

    private void step(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            close();
            throw asAssertion(failure);
        }
        if (pendingSteps == 0) {
            close();
            helper.succeed();
        }
    }

    private static GameTestAssertException asAssertion(RuntimeException failure) {
        if (failure instanceof GameTestAssertException assertion) {
            return assertion;
        }
        CelestialSkillTree.LOGGER.error("GameTest crashed", failure);
        return new GameTestAssertException("crashed: " + failure);
    }

    private void close() {
        if (skyForced) {
            SkyEvents.force(server(), SkyEvent.NONE);
        }
        communityBefore.ifPresent(xp -> CommunityTree.setProgress(server(), xp));
        tracked.forEach(Entity::discard);
        level().getEntitiesOfClass(ItemEntity.class, dropArea()).forEach(Entity::discard);
        players.forEach(server().getPlayerList()::remove);    }

    MinecraftServer server() {
        return helper.getLevel().getServer();
    }

    ServerLevel level() {
        return helper.getLevel();
    }

    Vec3 center() {
        return helper.absoluteVec(Vec3.atCenterOf(CENTER));
    }

    SkillGraphSnapshot graph() {
        return SkillTreeManager.graph().orElseThrow(() -> new GameTestAssertException("no skill tree loaded"));
    }

    ServerPlayer player() {
        MinecraftServer server = server();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), MOCK_NAME), false);
        ServerPlayer player = new ServerPlayer(server, level(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
        } catch (RuntimeException otherModLoginFailure) {
            CelestialSkillTree.LOGGER.warn("another mod failed while the mock player logged in; running this mod's login only", otherModLoginFailure);
            LifecycleEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        }
        player.setGameMode(GameType.SURVIVAL);
        players.add(player);
        return player;
    }

    ServerPlayer blankPlayer() {
        ServerPlayer player = player();
        withoutRandomCriticals(player);
        ModAttachments.setState(player, new PlayerSkillState());
        EffectEngine.refresh(player);
        return player;
    }

    PlayerSkillState learn(ServerPlayer player, String hub, String... targets) {
        PlayerSkillState state = ModAttachments.state(player);
        LedgerService.grant(state, STARTING_POINTS, POINT_CAP, false);
        confirm(state, List.of(id(hub)), hub);
        for (String target : targets) {
            PathFinder.Route route = PathFinder.route(graph(), state, List.of(), id(target))
                    .orElseThrow(() -> new GameTestAssertException("no route to " + target));
            check(route.valid(), "route to " + target + " is valid");
            confirm(state, route.newNodes(), target);
        }
        EffectEngine.refresh(player);
        return state;
    }

    private static void withoutRandomCriticals(ServerPlayer player) {
        OTHER_MOD_CRIT_CHANCES.stream()
                .flatMap(id -> BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).stream())
                .map(player::getAttribute)
                .filter(Objects::nonNull)
                .forEach(otherModCritChance -> otherModCritChance.setBaseValue(0));
    }

    static boolean otherModsChangeDamage() {
        return DAMAGE_CHANGING_MODS.stream().anyMatch(ModList.get()::isLoaded);
    }

    static double treeValue(ServerPlayer player, Holder<Attribute> attribute) {
        String id = attribute.unwrapKey().orElseThrow().location().toString();
        return AttributeAliases.active(id)
                .flatMap(alias -> BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(alias.target()))
                        .map(target -> player.getAttributeValue(target)))
                .orElseGet(() -> player.getAttributeValue(attribute));
    }

    void confirm(PlayerSkillState state, List<String> nodes, String what) {
        AllocationService.BatchResult result = AllocationService.confirm(graph(), state, nodes);
        check(result.ok(), "learning " + what + " rejected: " + result.rejection());
    }

    <E extends Mob> E mob(EntityType<E> type) {
        return track(helper.spawnWithNoFreeWill(type, CENTER));
    }

    <E extends Entity> E track(E entity) {
        tracked.add(entity);
        return entity;
    }

    int collect(Item item) {
        List<ItemEntity> drops = level().getEntitiesOfClass(ItemEntity.class, dropArea(), drop -> drop.getItem().is(item));
        int count = drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum();
        drops.forEach(Entity::discard);
        return count;
    }

    private AABB dropArea() {
        return new AABB(helper.absolutePos(CENTER)).inflate(DROP_RADIUS);
    }

    void forceSky(SkyEvent event) {
        skyForced = true;
        SkyEvents.force(server(), event);
        level().updateSkyBrightness();
    }

    void communityProgress(long xp) {
        if (communityBefore.isEmpty()) {
            communityBefore = OptionalLong.of(WorldSkillData.of(server()).communityXp());
        }
        CommunityTree.setProgress(server(), xp);
    }

    void check(boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    void same(long expected, long actual, String what) {
        check(expected == actual, what + ": expected " + expected + " but was " + actual);
    }

    void same(Object expected, Object actual, String what) {
        check(Objects.equals(expected, actual), what + ": expected " + expected + " but was " + actual);
    }

    void near(double expected, double actual, String what) {
        check(Math.abs(expected - actual) < TOLERANCE, what + ": expected " + expected + " but was " + actual);
    }

    void atLeast(double minimum, double actual, String what) {
        check(actual >= minimum - TOLERANCE, what + ": expected at least " + minimum + " but was " + actual);
    }

    static String id(String path) {
        return NS + path;
    }

    static void set(ServerPlayer player, Holder<Attribute> attribute, double value) {
        player.getAttribute(attribute).setBaseValue(value);
    }

    static float strike(LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        float before = victim.getHealth();
        victim.hurt(source, amount);
        return before - victim.getHealth();
    }

    static long experience(ServerPlayer player) {
        return XpMath.balance(player.experienceLevel, player.experienceProgress);
    }

    static int treeModifiers(ServerPlayer player) {
        return (int) BuiltInRegistries.ATTRIBUTE.holders().map(player::getAttribute).filter(Objects::nonNull)
                .map(AttributeInstance::getModifiers).flatMap(Collection::stream)
                .filter(modifier -> modifier.id().getNamespace().equals(CelestialSkillTree.MODID)).count();
    }
}
