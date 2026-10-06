package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class Triggers {
    private Triggers() {
    }

    public static void fire(ServerPlayer player, String trigger, LivingEntity target) {
        CompiledBuild build = EffectEngine.build(player);
        if (build.triggers().isEmpty()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        for (CompiledBuild.Keyed<SkillEffect.Trigger> keyed : build.triggers()) {
            SkillEffect.Trigger candidate = keyed.effect();
            boolean targetMissing = candidate.action().type().equals("target_effect") && (target == null || !target.isAlive());
            boolean fires = candidate.trigger().equals(trigger) && !targetMissing
                    && ModAttachments.cooldowns(player).tryUse(keyed.key(), now, candidate.cooldownTicks());
            if (fires) {
                run(player, candidate.action(), target);
            }
        }
    }

    private static void run(ServerPlayer player, SkillEffect.Action action, LivingEntity target) {
        switch (action.type()) {
            case "heal" -> player.heal((float) action.amount());
            case "self_effect" -> applyMobEffect(player, action, player);
            case "target_effect" -> applyMobEffect(player, action, target);
            default -> {
            }
        }
    }

    private static void applyMobEffect(ServerPlayer player, SkillEffect.Action action, LivingEntity receiver) {
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(action.effect())).ifPresent(mobEffect ->
                receiver.addEffect(new MobEffectInstance(mobEffect, action.durationTicks(), action.amplifier()), player));
    }
}
