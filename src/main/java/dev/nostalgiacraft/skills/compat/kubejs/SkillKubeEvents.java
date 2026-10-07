package dev.nostalgiacraft.skills.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.nostalgiacraft.skills.api.SkillEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.function.Function;

public final class SkillKubeEvents {
    static final EventGroup GROUP = EventGroup.of("NostalgiaSkillsEvents");
    private static final EventHandler POINTS_BOUGHT = GROUP.server("pointsBought", () -> PointsBoughtKubeEvent.class);
    private static final EventHandler NODES_LEARNED = GROUP.server("nodesLearned", () -> NodesLearnedKubeEvent.class);
    private static final EventHandler NODES_REFUNDED = GROUP.server("nodesRefunded", () -> NodesRemovedKubeEvent.class);
    private static final EventHandler BUILD_RESET = GROUP.server("buildReset", () -> NodesRemovedKubeEvent.class);
    private static final EventHandler SKY_EVENT_CHANGED = GROUP.server("skyEventChanged", () -> SkyEventChangedKubeEvent.class);
    private static final EventHandler STAR_LANDED = GROUP.server("starLanded", () -> StarLandedKubeEvent.class);
    private static final EventHandler ELITE_SPAWNED = GROUP.server("eliteSpawned", () -> EliteSpawnedKubeEvent.class);

    private SkillKubeEvents() {
    }

    static void forwardAll() {
        forward(SkillEvents.PointsBought.class, POINTS_BOUGHT, PointsBoughtKubeEvent::new);
        forward(SkillEvents.NodesLearned.class, NODES_LEARNED, NodesLearnedKubeEvent::new);
        forward(SkillEvents.NodesRefunded.class, NODES_REFUNDED, NodesRemovedKubeEvent::new);
        forward(SkillEvents.BuildReset.class, BUILD_RESET, NodesRemovedKubeEvent::new);
        forward(SkillEvents.SkyEventChanged.class, SKY_EVENT_CHANGED, SkyEventChangedKubeEvent::new);
        forward(SkillEvents.StarLanded.class, STAR_LANDED, StarLandedKubeEvent::new);
        forward(SkillEvents.EliteSpawned.class, ELITE_SPAWNED, EliteSpawnedKubeEvent::new);
    }

    private static <E extends Event> void forward(Class<E> type, EventHandler handler, Function<E, KubeEvent> wrap) {
        NeoForge.EVENT_BUS.addListener(type, event -> KubeJSGuard.run("posting " + type.getSimpleName(), () -> {
            if (handler.hasListeners()) {
                handler.post(ScriptType.SERVER, wrap.apply(event));
            }
        }));
    }

    public record PointsBoughtKubeEvent(SkillEvents.PointsBought event) implements KubeEvent {
        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public int getPoints() {
            return event.getPoints();
        }

        public long getXpSpent() {
            return event.getXpSpent();
        }
    }

    public record NodesLearnedKubeEvent(SkillEvents.NodesLearned event) implements KubeEvent {
        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public List<String> getNodes() {
            return event.getNodes();
        }
    }

    public record NodesRemovedKubeEvent(SkillEvents.NodesRemoved event) implements KubeEvent {
        public ServerPlayer getPlayer() {
            return event.getPlayer();
        }

        public List<String> getNodes() {
            return event.getNodes();
        }

        public int getPointsReturned() {
            return event.getPointsReturned();
        }
    }

    public record SkyEventChangedKubeEvent(SkillEvents.SkyEventChanged event) implements KubeEvent {
        public MinecraftServer getServer() {
            return event.getServer();
        }

        public String getBefore() {
            return event.getBefore().id();
        }

        public String getNow() {
            return event.getNow().id();
        }
    }

    public record StarLandedKubeEvent(SkillEvents.StarLanded event) implements KubeEvent {
        public ServerLevel getLevel() {
            return event.getLevel();
        }

        public ItemEntity getFragment() {
            return event.getFragment();
        }
    }

    public record EliteSpawnedKubeEvent(SkillEvents.EliteSpawned event) implements KubeEvent {
        public Monster getMonster() {
            return event.getMonster();
        }
    }
}
