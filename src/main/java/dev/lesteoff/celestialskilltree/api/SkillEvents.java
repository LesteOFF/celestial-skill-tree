package dev.lesteoff.celestialskilltree.api;

import dev.lesteoff.celestialskilltree.generation.CatalogEditor;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Set;

public final class SkillEvents {
    private SkillEvents() {
    }

    public static <E extends Event> E post(E event) {
        return NeoForge.EVENT_BUS.post(event);
    }

    public static <E extends Event & ICancellableEvent> boolean allows(E event) {
        return !post(event).isCanceled();
    }

    public abstract static class OfPlayer extends Event {
        private final ServerPlayer player;

        protected OfPlayer(ServerPlayer player) {
            this.player = player;
        }

        public ServerPlayer getPlayer() {
            return player;
        }
    }

    public static final class PointsBought extends OfPlayer {
        private final int points;
        private final long xpSpent;

        public PointsBought(ServerPlayer player, int points, long xpSpent) {
            super(player);
            this.points = points;
            this.xpSpent = xpSpent;
        }

        public int getPoints() {
            return points;
        }

        public long getXpSpent() {
            return xpSpent;
        }
    }

    public static final class NodesLearned extends OfPlayer {
        private final List<String> nodes;

        public NodesLearned(ServerPlayer player, List<String> nodes) {
            super(player);
            this.nodes = List.copyOf(nodes);
        }

        public List<String> getNodes() {
            return nodes;
        }
    }

    public abstract static class NodesRemoved extends OfPlayer {
        private final List<String> nodes;
        private final int pointsReturned;

        protected NodesRemoved(ServerPlayer player, List<String> nodes, int pointsReturned) {
            super(player);
            this.nodes = List.copyOf(nodes);
            this.pointsReturned = pointsReturned;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public int getPointsReturned() {
            return pointsReturned;
        }
    }

    public static final class NodesLost extends NodesRemoved {
        public NodesLost(ServerPlayer player, List<String> nodes) {
            super(player, nodes, 0);
        }
    }

    public static final class NodesRefunded extends NodesRemoved {
        public NodesRefunded(ServerPlayer player, List<String> nodes, int pointsReturned) {
            super(player, nodes, pointsReturned);
        }
    }

    public static final class BuildReset extends NodesRemoved {
        public BuildReset(ServerPlayer player, List<String> nodes, int pointsReturned) {
            super(player, nodes, pointsReturned);
        }
    }

    public static final class SkyEventChanged extends Event {
        private final MinecraftServer server;
        private final SkyEvent before;
        private final SkyEvent now;

        public SkyEventChanged(MinecraftServer server, SkyEvent before, SkyEvent now) {
            this.server = server;
            this.before = before;
            this.now = now;
        }

        public MinecraftServer getServer() {
            return server;
        }

        public SkyEvent getBefore() {
            return before;
        }

        public SkyEvent getNow() {
            return now;
        }
    }

    public static final class StarLanded extends Event {
        private final ServerLevel level;
        private final ItemEntity fragment;

        public StarLanded(ServerLevel level, ItemEntity fragment) {
            this.level = level;
            this.fragment = fragment;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public ItemEntity getFragment() {
            return fragment;
        }
    }

    public static final class TreeCriticalHit extends OfPlayer {
        private final LivingEntity victim;
        private final float multiplier;

        public TreeCriticalHit(ServerPlayer player, LivingEntity victim, float multiplier) {
            super(player);
            this.victim = victim;
            this.multiplier = multiplier;
        }

        public LivingEntity getVictim() {
            return victim;
        }

        public float getMultiplier() {
            return multiplier;
        }
    }

    public static final class CatalogLoading extends Event {
        private final CatalogEditor catalog;

        public CatalogLoading(CatalogEditor catalog) {
            this.catalog = catalog;
        }

        public CatalogEditor getCatalog() {
            return catalog;
        }
    }

