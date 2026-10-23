package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class ConsumptionEvents {
    private static final Map<UUID, Map<Holder<MobEffect>, Integer>> EFFECTS_BEFORE_MEAL = new HashMap<>();

    private ConsumptionEvents() {
    }

    private static boolean isMeal(ItemStack stack) {
        return !(stack.getItem() instanceof PotionItem)
                && (stack.has(DataComponents.FOOD) || stack.getUseAnimation() == UseAnim.EAT || stack.getUseAnimation() == UseAnim.DRINK);
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        EFFECTS_BEFORE_MEAL.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player && isMeal(event.getItem())) {
            Map<Holder<MobEffect>, Integer> active = new HashMap<>();
            player.getActiveEffects().forEach(effect -> active.put(effect.getEffect(), effect.getDuration()));
            EFFECTS_BEFORE_MEAL.put(player.getUUID(), active);
        }
    }

    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        EFFECTS_BEFORE_MEAL.remove(event.getEntity().getUUID());
    }

    private static void finishMeal(ServerPlayer player, Map<Holder<MobEffect>, Integer> before) {
        double bonus = EffectEngine.attributeValue(player, ModAttributes.POTION_DURATION);
        if (bonus != 0) {
            for (MobEffectInstance active : List.copyOf(player.getActiveEffects())) {
                boolean gainedFromMeal = active.getDuration() > before.getOrDefault(active.getEffect(), 0);
                if (gainedFromMeal && active.getEffect().value().isBeneficial() && !active.getEffect().value().isInstantenous()) {
                    player.addEffect(new MobEffectInstance(active.getEffect(), (int) Math.max(1, Math.round(active.getDuration() * (1.0 + bonus))),
                            active.getAmplifier(), active.isAmbient(), active.isVisible(), active.showIcon()));
                }
            }
        }
        Triggers.fire(player, "on_eat", null);
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        Map<Holder<MobEffect>, Integer> beforeMeal = EFFECTS_BEFORE_MEAL.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer eater && beforeMeal != null && isMeal(event.getItem())) {
            finishMeal(eater, beforeMeal);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getItem().getItem() instanceof PotionItem)) {
            return;
        }
        PotionContents contents = event.getItem().get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        boolean anyBeneficial = false;
        double bonus = EffectEngine.attributeValue(player, ModAttributes.POTION_DURATION);
        for (MobEffectInstance drunk : contents.getAllEffects()) {
            if (drunk.getEffect().value().isBeneficial() && !drunk.getEffect().value().isInstantenous()) {
                anyBeneficial = true;
                extendDuration(player, drunk, bonus);
            }
        }
        if (anyBeneficial) {
            Triggers.fire(player, "on_potion", null);
        }
    }

    private static void extendDuration(ServerPlayer player, MobEffectInstance drunk, double bonus) {
        if (bonus == 0) {
            return;
        }
        int target = (int) Math.max(1, Math.round(drunk.getDuration() * (1.0 + bonus)));
        MobEffectInstance active = player.getEffect(drunk.getEffect());
        if (active != null && active.getAmplifier() == drunk.getAmplifier() && active.getDuration() < target) {
            player.addEffect(new MobEffectInstance(drunk.getEffect(), target, drunk.getAmplifier(), drunk.isAmbient(),
                    drunk.isVisible(), drunk.showIcon()));
        }
    }

    public static void forget(UUID id) {
        EFFECTS_BEFORE_MEAL.remove(id);
    }
}
