package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.api.SkillEvents;
import dev.lesteoff.celestialskilltree.graph.SkillEffect;
import dev.lesteoff.celestialskilltree.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Triggers {
    public static final String ON_SPELL_CAST = "celestialskilltree:on_spell_cast";
    public static final String ON_SPELL_CRIT = "celestialskilltree:on_spell_crit";
    public static final String ON_MANA_EMPTY = "celestialskilltree:on_mana_empty";
    public static final String ON_GLYPH_CAST = "celestialskilltree:on_glyph_cast";
    public static final String ON_SPELL_RESOLVE = "celestialskilltree:on_spell_resolve";
    public static final String ON_COMBO_FINISHER = "celestialskilltree:on_combo_finisher";
    public static final String ON_HEADSHOT = "celestialskilltree:on_headshot";
    public static final String ON_RELOAD = "celestialskilltree:on_reload";
    public static final String ON_WALL_JUMP = "celestialskilltree:on_wall_jump";
    public static final String ON_ROLL = "celestialskilltree:on_roll";
    public static final String ON_BOON_KEY = "celestialskilltree:on_boon_key";
    public static final String ON_FATAL_BLOW = "celestialskilltree:on_fatal_blow";
    private static final Set<String> NEEDING_A_LIVING_TARGET = Set.of("target_effect", "ignite_target", "blink_behind_target", "web_target",
            "launch_target", "smite_target", "chaos_target");

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
            if (!candidate.trigger().equals(trigger)) {
                continue;
            }
            boolean targetMissing = NEEDING_A_LIVING_TARGET.contains(candidate.action().type()) && (target == null || !target.isAlive());
            boolean conditionHolds = candidate.condition().map(condition -> EffectEngine.holds(player, condition, null)).orElse(true);
            boolean fires = !targetMissing && conditionHolds
                    && ModAttachments.cooldowns(player).tryUse(keyed.key(), now, candidate.cooldownTicks());
            if (fires && SkillEvents.allows(new SkillEvents.TriggerFiring(player, trigger, candidate.action().type(), target))) {
                run(player, candidate.action(), target);
            }
        }
    }

    private static void run(ServerPlayer player, SkillEffect.Action action, LivingEntity target) {
        switch (action.type()) {
            case "heal" -> player.heal((float) action.amount());
            case "self_effect" -> applyMobEffect(player, action, player);
            case "target_effect" -> applyMobEffect(player, action, target);
            case "ignite_target" -> target.igniteForTicks(action.durationTicks());
            case "gust" -> BoonActions.gust(player, action.amount());
            case "pull" -> BoonActions.pull(player, action.amount());
            case "burst" -> BoonActions.burst(player, target, action.amount());
            case "blink_behind_target" -> BoonActions.blinkBehind(player, target);
            case "stacking_self_effect" -> BoonActions.stackEffect(player, action);
            case "area_effect" -> BoonActions.areaEffect(player, action);
            case "web_target" -> BoonActions.web(player, target);
            case "lunge" -> BoonActions.lunge(player, action.amount());
            case "chaos_target" -> BoonActions.chaos(player, target, action.amount());
            case "launch_target" -> BoonActions.launch(target, action.amount());
            case "smite_target" -> BoonActions.smite(player, target, action.amount());
            case "cheat_death" -> BoonActions.cheatDeath(player, action.amount());
            case "raise_ally" -> BoonActions.raiseAlly(player, action, target);
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
