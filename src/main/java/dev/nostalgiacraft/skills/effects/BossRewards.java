package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModItems;
import dev.nostalgiacraft.skills.registry.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class BossRewards {
    private static final Map<LivingEntity, Set<UUID>> PARTICIPANTS = new WeakHashMap<>();

    private BossRewards() {
    }

    public static boolean isRealPlayer(Object killer) {
        return killer instanceof ServerPlayer && !(killer instanceof FakePlayer);
    }

    @SubscribeEvent
    public static void onBossHurt(LivingDamageEvent.Post event) {
        LivingEntity boss = event.getEntity();
        boolean joinsTheFight = event.getNewDamage() > 0 && !boss.level().isClientSide()
                && boss.getType().is(ModTags.FIRST_KILL_FRAGMENT) && isRealPlayer(event.getSource().getEntity());
        if (joinsTheFight) {
            PARTICIPANTS.computeIfAbsent(boss, fought -> new HashSet<>()).add(event.getSource().getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent event) {
        LivingEntity boss = event.getEntity();
        int fragments = ServerConfig.BOSS_FRAGMENTS.get();
        boolean rewarded = fragments > 0 && !boss.level().isClientSide() && boss.getType().is(ModTags.FIRST_KILL_FRAGMENT);
        Set<UUID> fighters = new HashSet<>(Optional.ofNullable(PARTICIPANTS.remove(boss)).orElse(Set.of()));
        if (rewarded && isRealPlayer(event.getSource().getEntity())) {
            fighters.add(event.getSource().getEntity().getUUID());
            fighters.stream().map(boss.getServer().getPlayerList()::getPlayer).filter(Objects::nonNull)
                    .forEach(fighter -> reward(fighter, boss, fragments));
        }
    }

    private static void reward(ServerPlayer killer, LivingEntity boss, int fragments) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
        Set<String> killed = killer.getData(ModAttachments.BOSS_KILLS);
        if (killed.add(type)) {
            killer.setData(ModAttachments.BOSS_KILLS, killed);
            ItemHandlerHelper.giveItemToPlayer(killer, new ItemStack(ModItems.STAR_FRAGMENT.get(), fragments));
            killer.sendSystemMessage(Component.translatable("message.nostalgiaskills.boss_fragments", fragments, boss.getDisplayName()));
        }
    }
}
