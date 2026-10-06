package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Executes trigger effects. Actions only heal or apply mob effects, never deal damage, so a trigger can never
 * cause another damage event that re-fires it (no recursion). Each effect has its own persisted cooldown.
 */
public final class Triggers {
    private Triggers() {
    }

    public static void fire(ServerPlayer p, String trigger, LivingEntity target) {
        CompiledBuild cb = EffectEngine.build(p);
        if (cb.triggers().isEmpty()) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        for (CompiledBuild.Keyed<SkillEffect.Trigger> k : cb.triggers()) {
            SkillEffect.Trigger t = k.effect();
            if (!t.trigger().equals(trigger)) {
                continue;
            }
            if (t.action().type().equals("target_effect") && (target == null || !target.isAlive())) {
                continue;
            }
            if (!ModAttachments.cooldowns(p).tryUse(k.key(), now, t.cooldownTicks())) {
                continue;
            }
            run(p, t.action(), target);
        }
    }

    private static void run(ServerPlayer p, SkillEffect.Action a, LivingEntity target) {
        switch (a.type()) {
            case "heal" -> p.heal((float) a.amount());
            case "self_effect" -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(a.effect()))
                    .ifPresent(h -> p.addEffect(new MobEffectInstance(h, a.durationTicks(), a.amplifier()), p));
            case "target_effect" -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(a.effect()))
                    .ifPresent(h -> target.addEffect(new MobEffectInstance(h, a.durationTicks(), a.amplifier()), p));
            default -> {
            }
        }
    }
}
