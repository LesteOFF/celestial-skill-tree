package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Triggers {
    public static final String ON_SPELL_CAST = "nostalgiaskills:on_spell_cast";
    public static final String ON_SPELL_CRIT = "nostalgiaskills:on_spell_crit";
    public static final String ON_MANA_EMPTY = "nostalgiaskills:on_mana_empty";
    public static final String ON_GLYPH_CAST = "nostalgiaskills:on_glyph_cast";
    public static final String ON_SPELL_RESOLVE = "nostalgiaskills:on_spell_resolve";
    public static final String ON_COMBO_FINISHER = "nostalgiaskills:on_combo_finisher";
    public static final String ON_HEADSHOT = "nostalgiaskills:on_headshot";
    public static final String ON_RELOAD = "nostalgiaskills:on_reload";
    public static final String ON_WALL_JUMP = "nostalgiaskills:on_wall_jump";
    public static final String ON_ROLL = "nostalgiaskills:on_roll";

    private static final Map<String, ActionHandler> EXTRA_ACTIONS = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface ActionHandler {
        void run(ServerPlayer player, SkillEffect.Action action);
    }

    private Triggers() {
    }

    public static void registerAction(String type, ActionHandler handler) {
        EXTRA_ACTIONS.put(type, handler);
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
                ActionHandler extra = EXTRA_ACTIONS.get(action.type());
                if (extra != null) {
                    extra.run(player, action);
                }
            }
        }
    }

    private static void applyMobEffect(ServerPlayer player, SkillEffect.Action action, LivingEntity receiver) {
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(action.effect())).ifPresent(mobEffect ->
                receiver.addEffect(new MobEffectInstance(mobEffect, action.durationTicks(), action.amplifier()), player));
    }
}
