package dev.lesteoff.celestialskilltree.network;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.graph.CommunityParser;
import dev.lesteoff.celestialskilltree.graph.GraphCodec;
import dev.lesteoff.celestialskilltree.graph.SkillSign;
import dev.lesteoff.celestialskilltree.progression.AllocationService;
import dev.lesteoff.celestialskilltree.progression.CommunityProgress;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.progression.SkyEvent;
import dev.lesteoff.celestialskilltree.progression.XpCurve;
import dev.lesteoff.celestialskilltree.registry.StateCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Payloads {
    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, name));
    }

    static final StreamCodec<ByteBuf, String> NODE_ID = ByteBufCodecs.stringUtf8(64);

    public record RequestSync(String knownFingerprint) implements CustomPacketPayload {
        public static final Type<RequestSync> TYPE = typeOf("request_sync");
        public static final StreamCodec<ByteBuf, RequestSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), RequestSync::knownFingerprint, RequestSync::new);

        public Type<RequestSync> type() {
            return TYPE;
        }
    }

    public record BuyPoint(UUID requestId, long expectedStateRevision, int count) implements CustomPacketPayload {
        public static final Type<BuyPoint> TYPE = typeOf("buy_point");
        public static final StreamCodec<ByteBuf, BuyPoint> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BuyPoint::requestId, ByteBufCodecs.VAR_LONG, BuyPoint::expectedStateRevision,
                ByteBufCodecs.VAR_INT, BuyPoint::count, BuyPoint::new);

        public Type<BuyPoint> type() {
            return TYPE;
        }
    }

    public record DonateXp(UUID requestId, long expectedStateRevision, String starId, int amount) implements CustomPacketPayload {
        public static final Type<DonateXp> TYPE = typeOf("donate_xp");
        public static final StreamCodec<ByteBuf, DonateXp> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, DonateXp::requestId, ByteBufCodecs.VAR_LONG, DonateXp::expectedStateRevision,
                NODE_ID, DonateXp::starId, ByteBufCodecs.VAR_INT, DonateXp::amount, DonateXp::new);

        public Type<DonateXp> type() {
            return TYPE;
        }
    }

    public record ConfirmAllocation(UUID requestId, long expectedStateRevision, int expectedGraphRevision,
                                    List<String> nodes) implements CustomPacketPayload {
        public static final Type<ConfirmAllocation> TYPE = typeOf("confirm_allocation");
        public static final StreamCodec<ByteBuf, ConfirmAllocation> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ConfirmAllocation::requestId,
                ByteBufCodecs.VAR_LONG, ConfirmAllocation::expectedStateRevision,
                ByteBufCodecs.VAR_INT, ConfirmAllocation::expectedGraphRevision,
                NODE_ID.apply(ByteBufCodecs.list(AllocationService.MAX_BATCH)), ConfirmAllocation::nodes,
                ConfirmAllocation::new);

        public Type<ConfirmAllocation> type() {
            return TYPE;
        }
    }

    public record RequestReset(UUID requestId, long expectedStateRevision, int expectedGraphRevision)
            implements CustomPacketPayload {
        public static final Type<RequestReset> TYPE = typeOf("request_reset");
        public static final StreamCodec<ByteBuf, RequestReset> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, RequestReset::requestId, ByteBufCodecs.VAR_LONG, RequestReset::expectedStateRevision,
                ByteBufCodecs.VAR_INT, RequestReset::expectedGraphRevision, RequestReset::new);

        public Type<RequestReset> type() {
            return TYPE;
        }
    }

    public record RequestRefund(UUID requestId, long expectedStateRevision, int expectedGraphRevision, String nodeId)
            implements CustomPacketPayload {
        public static final Type<RequestRefund> TYPE = typeOf("request_refund");
        public static final StreamCodec<ByteBuf, RequestRefund> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, RequestRefund::requestId,
                ByteBufCodecs.VAR_LONG, RequestRefund::expectedStateRevision,
                ByteBufCodecs.VAR_INT, RequestRefund::expectedGraphRevision,
                NODE_ID, RequestRefund::nodeId, RequestRefund::new);

        public Type<RequestRefund> type() {
            return TYPE;
        }
    }

    public record ToggleNode(UUID requestId, long expectedStateRevision, int expectedGraphRevision, String nodeId)
            implements CustomPacketPayload {
        public static final Type<ToggleNode> TYPE = typeOf("toggle_node");
        public static final StreamCodec<ByteBuf, ToggleNode> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ToggleNode::requestId,
                ByteBufCodecs.VAR_LONG, ToggleNode::expectedStateRevision,
                ByteBufCodecs.VAR_INT, ToggleNode::expectedGraphRevision,
                NODE_ID, ToggleNode::nodeId, ToggleNode::new);

        public Type<ToggleNode> type() {
            return TYPE;
        }
    }

    public record GraphSync(int revision, byte[] data) implements CustomPacketPayload {
        public static final Type<GraphSync> TYPE = typeOf("graph_sync");
        public static final StreamCodec<ByteBuf, GraphSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GraphSync::revision, ByteBufCodecs.byteArray(GraphCodec.MAX_BYTES), GraphSync::data,
                GraphSync::new);

        public Type<GraphSync> type() {
            return TYPE;
        }
    }

    public record Economy(int nextPointCost, int pointLimit, boolean xpPurchaseEnabled, boolean orbRequired,
                          boolean nodeRefundsEnabled, boolean orbRequiredForNodeRefund, long walletXp) {
        private static final StreamCodec<ByteBuf, Economy> RULES = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Economy::nextPointCost, ByteBufCodecs.VAR_INT, Economy::pointLimit,
                ByteBufCodecs.BOOL, Economy::xpPurchaseEnabled, ByteBufCodecs.BOOL, Economy::orbRequired,
                ByteBufCodecs.BOOL, Economy::nodeRefundsEnabled, ByteBufCodecs.BOOL, Economy::orbRequiredForNodeRefund,
                Economy::new);
        public static final StreamCodec<ByteBuf, Economy> CODEC = StreamCodec.composite(
                RULES, economy -> economy, ByteBufCodecs.VAR_LONG, Economy::walletXp, Economy::withWalletXp);

        public Economy(int nextPointCost, int pointLimit, boolean xpPurchaseEnabled, boolean orbRequired,
                       boolean nodeRefundsEnabled, boolean orbRequiredForNodeRefund) {
            this(nextPointCost, pointLimit, xpPurchaseEnabled, orbRequired, nodeRefundsEnabled, orbRequiredForNodeRefund, 0);
        }

        public Economy withWalletXp(long stored) {
            return new Economy(nextPointCost, pointLimit, xpPurchaseEnabled, orbRequired, nodeRefundsEnabled,
                    orbRequiredForNodeRefund, stored);
        }
    }

    static final StreamCodec<ByteBuf, XpCurve> CURVE = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, XpCurve::base, ByteBufCodecs.VAR_INT, XpCurve::linear,
            ByteBufCodecs.VAR_INT, XpCurve::quadratic, ByteBufCodecs.DOUBLE, XpCurve::discount, XpCurve::new);

    public record Position(float x, float y) {
        public static final StreamCodec<ByteBuf, Position> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Position::x, ByteBufCodecs.FLOAT, Position::y, Position::new);
    }

    public record TierInfo(String id, long xpRequired, String parent, String exclusive, String icon, Position position) {
        public static final StreamCodec<ByteBuf, TierInfo> CODEC = StreamCodec.composite(
                NODE_ID, TierInfo::id, ByteBufCodecs.VAR_LONG, TierInfo::xpRequired, NODE_ID, TierInfo::parent,
                NODE_ID, TierInfo::exclusive, ByteBufCodecs.STRING_UTF8, TierInfo::icon, Position.CODEC, TierInfo::position,
                TierInfo::new);

        public float x() {
            return position.x();
        }

        public float y() {
            return position.y();
        }

        public boolean rivals(TierInfo other) {
            return !id.equals(other.id) && !exclusive.isEmpty() && exclusive.equals(other.exclusive);
        }
    }

    public record Patron(String name, long xp) {
        public static final StreamCodec<ByteBuf, Patron> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(16), Patron::name, ByteBufCodecs.VAR_LONG, Patron::xp, Patron::new);

        public boolean anonymous() {
            return name.isEmpty();
        }
    }

    public record StarProgress(String id, long donated, CommunityProgress.StarStatus status, List<Patron> patrons) {
        public static final int MAX_PATRONS = 3;
        public static final StreamCodec<ByteBuf, StarProgress> CODEC = StreamCodec.composite(
                NODE_ID, StarProgress::id, ByteBufCodecs.VAR_LONG, StarProgress::donated,
                ByteBufCodecs.idMapper(CommunityProgress.StarStatus::byId, CommunityProgress.StarStatus::ordinal), StarProgress::status,
                Patron.CODEC.apply(ByteBufCodecs.list(MAX_PATRONS)), StarProgress::patrons, StarProgress::new);
    }

    public record Community(List<StarProgress> stars, List<Patron> ranking, int ownRank, long ownTotal, long anonymousXp,
                            int patronCount) {
        public static final int MAX_RANKING = 256;
        public static final Community NONE = new Community(List.of(), List.of(), 0, 0, 0, 0);
        public static final StreamCodec<ByteBuf, Community> CODEC = StreamCodec.composite(
                StarProgress.CODEC.apply(ByteBufCodecs.list(CommunityParser.MAX_STARS)), Community::stars,
                Patron.CODEC.apply(ByteBufCodecs.list(MAX_RANKING)), Community::ranking,
                ByteBufCodecs.VAR_INT, Community::ownRank, ByteBufCodecs.VAR_LONG, Community::ownTotal,
                ByteBufCodecs.VAR_LONG, Community::anonymousXp, ByteBufCodecs.VAR_INT, Community::patronCount, Community::new);

        public Optional<StarProgress> progress(String starId) {
            return stars.stream().filter(star -> star.id().equals(starId)).findFirst();
        }

        public CommunityProgress.StarStatus status(String starId) {
            return progress(starId).map(StarProgress::status).orElse(CommunityProgress.StarStatus.LOCKED);
        }

        public long donated() {
            return stars.stream().mapToLong(StarProgress::donated).sum();
        }

        public long litCount() {
            return stars.stream().filter(star -> star.status() == CommunityProgress.StarStatus.LIT).count();
        }
    }

    public record SignBonus(String descriptionKey, boolean active) {
        public static final StreamCodec<ByteBuf, SignBonus> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SignBonus::descriptionKey, ByteBufCodecs.BOOL, SignBonus::active, SignBonus::new);
    }

    public record SignInfo(String id, String descriptionKey, List<SignBonus> bonuses) {
        public static final int MAX_BONUSES = 8;
        public static final StreamCodec<ByteBuf, SignInfo> CODEC = StreamCodec.composite(
                NODE_ID, SignInfo::id, ByteBufCodecs.STRING_UTF8, SignInfo::descriptionKey,
                SignBonus.CODEC.apply(ByteBufCodecs.list(MAX_BONUSES)), SignInfo::bonuses, SignInfo::new);

        public static MutableComponent describe(List<String> descriptionKeys) {
            MutableComponent description = Component.empty();
            for (int i = 0; i < descriptionKeys.size(); i++) {
                if (i > 0) {
                    description.append(" ");
                }
                description.append(Component.translatable(descriptionKeys.get(i)));
            }
            return description;
        }
    }

    public record WorldRules(SkyEvent skyEvent, List<TierInfo> communityTiers, Community community, List<SignInfo> signs) {
        public static final int MAX_SIGNS = 64;
        public static final WorldRules NONE = new WorldRules(SkyEvent.NONE, List.of(), Community.NONE, List.of());
        public static final StreamCodec<ByteBuf, WorldRules> CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(SkyEvent::byId, SkyEvent::ordinal), WorldRules::skyEvent,
                TierInfo.CODEC.apply(ByteBufCodecs.list(CommunityParser.MAX_STARS)), WorldRules::communityTiers,
                Community.CODEC, WorldRules::community,
                SignInfo.CODEC.apply(ByteBufCodecs.list(MAX_SIGNS)), WorldRules::signs, WorldRules::new);

        public List<String> signIds() {
            return signs.stream().map(SignInfo::id).toList();
        }

        public Optional<SignInfo> sign(String signId) {
            return signs.stream().filter(sign -> sign.id().equals(signId)).findFirst();
        }

        public MutableComponent signBaseDescription(String signId) {
            return Component.translatable(sign(signId).map(SignInfo::descriptionKey).orElse(SkillSign.defaultDescriptionKey(signId)));
        }

        public Optional<TierInfo> communityTier(String starId) {
            return communityTiers.stream().filter(tier -> tier.id().equals(starId)).findFirst();
        }

        public WorldRules withCommunity(Community updated) {
            return new WorldRules(skyEvent, communityTiers, updated, signs);
        }
    }

    public record CommunityProgressSync(Community community) implements CustomPacketPayload {
        public static final Type<CommunityProgressSync> TYPE = typeOf("community_progress");
        public static final StreamCodec<ByteBuf, CommunityProgressSync> CODEC = StreamCodec.composite(
                Community.CODEC, CommunityProgressSync::community, CommunityProgressSync::new);

        public Type<CommunityProgressSync> type() {
            return TYPE;
        }
    }

    public record StateSync(int graphRevision, PlayerSkillState state, Economy economy, XpCurve curve,
                            WorldRules worldRules) implements CustomPacketPayload {
        public static final Type<StateSync> TYPE = typeOf("state_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, StateSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StateSync::graphRevision,
                ByteBufCodecs.fromCodec(StateCodecs.STATE), StateSync::state,
                Economy.CODEC, StateSync::economy, CURVE, StateSync::curve,
                WorldRules.CODEC, StateSync::worldRules, StateSync::new);

        public Type<StateSync> type() {
            return TYPE;
        }
    }

    public record TransactionResult(UUID requestId, boolean ok, String messageKey, List<String> arguments)
            implements CustomPacketPayload {
        public static final String ALLOCATED = "message.celestialskilltree.allocated";
        public static final String POINT_BOUGHT = "message.celestialskilltree.point_bought";
        public static final String POINTS_BOUGHT = "message.celestialskilltree.points_bought";
        public static final int MAX_ARGUMENTS = 8;
        public static final Type<TransactionResult> TYPE = typeOf("transaction_result");
        public static final StreamCodec<ByteBuf, TransactionResult> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, TransactionResult::requestId, ByteBufCodecs.BOOL, TransactionResult::ok,
                ByteBufCodecs.stringUtf8(128), TransactionResult::messageKey,
                ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(MAX_ARGUMENTS)), TransactionResult::arguments,
                TransactionResult::new);

        public Type<TransactionResult> type() {
            return TYPE;
        }
    }

    public record TreeViewing(boolean open, String sectorKey) implements CustomPacketPayload {
        public static final int MAX_SECTOR_KEY_LENGTH = 128;
        public static final Type<TreeViewing> TYPE = typeOf("tree_viewing");
        public static final StreamCodec<ByteBuf, TreeViewing> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, TreeViewing::open, ByteBufCodecs.stringUtf8(MAX_SECTOR_KEY_LENGTH), TreeViewing::sectorKey, TreeViewing::new);

        public Type<TreeViewing> type() {
            return TYPE;
        }
    }

    public record UseBoon() implements CustomPacketPayload {
        public static final Type<UseBoon> TYPE = typeOf("use_boon");
        public static final StreamCodec<ByteBuf, UseBoon> CODEC = StreamCodec.unit(new UseBoon());

        public Type<UseBoon> type() {
            return TYPE;
        }
    }

    public record TreeViewer(UUID player, boolean open, String sectorKey) implements CustomPacketPayload {
        public static final Type<TreeViewer> TYPE = typeOf("tree_viewer");
        public static final StreamCodec<ByteBuf, TreeViewer> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, TreeViewer::player, ByteBufCodecs.BOOL, TreeViewer::open,
                ByteBufCodecs.STRING_UTF8, TreeViewer::sectorKey, TreeViewer::new);

        public Type<TreeViewer> type() {
            return TYPE;
        }
    }

    public record Gesture(UUID player, String animation) implements CustomPacketPayload {
        public static final Type<Gesture> TYPE = typeOf("gesture");
        public static final StreamCodec<ByteBuf, Gesture> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Gesture::player, ByteBufCodecs.STRING_UTF8, Gesture::animation, Gesture::new);

        public Type<Gesture> type() {
            return TYPE;
        }
    }

    public record Stargaze() implements CustomPacketPayload {
        public static final Type<Stargaze> TYPE = typeOf("stargaze");
        public static final StreamCodec<ByteBuf, Stargaze> CODEC = StreamCodec.unit(new Stargaze());

        public Type<Stargaze> type() {
            return TYPE;
        }
    }

    public record OpenScreen(boolean resetMode) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = typeOf("open_screen");
        public static final StreamCodec<ByteBuf, OpenScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OpenScreen::resetMode, OpenScreen::new);

        public Type<OpenScreen> type() {
            return TYPE;
        }
    }
}
