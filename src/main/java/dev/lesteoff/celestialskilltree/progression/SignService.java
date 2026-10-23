package dev.lesteoff.celestialskilltree.progression;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class SignService {
    private SignService() {
    }

    public static Optional<String> assignIfMissing(PlayerSkillState state, List<String> sortedSignIds, long worldDay, UUID playerId) {
        if (state.sign().isPresent() || sortedSignIds.isEmpty()) {
            return Optional.empty();
        }
        String signId = sortedSignIds.get((int) Math.floorMod(worldDay + playerId.hashCode(), (long) sortedSignIds.size()));
        state.setSign(signId);
        state.bumpRevision();
        state.log("born under sign " + signId + " on day " + worldDay);
        return Optional.of(signId);
    }

    public static void set(PlayerSkillState state, String signId) {
        state.setSign(signId);
        state.bumpRevision();
        state.log("sign set to " + signId);
    }

    public static Optional<String> reroll(PlayerSkillState state, List<String> signIds, RandomGenerator random) {
        List<String> others = signIds.stream().filter(signId -> state.sign().filter(signId::equals).isEmpty()).toList();
        if (others.isEmpty()) {
            return Optional.empty();
        }
        String signId = others.get(random.nextInt(others.size()));
        set(state, signId);
        return Optional.of(signId);
    }
}
