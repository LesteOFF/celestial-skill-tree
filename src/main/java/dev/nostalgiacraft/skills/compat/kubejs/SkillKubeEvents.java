package dev.nostalgiacraft.skills.compat.kubejs;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.nostalgiacraft.skills.api.SkillEvents;
import dev.nostalgiacraft.skills.compat.OptionalEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.function.Consumer;
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
    private static final EventHandler CATALOG = GROUP.server("catalog", () -> CatalogKubeEvent.class);
    private static final EventHandler POINTS_GRANTED = GROUP.server("pointsGranted", () -> ApiKubeEvent.class);
    private static final EventHandler NODES_LOST = GROUP.server("nodesLost", () -> NodesRemovedKubeEvent.class);
    private static final EventHandler DISCOVERIES_REVEALED = GROUP.server("discoveriesRevealed", () -> ApiKubeEvent.class);
    private static final EventHandler COMMUNITY_TIER_UNLOCKED = GROUP.server("communityTierUnlocked", () -> ApiKubeEvent.class);
    private static final EventHandler POINTS_BUYING = GROUP.server("pointsBuying", () -> ApiKubeEvent.class).hasResult();
    private static final EventHandler NODES_LEARNING = GROUP.server("nodesLearning", () -> ApiKubeEvent.class).hasResult();
    private static final EventHandler REFUND_REQUESTED = GROUP.server("refundRequested", () -> ApiKubeEvent.class).hasResult();
    private static final EventHandler ELITE_SPAWNING = GROUP.server("eliteSpawning", () -> ApiKubeEvent.class).hasResult();
    private static final EventHandler STAR_FALLING = GROUP.server("starFalling", () -> ApiKubeEvent.class).hasResult();
    private static final EventHandler FALLEN_STAR_DROPPING = GROUP.server("fallenStarDropping", () -> ApiKubeEvent.class).hasResult();
    private static final EventHandler TRIGGER_FIRING = GROUP.server("triggerFiring", () -> ApiKubeEvent.class).hasResult();

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
        forward(SkillEvents.CatalogLoading.class, CATALOG, CatalogKubeEvent::new);
        forward(SkillEvents.PointsGranted.class, POINTS_GRANTED, ApiKubeEvent::new);
        forward(SkillEvents.NodesLost.class, NODES_LOST, NodesRemovedKubeEvent::new);
        forward(SkillEvents.DiscoveriesRevealed.class, DISCOVERIES_REVEALED, ApiKubeEvent::new);
        forward(SkillEvents.CommunityTierUnlocked.class, COMMUNITY_TIER_UNLOCKED, ApiKubeEvent::new);
        forwardCancellable(SkillEvents.PointsBuying.class, POINTS_BUYING);
        forwardCancellable(SkillEvents.NodesLearning.class, NODES_LEARNING);
        forwardCancellable(SkillEvents.RefundRequested.class, REFUND_REQUESTED);
        forwardCancellable(SkillEvents.EliteSpawning.class, ELITE_SPAWNING);
        forwardCancellable(SkillEvents.StarFalling.class, STAR_FALLING);
        forwardCancellable(SkillEvents.FallenStarDropping.class, FALLEN_STAR_DROPPING);
        forwardCancellable(SkillEvents.TriggerFiring.class, TRIGGER_FIRING);
    }

    private static <E extends Event> void forward(Class<E> type, EventHandler handler, Function<E, KubeEvent> wrap) {
        listen(type, handler, event -> handler.post(ScriptType.SERVER, wrap.apply(event)));
    }

    private static <E extends Event & ICancellableEvent> void forwardCancellable(Class<E> type, EventHandler handler) {
        listen(type, handler, event -> handler.post(ScriptType.SERVER, new ApiKubeEvent(event)).applyCancel(event));
    }

    private static <E extends Event> void listen(Class<E> type, EventHandler handler, Consumer<E> post) {
        NeoForge.EVENT_BUS.addListener(type, event -> OptionalEvents.guard(NostalgiaSkillsPlugin.MOD_ID, "posting " + type.getSimpleName(), () -> {
            if (handler.hasListeners()) {
                post.accept(event);
            }
        }));
    }

    public record ApiKubeEvent(Event event) implements KubeEvent {
        public Event getEvent() {
            return event;
        }
    }

    public record CatalogKubeEvent(SkillEvents.CatalogLoading event) implements KubeEvent {
        public void addClass(String id, JsonObject definition) {
            event.getCatalog().addClass(id, definition);
        }

        public void removeClass(String id) {
            event.getCatalog().removeClass(id);
        }

        public void addTheme(String id, JsonObject definition) {
            event.getCatalog().addTheme(id, definition);
        }

        public void removeTheme(String id) {
            event.getCatalog().removeTheme(id);
        }

        public void addKeystone(String id, JsonObject definition) {
            event.getCatalog().addKeystone(id, definition);
        }

        public void removeKeystone(String id) {
            event.getCatalog().removeKeystone(id);
        }

        public void rules(JsonObject definition) {
            event.getCatalog().rules(definition);
        }
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
