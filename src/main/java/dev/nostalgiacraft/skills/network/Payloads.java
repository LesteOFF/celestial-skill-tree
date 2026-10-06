package dev.nostalgiacraft.skills.network;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.GraphCodec;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
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

/** All mod payloads. Client-to-server payloads carry intents only; the server decides everything. */
public final class Payloads {
    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, name));
    }

    static final StreamCodec<ByteBuf, String> NODE_ID = ByteBufCodecs.stringUtf8(64);

    // ------------------------------------------------------------------ client -> server

    /** Sent when the screen opens; the graph is only resent when the client's fingerprint is outdated. */
    public record RequestSync(String knownFingerprint) implements CustomPacketPayload {
        public static final Type<RequestSync> TYPE = typeOf("request_sync");
        public static final StreamCodec<ByteBuf, RequestSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), RequestSync::knownFingerprint, RequestSync::new);

        public Type<RequestSync> type() {
            return TYPE;
        }
    }

    public record BuyPoint(UUID requestId, long expectedStateRevision) implements CustomPacketPayload {
        public static final Type<BuyPoint> TYPE = typeOf("buy_point");
        public static final StreamCodec<ByteBuf, BuyPoint> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BuyPoint::requestId, ByteBufCodecs.VAR_LONG, BuyPoint::expectedStateRevision,
                BuyPoint::new);

        public Type<BuyPoint> type() {
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

    /** Intent to refund one allocated skill; the server decides which dependent allocations go with it. */
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

    // ------------------------------------------------------------------ server -> client

    public record GraphSync(int revision, byte[] data) implements CustomPacketPayload {
        public static final Type<GraphSync> TYPE = typeOf("graph_sync");
        public static final StreamCodec<ByteBuf, GraphSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GraphSync::revision, ByteBufCodecs.byteArray(GraphCodec.MAX_BYTES), GraphSync::data,
                GraphSync::new);

        public Type<GraphSync> type() {
            return TYPE;
        }
    }

    /** Economy values the client needs to present (never to decide). */
    public record Economy(int nextPointCost, int pointLimit, boolean xpPurchaseEnabled, boolean orbRequired,
                          boolean nodeRefundsEnabled, boolean orbRequiredForNodeRefund) {
        public static final StreamCodec<ByteBuf, Economy> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Economy::nextPointCost, ByteBufCodecs.VAR_INT, Economy::pointLimit,
                ByteBufCodecs.BOOL, Economy::xpPurchaseEnabled, ByteBufCodecs.BOOL, Economy::orbRequired,
                ByteBufCodecs.BOOL, Economy::nodeRefundsEnabled, ByteBufCodecs.BOOL, Economy::orbRequiredForNodeRefund,
                Economy::new);
    }

    public record StateSync(int graphRevision, PlayerSkillState state, Economy economy) implements CustomPacketPayload {
        public static final Type<StateSync> TYPE = typeOf("state_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, StateSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StateSync::graphRevision,
                ByteBufCodecs.fromCodec(StateCodecs.STATE), StateSync::state,
                Economy.CODEC, StateSync::economy, StateSync::new);

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

    /** Asks the client to open the tree screen (command) or the reset confirmation (orb use). */
    public record OpenScreen(boolean resetMode) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = typeOf("open_screen");
        public static final StreamCodec<ByteBuf, OpenScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OpenScreen::resetMode, OpenScreen::new);

        public Type<OpenScreen> type() {
            return TYPE;
        }
    }
}
