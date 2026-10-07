package dev.nostalgiacraft.skills.api;

import dev.nostalgiacraft.skills.progression.SkyEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public final class SkillEvents {
    private SkillEvents() {
    }

    public static void post(Event event) {
        NeoForge.EVENT_BUS.post(event);
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

    public static final class EliteSpawned extends Event {
        private final Monster monster;

        public EliteSpawned(Monster monster) {
            this.monster = monster;
        }

        public Monster getMonster() {
            return monster;
        }
    }
}
