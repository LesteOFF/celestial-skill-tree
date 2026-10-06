package dev.nostalgiacraft.skills.server;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.data.SkillTreeManager;
import dev.nostalgiacraft.skills.progression.FallenStarService;
import dev.nostalgiacraft.skills.registry.FallenStarItem;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
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
        return SkillTreeManager.graph()
                .flatMap(graph -> FallenStarService.dropNewest(ModAttachments.state(player), graph))
                .map(allocation -> {
                    ItemEntity star = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(),
                            FallenStarItem.of(player, allocation));
                    star.setGlowingTag(true);
                    star.setUnlimitedLifetime();
                    player.level().addFreshEntity(star);
                    return star;
                });
    }
}
