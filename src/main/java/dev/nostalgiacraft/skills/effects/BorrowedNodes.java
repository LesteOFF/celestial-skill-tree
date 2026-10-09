package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class BorrowedNodes {
    private static final String SOURCE_PREFIX = "borrowed/";
    private static final List<Function<Player, Collection<String>>> LENDERS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Set<String>> APPLIED = new HashMap<>();

    private BorrowedNodes() {
    }

    public static void registerLender(Function<Player, Collection<String>> lender) {
        LENDERS.add(lender);
    }

    public static void refreshIfChanged(ServerPlayer player) {
        boolean changed = !LENDERS.isEmpty() && !lentTo(player).equals(APPLIED.getOrDefault(player.getUUID(), Set.of()));
        if (changed) {
            EffectEngine.refresh(player);
        }
    }

    static Map<String, List<SkillEffect>> effects(SkillGraphSnapshot graph, ServerPlayer player) {
        Set<String> lent = lentTo(player);
        APPLIED.put(player.getUUID(), lent);
        Map<String, List<SkillEffect>> sources = new LinkedHashMap<>();
        lent.forEach(id -> graph.node(id).ifPresent(node -> sources.put(SOURCE_PREFIX + id, node.effects())));
        return sources;
    }

    static void forget(UUID player) {
        APPLIED.remove(player);
    }

    public static Set<String> lentTo(Player player) {
        Set<String> lent = new TreeSet<>();
        LENDERS.forEach(lender -> lent.addAll(lender.apply(player)));
        return lent;
    }
}
