package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.registry.ModAttachments;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import dev.nostalgiacraft.skills.registry.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side gameplay hooks for event effects. Damage numbers documented in ARCHITECTURE.md:
 * dealt bonuses are summed into one multiplier applied to the incoming amount (after vanilla weapon, enchantment
 * and vanilla-critical calculation, before armor); taken modifiers likewise, then damage reduction.
 */
@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class GameplayEvents {
    static final String ORIGIN_KEY = "nostalgiaskills_origin";
    private static final Map<UUID, Long> VANILLA_CRIT_TICK = new HashMap<>();
    private static final Map<UUID, PendingWear> PENDING_WEAR = new HashMap<>();

    private record PendingWear(ItemStack stack, int damageBefore, double roll) {
    }

    private GameplayEvents() {
    }

    static double attr(LivingEntity e, Holder<Attribute> h) {
        return e.getAttributes().hasAttribute(h) ? e.getAttributeValue(h) : 0.0;
    }

    static boolean melee(DamageSource s, ServerPlayer p) {
        return s.getEntity() == p && s.getDirectEntity() == p && s.is(DamageTypes.PLAYER_ATTACK);
    }

    static boolean projectile(DamageSource s, ServerPlayer p) {
        return s.getEntity() == p && s.getDirectEntity() != p && s.is(DamageTypeTags.IS_PROJECTILE);
    }

    static boolean inScope(String scope, DamageSource s, ServerPlayer attacker, boolean dealt) {
        return switch (scope) {
            case "melee" -> dealt ? melee(s, attacker) : s.getDirectEntity() instanceof LivingEntity && !s.is(DamageTypeTags.IS_PROJECTILE);
            case "projectile" -> dealt ? projectile(s, attacker) : s.is(DamageTypeTags.IS_PROJECTILE);
            case "magic" -> s.is(ModTags.MAGIC);
            case "fire" -> s.is(DamageTypeTags.IS_FIRE);
            default -> true;
        };
    }

    static double shotDistance(DamageSource s, LivingEntity victim) {
        Entity direct = s.getDirectEntity();
        if (direct == null || !direct.getPersistentData().contains(ORIGIN_KEY)) {
            return -1;
        }
        CompoundTag o = direct.getPersistentData().getCompound(ORIGIN_KEY);
        return new Vec3(o.getDouble("x"), o.getDouble("y"), o.getDouble("z")).distanceTo(victim.position());
    }

    /** Records where a player's projectile was fired; piercing hits measure from this same origin. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Projectile proj)) {
            return;
        }
        if (proj.getOwner() instanceof ServerPlayer && !proj.getPersistentData().contains(ORIGIN_KEY)) {
            CompoundTag o = new CompoundTag();
            o.putDouble("x", proj.getX());
            o.putDouble("y", proj.getY());
            o.putDouble("z", proj.getZ());
            proj.getPersistentData().put(ORIGIN_KEY, o);
        }
    }

    @SubscribeEvent
    public static void onVanillaCrit(CriticalHitEvent e) {
        if (e.isCriticalHit() && e.getEntity() instanceof ServerPlayer p) {
            VANILLA_CRIT_TICK.put(p.getUUID(), p.serverLevel().getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource src = e.getSource();
        // ---------------- defender side
        if (victim instanceof ServerPlayer def) {
            boolean dodgeable = !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && (src.getDirectEntity() instanceof LivingEntity || src.is(DamageTypeTags.IS_PROJECTILE))
                    && src.getEntity() != def;
            double dodge = Math.min(attr(def, ModAttributes.DODGE_CHANCE), ServerConfig.CAP_DODGE_CHANCE.get());
            if (dodgeable && dodge > 0 && def.getRandom().nextDouble() < dodge) {
                e.setCanceled(true);
                def.level().playSound(null, def.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.4f);
                Triggers.fire(def, "on_dodge", src.getEntity() instanceof LivingEntity l ? l : null);
                return;
            }
            double mult = 1.0;
            for (CompiledBuild.Keyed<SkillEffect.DamageModifier> k : EffectEngine.build(def).damage()) {
                SkillEffect.DamageModifier d = k.effect();
                if (!d.dealt() && inScope(d.scope(), src, def, false)
                        && d.condition().map(c -> EffectEngine.holds(def, c, null)).orElse(true)) {
                    mult += d.amount();
                }
            }
            if (src.is(ModTags.MAGIC)) {
                mult -= Math.min(attr(def, ModAttributes.MAGIC_RESISTANCE), ServerConfig.CAP_MAGIC_RESISTANCE.get());
            }
            mult -= Math.min(attr(def, ModAttributes.DAMAGE_REDUCTION), ServerConfig.CAP_DAMAGE_REDUCTION.get());
            if (mult != 1.0) {
                e.setAmount((float) (e.getAmount() * Math.max(0.05, Math.min(3.0, mult))));
            }
        }
        // ---------------- attacker side
        if (!(src.getEntity() instanceof ServerPlayer atk) || atk == victim) {
            return;
        }
        boolean isMelee = melee(src, atk);
        boolean isProjectile = projectile(src, atk);
        if (!isMelee && !isProjectile) {
            return; // environmental or indirect damage is never treated as weapon damage
        }
        double dist = isProjectile ? shotDistance(src, victim) : -1;
        EffectEngine.DamageContext ctx = new EffectEngine.DamageContext(victim, dist);
        double bonus = 0;
        ItemStack held = atk.getMainHandItem();
        if (isMelee) {
            if (held.is(ModTags.itemTag(ServerConfig.LIGHT_WEAPON_TAG.get()))) {
                bonus += attr(atk, ModAttributes.LIGHT_WEAPON_DAMAGE);
            }
            if (held.is(ModTags.itemTag(ServerConfig.HEAVY_WEAPON_TAG.get()))) {
                bonus += attr(atk, ModAttributes.HEAVY_WEAPON_DAMAGE);
            }
            if (atk.getHealth() < atk.getMaxHealth() * 0.5f) {
                bonus += attr(atk, ModAttributes.LOW_HEALTH_DAMAGE);
            }
        } else {
            bonus += attr(atk, ModAttributes.PROJECTILE_DAMAGE);
            if (dist > 12.0) {
                bonus += attr(atk, ModAttributes.LONG_RANGE_DAMAGE);
            }
            if (src.getDirectEntity() instanceof AbstractArrow arrow) {
                ItemStack weapon = arrow.getWeaponItem();
                if (weapon != null && weapon.is(ModTags.itemTag(ServerConfig.CROSSBOW_TAG.get()))) {
                    bonus += attr(atk, ModAttributes.CROSSBOW_DAMAGE);
                } else if (arrow.isCritArrow()) {
                    bonus += attr(atk, ModAttributes.CHARGED_SHOT_DAMAGE);
                }
            }
        }
        for (CompiledBuild.Keyed<SkillEffect.DamageModifier> k : EffectEngine.build(atk).damage()) {
            SkillEffect.DamageModifier d = k.effect();
            if (d.dealt() && inScope(d.scope(), src, atk, true)
                    && d.condition().map(c -> EffectEngine.holds(atk, c, ctx)).orElse(true)) {
                bonus += d.amount();
            }
        }
        float amount = (float) (e.getAmount() * Math.max(0.05, 1.0 + bonus));
        boolean vanillaCrit = VANILLA_CRIT_TICK.getOrDefault(atk.getUUID(), -1L) == atk.serverLevel().getGameTime();
        if (isMelee) {
            double chance = Math.min(attr(atk, ModAttributes.CRIT_CHANCE), ServerConfig.CAP_CRIT_CHANCE.get());
            // The mod's critical hit only rolls on hits that are not already vanilla (jump) criticals,
            // so the two never multiply together; both count as critical hits for on_crit triggers.
            if (!vanillaCrit && chance > 0 && atk.getRandom().nextDouble() < chance) {
                amount *= (float) (1.0 + attr(atk, ModAttributes.CRIT_DAMAGE));
                atk.level().playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1.2f);
                Triggers.fire(atk, "on_crit", victim);
            } else if (vanillaCrit) {
                Triggers.fire(atk, "on_crit", victim);
            }
        }
        e.setAmount(amount);
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide() || e.getNewDamage() <= 0) {
            return;
        }
        DamageSource src = e.getSource();
        if (src.getEntity() instanceof ServerPlayer atk && atk != victim) {
            if (melee(src, atk)) {
                double ls = Math.min(attr(atk, ModAttributes.LIFESTEAL), ServerConfig.CAP_LIFESTEAL.get());
                if (ls > 0 && ModAttachments.cooldowns(atk).tryUse("lifesteal", atk.serverLevel().getGameTime(),
                        ServerConfig.LIFESTEAL_COOLDOWN_TICKS.get())) {
                    atk.heal((float) (e.getNewDamage() * ls));
                }
            } else if (projectile(src, atk) && src.getDirectEntity() instanceof AbstractArrow arrow && arrow.isCritArrow()) {
                Triggers.fire(atk, "on_full_charge_hit", victim);
            }
        }
        if (victim instanceof ServerPlayer p && p.isAlive()) {
            Triggers.fire(p, "on_hurt", src.getEntity() instanceof LivingEntity l ? l : null);
            float max = p.getMaxHealth();
            float after = p.getHealth();
            float before = after + e.getNewDamage();
            if (before >= max * 0.3f && after < max * 0.3f) {
                Triggers.fire(p, "on_low_health", null);
            }
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent e) {
        if (e.getBlocked() && e.getEntity() instanceof ServerPlayer p) {
            Triggers.fire(p, "on_block", e.getDamageSource().getEntity() instanceof LivingEntity l ? l : null);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide() || !(e.getSource().getEntity() instanceof ServerPlayer p) || p == e.getEntity()) {
            return;
        }
        Triggers.fire(p, "on_kill", null);
        if (projectile(e.getSource(), p)) {
            Triggers.fire(p, "on_projectile_kill", null);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            double bonus = attr(p, ModAttributes.HEALING_RECEIVED);
            if (bonus != 0) {
                e.setAmount((float) Math.max(0, e.getAmount() * (1.0 + bonus)));
            }
        }
    }

    /**
     * Beneficial effects from a drunk potion last (1 + potion_duration) times their potion duration. The target
     * duration is computed from the potion itself, so drinking again refreshes but can never compound.
     */
    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getItem().getItem() instanceof PotionItem)) {
            return;
        }
        PotionContents contents = e.getItem().get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        boolean anyBeneficial = false;
        double bonus = attr(p, ModAttributes.POTION_DURATION);
        for (MobEffectInstance fx : contents.getAllEffects()) {
            if (!fx.getEffect().value().isBeneficial() || fx.getEffect().value().isInstantenous()) {
                continue;
            }
            anyBeneficial = true;
            if (bonus == 0) {
                continue;
            }
            int target = (int) Math.max(1, Math.round(fx.getDuration() * (1.0 + bonus)));
            MobEffectInstance active = p.getEffect(fx.getEffect());
            if (active != null && active.getAmplifier() == fx.getAmplifier() && active.getDuration() < target) {
                p.addEffect(new MobEffectInstance(fx.getEffect(), target, fx.getAmplifier(), fx.isAmbient(),
                        fx.isVisible(), fx.showIcon()));
            }
        }
        if (anyBeneficial) {
            Triggers.fire(p, "on_potion", null);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        // Runs on both sides with the synced attribute so client break prediction matches the server.
        ItemStack tool = e.getEntity().getMainHandItem();
        if (tool.is(ModTags.itemTag(ServerConfig.MINING_TOOL_TAG.get())) && tool.isCorrectToolForDrops(e.getState())) {
            double bonus = attr(e.getEntity(), ModAttributes.MINING_SPEED);
            if (bonus != 0) {
                e.setNewSpeed((float) (e.getNewSpeed() * Math.max(0.1, 1.0 + bonus)));
            }
        }
    }

    /**
     * Tool wear: the break event fires before vanilla damages the tool. We remember the damage value and settle it
     * at the end of the tick: a positive preservation roll undoes that break's wear, a negative value may add one
     * extra point. Each break is settled exactly once, so wear is never charged twice.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) {
            return;
        }
        ItemStack tool = p.getMainHandItem();
        if (!tool.is(ModTags.itemTag(ServerConfig.MINING_TOOL_TAG.get())) || !tool.isCorrectToolForDrops(e.getState())) {
            return;
        }
        Triggers.fire(p, "on_block_break", null);
        double pres = attr(p, ModAttributes.TOOL_PRESERVATION);
        if (pres != 0 && tool.isDamageableItem()) {
            PENDING_WEAR.put(p.getUUID(), new PendingWear(tool, tool.getDamageValue(), p.getRandom().nextDouble()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        PendingWear w = PENDING_WEAR.remove(p.getUUID());
        if (w != null && !w.stack().isEmpty() && w.stack().getDamageValue() > w.damageBefore()) {
            double pres = attr(p, ModAttributes.TOOL_PRESERVATION);
            if (pres > 0 && w.roll() < pres) {
                w.stack().setDamageValue(w.damageBefore());
            } else if (pres < 0 && w.roll() < -pres) {
                w.stack().hurtAndBreak(1, p.serverLevel(), p, item -> { });
            }
        }
        if (p.tickCount % 10 == 0) {
            EffectEngine.updateConditionals(p);
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            EffectEngine.updateConditionals(p);
        }
    }

    public static void forget(UUID id) {
        VANILLA_CRIT_TICK.remove(id);
        PENDING_WEAR.remove(id);
    }
}
