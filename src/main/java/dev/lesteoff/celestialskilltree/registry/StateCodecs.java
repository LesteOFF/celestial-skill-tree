package dev.lesteoff.celestialskilltree.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.progression.Allocation;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;

import java.util.List;
import java.util.Optional;

public final class StateCodecs {
    public static final Codec<Allocation> ALLOCATION = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("node").forGetter(Allocation::nodeId),
            Codec.INT.optionalFieldOf("rank", 1).forGetter(Allocation::rank),
            Codec.INT.fieldOf("paid").forGetter(Allocation::paidCost),
            Codec.LONG.fieldOf("order").forGetter(Allocation::acquisitionIndex)
    ).apply(i, Allocation::new));

    private static final MapCodec<PlayerSkillState> CORE = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", PlayerSkillState.SCHEMA_VERSION).forGetter(s -> PlayerSkillState.SCHEMA_VERSION),
            Codec.STRING.optionalFieldOf("tree", "celestialskilltree:main").forGetter(PlayerSkillState::treeId),
            Codec.STRING.optionalFieldOf("starting_root").forGetter(PlayerSkillState::startingRoot),
            ALLOCATION.listOf().optionalFieldOf("allocations", List.of()).forGetter(PlayerSkillState::orderedAllocations),
            Codec.LONG.optionalFieldOf("next_order", 0L).forGetter(PlayerSkillState::nextAcquisition),
            Codec.INT.optionalFieldOf("purchased", 0).forGetter(PlayerSkillState::purchasedPoints),
            Codec.INT.optionalFieldOf("net_granted", 0).forGetter(PlayerSkillState::netGrantedPoints),
            Codec.INT.optionalFieldOf("available", 0).forGetter(PlayerSkillState::availablePoints),
            Codec.LONG.optionalFieldOf("lifetime_spent", 0L).forGetter(PlayerSkillState::lifetimeSpent),
            Codec.LONG.optionalFieldOf("lifetime_refunded", 0L).forGetter(PlayerSkillState::lifetimeRefunded),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(PlayerSkillState::stateRevision),
            Codec.STRING.optionalFieldOf("graph_fingerprint", "").forGetter(PlayerSkillState::reconciledFingerprint),
            Codec.STRING.listOf().optionalFieldOf("migrations", List.of()).forGetter(s -> List.copyOf(s.migrationMarkers())),
            Codec.STRING.listOf().optionalFieldOf("audit", List.of()).forGetter(PlayerSkillState::audit),
            Codec.STRING.optionalFieldOf("sign").forGetter(PlayerSkillState::sign),
            Codec.STRING.listOf().optionalFieldOf("discoveries", List.of()).forGetter(s -> List.copyOf(s.discoveries()))
    ).apply(i, StateCodecs::restore));

    public static final Codec<PlayerSkillState> STATE = RecordCodecBuilder.create(i -> i.group(
            CORE.forGetter(state -> state),
            Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(s -> List.copyOf(s.disabled()))
    ).apply(i, PlayerSkillState::withRestoredDisabled));

    private StateCodecs() {
    }

    @SuppressWarnings("unused")
    private static PlayerSkillState restore(int schema, String tree, Optional<String> root, List<Allocation> allocs,
                                            long next, int purchased, int granted, int available, long spent,
                                            long refunded, long revision, String fp, List<String> markers,
                                            List<String> audit, Optional<String> sign, List<String> discoveries) {
        PlayerSkillState state = PlayerSkillState.restore(tree, root.orElse(null), allocs, next, purchased, granted, available, spent,
                refunded, revision, fp, markers, audit).withRestoredSign(sign).withRestoredDiscoveries(discoveries);
        if (state.repairedOnLoad()) {
            CelestialSkillTree.LOGGER.warn("Repaired an inconsistent skill point ledger (purchased {}, granted {}, available {}); now granted {}, available {}",
                    purchased, granted, available, state.netGrantedPoints(), state.availablePoints());
        }
        return state;
    }
}
