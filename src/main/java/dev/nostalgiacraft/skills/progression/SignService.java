package dev.nostalgiacraft.skills.progression;

import java.util.List;
import java.util.Optional;

public final class SignService {
    private SignService() {
    }

    public static Optional<String> assignIfMissing(PlayerSkillState state, List<String> sortedSignIds, long worldDay) {
        if (state.sign().isPresent() || sortedSignIds.isEmpty()) {
            return Optional.empty();
        }
        String signId = sortedSignIds.get((int) Math.floorMod(worldDay, (long) sortedSignIds.size()));
        state.setSign(signId);
        state.bumpRevision();
        state.log("born under sign " + signId + " on day " + worldDay);
        return Optional.of(signId);
    }
}
