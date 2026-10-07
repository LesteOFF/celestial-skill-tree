package dev.nostalgiacraft.skills.network;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import dev.nostalgiacraft.skills.progression.XpCurve;
import dev.nostalgiacraft.skills.registry.StateCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public final class Payloads {
    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, name));
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

    public record DonateXp(UUID requestId, long expectedStateRevision, int amount) implements CustomPacketPayload {
        public static final Type<DonateXp> TYPE = typeOf("donate_xp");
        public static final StreamCodec<ByteBuf, DonateXp> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, DonateXp::requestId, ByteBufCodecs.VAR_LONG, DonateXp::expectedStateRevision,
                ByteBufCodecs.VAR_INT, DonateXp::amount, DonateXp::new);

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
                          boolean nodeRefundsEnabled, boolean orbRequiredForNodeRefund) {
        public static final StreamCodec<ByteBuf, Economy> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Economy::nextPointCost, ByteBufCodecs.VAR_INT, Economy::pointLimit,
                ByteBufCodecs.BOOL, Economy::xpPurchaseEnabled, ByteBufCodecs.BOOL, Economy::orbRequired,
                ByteBufCodecs.BOOL, Economy::nodeRefundsEnabled, ByteBufCodecs.BOOL, Economy::orbRequiredForNodeRefund,
                Economy::new);
    }

    static final StreamCodec<ByteBuf, XpCurve> CURVE = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, XpCurve::base, ByteBufCodecs.VAR_INT, XpCurve::linear,
            ByteBufCodecs.VAR_INT, XpCurve::quadratic, ByteBufCodecs.DOUBLE, XpCurve::discount, XpCurve::new);

    public record TierInfo(String id, long xpRequired) {
        public static final StreamCodec<ByteBuf, TierInfo> CODEC = StreamCodec.composite(
                NODE_ID, TierInfo::id, ByteBufCodecs.VAR_LONG, TierInfo::xpRequired, TierInfo::new);
    }

    public record WorldRules(SkyEvent skyEvent, long communityXp, List<TierInfo> communityTiers, List<String> signIds) {
        public static final int MAX_TIERS = 64;
        public static final int MAX_SIGNS = 64;
        public static final WorldRules NONE = new WorldRules(SkyEvent.NONE, 0, List.of(), List.of());
        public static final StreamCodec<ByteBuf, WorldRules> CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(SkyEvent::byId, SkyEvent::ordinal), WorldRules::skyEvent, ByteBufCodecs.VAR_LONG, WorldRules::communityXp,
                TierInfo.CODEC.apply(ByteBufCodecs.list(MAX_TIERS)), WorldRules::communityTiers,
                NODE_ID.apply(ByteBufCodecs.list(MAX_SIGNS)), WorldRules::signIds, WorldRules::new);

        public List<Long> thresholds() {
            return communityTiers.stream().map(TierInfo::xpRequired).toList();
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

    public record TransactionResult(UUID requestId, boolean ok, String messageKey, String detail)
            implements CustomPacketPayload {
        public static final Type<TransactionResult> TYPE = typeOf("transaction_result");
        public static final StreamCodec<ByteBuf, TransactionResult> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, TransactionResult::requestId, ByteBufCodecs.BOOL, TransactionResult::ok,
                ByteBufCodecs.stringUtf8(128), TransactionResult::messageKey, ByteBufCodecs.stringUtf8(512),
                TransactionResult::detail, TransactionResult::new);

        public Type<TransactionResult> type() {
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
