package dev.lesteoff.celestialskilltree.server;

import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.data.SkillTreeManager;
import dev.lesteoff.celestialskilltree.progression.FallenStarService;
import dev.lesteoff.celestialskilltree.progression.PlayerSkillState;
import dev.lesteoff.celestialskilltree.registry.FallenStarItem;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class FallenStars {
    public enum Mode { OFF, PVP_ONLY, ALWAYS }

    private FallenStars() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && applies(ServerConfig.DROP_STAR_ON_DEATH.get(), player, event.getSource())) {
            drop(player);
        }
    }

    static boolean applies(Mode mode, ServerPlayer victim, DamageSource source) {
        boolean killedByOtherPlayer = source.getEntity() instanceof Player && source.getEntity() != victim;
        return mode == Mode.ALWAYS || (mode == Mode.PVP_ONLY && killedByOtherPlayer);
    }

    public static Optional<ItemEntity> drop(ServerPlayer player) {
        PlayerSkillState state = ModAttachments.state(player);
        return SkillTreeManager.graph()
                .flatMap(graph -> FallenStarService.newestDroppable(state, graph))
                .filter(allocation -> SkillEvents.allows(new SkillEvents.FallenStarDropping(player, allocation.nodeId())))
                .map(allocation -> {
                    FallenStarService.drop(state, allocation);
                    SkillEvents.post(new SkillEvents.NodesLost(player, List.of(allocation.nodeId())));
                    ItemEntity star = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(),
                            FallenStarItem.of(player, allocation));
                    star.setGlowingTag(true);
                    star.setUnlimitedLifetime();
                    player.level().addFreshEntity(star);
                    return star;
                });
    }
}