    public static final class EliteSpawned extends Event {
        private final Monster monster;

        public EliteSpawned(Monster monster) {
            this.monster = monster;
        }

        public Monster getMonster() {
            return monster;
        }
    }

    public enum GrantSource { COMMAND, ITEM, INTEGRATION }

    public static final class PointsGranted extends OfPlayer {
        private final int points;
        private final GrantSource source;

        public PointsGranted(ServerPlayer player, int points, GrantSource source) {
            super(player);
            this.points = points;
            this.source = source;
        }

        public int getPoints() {
            return points;
        }

        public GrantSource getSource() {
            return source;
        }
    }

    public static final class DiscoveriesRevealed extends OfPlayer {
        private final Set<String> keys;

        public DiscoveriesRevealed(ServerPlayer player, Set<String> keys) {
            super(player);
            this.keys = Set.copyOf(keys);
        }

        public Set<String> getKeys() {
            return keys;
        }
    }

    public static final class CommunityTierUnlocked extends Event {
        private final MinecraftServer server;
        private final String tierId;

        public CommunityTierUnlocked(MinecraftServer server, String tierId) {
            this.server = server;
            this.tierId = tierId;
        }

        public MinecraftServer getServer() {
            return server;
        }

        public String getTierId() {
            return tierId;
        }
    }

    public static final class PointsBuying extends OfPlayer implements ICancellableEvent {
        private final int count;

        public PointsBuying(ServerPlayer player, int count) {
            super(player);
            this.count = count;
        }

        public int getCount() {
            return count;
        }
    }

    public static final class NodesLearning extends OfPlayer implements ICancellableEvent {
        private final List<String> nodes;

        public NodesLearning(ServerPlayer player, List<String> nodes) {
            super(player);
            this.nodes = List.copyOf(nodes);
        }

        public List<String> getNodes() {
            return nodes;
        }
    }

    public static final class RefundRequested extends OfPlayer implements ICancellableEvent {
        private final String node;
        private final List<String> nodesRemoved;
        private final int pointsReturned;

        public RefundRequested(ServerPlayer player, String node, List<String> nodesRemoved, int pointsReturned) {
            super(player);
            this.node = node;
            this.nodesRemoved = List.copyOf(nodesRemoved);
            this.pointsReturned = pointsReturned;
        }

        public String getNode() {
            return node;
        }

        public List<String> getNodesRemoved() {
            return nodesRemoved;
        }

        public int getPointsReturned() {
            return pointsReturned;
        }
    }

    public static final class EliteSpawning extends Event implements ICancellableEvent {
        private final Monster monster;
        private int nodeCount;

        public EliteSpawning(Monster monster, int nodeCount) {
            this.monster = monster;
            this.nodeCount = nodeCount;
        }

        public Monster getMonster() {
            return monster;
        }

        public int getNodeCount() {
            return nodeCount;
        }

        public void setNodeCount(int nodeCount) {
            this.nodeCount = Math.max(0, nodeCount);
        }
    }

    public static final class StarFalling extends OfPlayer implements ICancellableEvent {
        public StarFalling(ServerPlayer player) {
            super(player);
        }
    }

    public static final class FallenStarDropping extends OfPlayer implements ICancellableEvent {
        private final String node;

        public FallenStarDropping(ServerPlayer player, String node) {
            super(player);
            this.node = node;
        }

        public String getNode() {
            return node;
        }
    }

    public static final class TriggerFiring extends OfPlayer implements ICancellableEvent {
        private final String trigger;
        private final String action;
        private final LivingEntity target;

        public TriggerFiring(ServerPlayer player, String trigger, String action, LivingEntity target) {
            super(player);
            this.trigger = trigger;
            this.action = action;
            this.target = target;
        }

        public String getTrigger() {
            return trigger;
        }

        public String getAction() {
            return action;
        }

        public LivingEntity getTarget() {
            return target;
        }
    }
}
