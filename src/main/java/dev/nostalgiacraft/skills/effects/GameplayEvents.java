package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.api.SkillEvents;
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
import net.minecraft.world.level.block.state.BlockState;
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

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
public final class GameplayEvents {
    static final String ORIGIN_KEY = "nostalgiaskills_origin";
    private static final double LONG_RANGE_BLOCKS = 12.0;
    private static final float LOW_HEALTH_DAMAGE_FRACTION = 0.5f;
    private static final float LOW_HEALTH_TRIGGER_FRACTION = 0.3f;
    private static final double MIN_DAMAGE_MULTIPLIER = 0.05;
    private static final double MAX_TAKEN_MULTIPLIER = 3.0;
    private static final double MIN_MINING_MULTIPLIER = 0.1;
    private static final int CONDITIONAL_REFRESH_TICKS = 10;
    private static final double NO_SHOT_DISTANCE = -1;
    private static final Map<UUID, Long> VANILLA_CRIT_TICK = new HashMap<>();
    private static final Map<UUID, PendingWear> PENDING_WEAR = new HashMap<>();

    private record PendingWear(ItemStack stack, int damageBefore, double roll) {
    }

    private GameplayEvents() {
    }

    static double attributeValue(LivingEntity entity, Holder<Attribute> attribute) {
        return entity.getAttributes().hasAttribute(attribute) ? entity.getAttributeValue(attribute) : 0.0;
    }

    static boolean melee(DamageSource source, ServerPlayer player) {
        return source.getEntity() == player && source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK);
    }

    static boolean byProjectile(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(ModTags.COUNTS_AS_PROJECTILE);
    }

    static boolean projectile(DamageSource source, ServerPlayer player) {
        return source.getEntity() == player && source.getDirectEntity() != player && byProjectile(source);
    }

    static boolean weapon(DamageSource source, ServerPlayer player) {
        return melee(source, player) || projectile(source, player);
    }

    static boolean inScope(String scope, DamageSource source, ServerPlayer player, boolean dealt) {
        if (SkillEffect.isTagScope(scope)) {
            return source.is(ModTags.damageTag(SkillEffect.tagOf(scope)));
        }
        if (dealt && !weapon(source, player)) {
            return false;
        }
        boolean byProjectile = byProjectile(source);
        return switch (scope) {
            case "melee" -> dealt ? melee(source, player) : source.getDirectEntity() instanceof LivingEntity && !byProjectile;
            case "projectile" -> dealt ? projectile(source, player) : byProjectile;
            case "magic" -> source.is(ModTags.MAGIC);
            case "fire" -> source.is(DamageTypeTags.IS_FIRE);
            default -> true;
        };
    }

    static double shotDistance(DamageSource source, LivingEntity victim) {
        Entity direct = source.getDirectEntity();
        if (direct == null || !direct.getPersistentData().contains(ORIGIN_KEY)) {
            return NO_SHOT_DISTANCE;
        }
        CompoundTag origin = direct.getPersistentData().getCompound(ORIGIN_KEY);
        return new Vec3(origin.getDouble("x"), origin.getDouble("y"), origin.getDouble("z")).distanceTo(victim.position());
    }

    private static LivingEntity livingAttacker(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker ? attacker : null;
    }

    private static double modifierSum(ServerPlayer player, DamageSource source, boolean dealt,
                                      EffectEngine.DamageContext context) {
        double sum = 0;
        for (CompiledBuild.Keyed<SkillEffect.DamageModifier> keyed : EffectEngine.build(player).damage()) {
            SkillEffect.DamageModifier modifier = keyed.effect();
            boolean applies = modifier.dealt() == dealt && inScope(modifier.scope(), source, player, dealt)
                    && modifier.condition().map(condition -> EffectEngine.holds(player, condition, context)).orElse(true);
            if (applies) {
                sum += modifier.amount();
            }
        }
        return sum;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        if (projectile.getOwner() instanceof ServerPlayer) {
            rememberOrigin(projectile);
        }
    }

    public static void rememberOrigin(Entity projectile) {
        if (projectile.getPersistentData().contains(ORIGIN_KEY)) {
            return;
        }
        CompoundTag origin = new CompoundTag();
        origin.putDouble("x", projectile.getX());
        origin.putDouble("y", projectile.getY());
        origin.putDouble("z", projectile.getZ());
        projectile.getPersistentData().put(ORIGIN_KEY, origin);
    }

    @SubscribeEvent
    public static void onVanillaCrit(CriticalHitEvent event) {
        if (event.isCriticalHit() && event.getEntity() instanceof ServerPlayer player) {
            VANILLA_CRIT_TICK.put(player.getUUID(), player.serverLevel().getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (victim instanceof ServerPlayer defender && !defend(event, defender, source)) {
            return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
            attack(event, attacker, victim, source);
        }
    }

    private static boolean defend(LivingIncomingDamageEvent event, ServerPlayer defender, DamageSource source) {
        if (dodges(defender, source)) {
            event.setCanceled(true);
            defender.level().playSound(null, defender.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.4f);
            Triggers.fire(defender, "on_dodge", livingAttacker(source));
            return false;
        }
        double multiplier = 1.0 + modifierSum(defender, source, false, null);
        if (source.is(ModTags.MAGIC)) {
            multiplier -= Math.min(attributeValue(defender, ModAttributes.MAGIC_RESISTANCE), ServerConfig.CAP_MAGIC_RESISTANCE.get());
        }
        multiplier -= Math.min(attributeValue(defender, ModAttributes.DAMAGE_REDUCTION), ServerConfig.CAP_DAMAGE_REDUCTION.get());
        if (multiplier != 1.0) {
            double clamped = Math.max(MIN_DAMAGE_MULTIPLIER, Math.min(MAX_TAKEN_MULTIPLIER, multiplier));
            event.setAmount((float) (event.getAmount() * clamped));
        }
        return true;
    }

    private static boolean dodges(ServerPlayer defender, DamageSource source) {
        boolean dodgeable = !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (source.getDirectEntity() instanceof LivingEntity || byProjectile(source))
                && source.getEntity() != defender;
        double chance = Math.min(attributeValue(defender, ModAttributes.DODGE_CHANCE), ServerConfig.CAP_DODGE_CHANCE.get());
        return dodgeable && chance > 0 && defender.getRandom().nextDouble() < chance;
    }

    private static void attack(LivingIncomingDamageEvent event, ServerPlayer attacker, LivingEntity victim,
                               DamageSource source) {
        boolean isMelee = melee(source, attacker);
        boolean isSpell = source.is(ModTags.SPELL_DAMAGE);
        double distance = isMelee ? NO_SHOT_DISTANCE : shotDistance(source, victim);
        double bonus = modifierSum(attacker, source, true, new EffectEngine.DamageContext(victim, distance));
        if (isMelee) {
            bonus += meleeBonus(attacker);
        } else if (projectile(source, attacker)) {
            bonus += projectileBonus(attacker, source, distance);
        } else if (bonus == 0 && !isSpell) {
            return;
        }
        float amount = (float) (event.getAmount() * Math.max(MIN_DAMAGE_MULTIPLIER, 1.0 + bonus));
        event.setAmount(isMelee || isSpell ? amount * criticalMultiplier(attacker, victim, isSpell) : amount);
    }

    private static double meleeBonus(ServerPlayer attacker) {
        ItemStack weapon = attacker.getMainHandItem();
        double bonus = 0;
        if (weapon.is(ModTags.itemTag(ServerConfig.LIGHT_WEAPON_TAG.get()))) {
            bonus += attributeValue(attacker, ModAttributes.LIGHT_WEAPON_DAMAGE);
        }
        if (weapon.is(ModTags.itemTag(ServerConfig.HEAVY_WEAPON_TAG.get()))) {
            bonus += attributeValue(attacker, ModAttributes.HEAVY_WEAPON_DAMAGE);
        }
        if (attacker.getHealth() < attacker.getMaxHealth() * LOW_HEALTH_DAMAGE_FRACTION) {
            bonus += attributeValue(attacker, ModAttributes.LOW_HEALTH_DAMAGE);
        }
        return bonus;
    }

    private static double projectileBonus(ServerPlayer attacker, DamageSource source, double distance) {
        double bonus = attributeValue(attacker, ModAttributes.PROJECTILE_DAMAGE);
        if (distance > LONG_RANGE_BLOCKS) {
            bonus += attributeValue(attacker, ModAttributes.LONG_RANGE_DAMAGE);
        }
        if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            ItemStack weapon = arrow.getWeaponItem();
            if (weapon != null && weapon.is(ModTags.itemTag(ServerConfig.CROSSBOW_TAG.get()))) {
                bonus += attributeValue(attacker, ModAttributes.CROSSBOW_DAMAGE);
            } else if (arrow.isCritArrow()) {
                bonus += attributeValue(attacker, ModAttributes.CHARGED_SHOT_DAMAGE);
            }
        }
        return bonus;
    }

    private static float criticalMultiplier(ServerPlayer attacker, LivingEntity victim, boolean spell) {
        boolean vanillaCrit = !spell
                && VANILLA_CRIT_TICK.getOrDefault(attacker.getUUID(), -1L) == attacker.serverLevel().getGameTime();
        double chance = Math.min(attributeValue(attacker, ModAttributes.CRIT_CHANCE), ServerConfig.CAP_CRIT_CHANCE.get());
        boolean treeCrit = !vanillaCrit && chance > 0 && attacker.getRandom().nextDouble() < chance;
        if (treeCrit) {
            attacker.level().playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1.2f);
        }
        if (treeCrit || vanillaCrit) {
            Triggers.fire(attacker, "on_crit", victim);
        }
        if (treeCrit && spell) {
            Triggers.fire(attacker, Triggers.ON_SPELL_CRIT, victim);
        }
        if (!treeCrit) {
            return 1f;
        }
        float multiplier = (float) (1.0 + attributeValue(attacker, ModAttributes.CRIT_DAMAGE));
        SkillEvents.post(new SkillEvents.TreeCriticalHit(attacker, victim, multiplier));
        return multiplier;
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getNewDamage() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
            afterDealingDamage(attacker, victim, source, event.getNewDamage());
        }
        if (victim instanceof ServerPlayer hurt && hurt.isAlive()) {
            afterTakingDamage(hurt, source, event.getNewDamage());
        }
    }

    private static void afterDealingDamage(ServerPlayer attacker, LivingEntity victim, DamageSource source, float dealt) {
        if (melee(source, attacker) || source.is(ModTags.SPELL_DAMAGE)) {
            double lifesteal = Math.min(attributeValue(attacker, ModAttributes.LIFESTEAL), ServerConfig.CAP_LIFESTEAL.get());
            boolean ready = lifesteal > 0 && ModAttachments.cooldowns(attacker).tryUse("lifesteal",
                    attacker.serverLevel().getGameTime(), ServerConfig.LIFESTEAL_COOLDOWN_TICKS.get());
            if (ready) {
                attacker.heal((float) (dealt * lifesteal));
            }
        } else if (projectile(source, attacker) && source.getDirectEntity() instanceof AbstractArrow arrow && arrow.isCritArrow()) {
            Triggers.fire(attacker, "on_full_charge_hit", victim);
        }
    }

    private static void afterTakingDamage(ServerPlayer hurt, DamageSource source, float taken) {
        Triggers.fire(hurt, "on_hurt", livingAttacker(source));
        float threshold = hurt.getMaxHealth() * LOW_HEALTH_TRIGGER_FRACTION;
        float after = hurt.getHealth();
        if (after + taken >= threshold && after < threshold) {
            Triggers.fire(hurt, "on_low_health", null);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && event.getEntity() instanceof ServerPlayer player) {
            Triggers.fire(player, "on_block", livingAttacker(event.getDamageSource()));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        boolean killedByOtherPlayer = !event.getEntity().level().isClientSide()
                && event.getSource().getEntity() instanceof ServerPlayer && event.getSource().getEntity() != event.getEntity();
        if (!killedByOtherPlayer) {
            return;
        }
        ServerPlayer killer = (ServerPlayer) event.getSource().getEntity();
        Triggers.fire(killer, "on_kill", null);
        if (projectile(event.getSource(), killer)) {
            Triggers.fire(killer, "on_projectile_kill", null);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            double bonus = attributeValue(player, ModAttributes.HEALING_RECEIVED);
            if (bonus != 0) {
                event.setAmount((float) Math.max(0, event.getAmount() * (1.0 + bonus)));
            }
        }
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getItem().getItem() instanceof PotionItem)) {
            return;
        }
        PotionContents contents = event.getItem().get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        boolean anyBeneficial = false;
        double bonus = attributeValue(player, ModAttributes.POTION_DURATION);
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

    private static boolean isEligibleMiningTool(ItemStack tool, BlockState state) {
        return tool.is(ModTags.itemTag(ServerConfig.MINING_TOOL_TAG.get())) && tool.isCorrectToolForDrops(state);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (isEligibleMiningTool(event.getEntity().getMainHandItem(), event.getState())) {
            double bonus = attributeValue(event.getEntity(), ModAttributes.MINING_SPEED);
            if (bonus != 0) {
                event.setNewSpeed((float) (event.getNewSpeed() * Math.max(MIN_MINING_MULTIPLIER, 1.0 + bonus)));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!isEligibleMiningTool(tool, event.getState())) {
            return;
        }
        Triggers.fire(player, "on_block_break", null);
        if (attributeValue(player, ModAttributes.TOOL_PRESERVATION) != 0 && tool.isDamageableItem()) {
            PENDING_WEAR.put(player.getUUID(), new PendingWear(tool, tool.getDamageValue(), player.getRandom().nextDouble()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PendingWear wear = PENDING_WEAR.remove(player.getUUID());
        if (wear != null && !wear.stack().isEmpty() && wear.stack().getDamageValue() > wear.damageBefore()) {
            settleWear(player, wear);
        }
        if (player.tickCount % CONDITIONAL_REFRESH_TICKS == 0) {
            EffectEngine.updateConditionals(player);
        }
    }

    private static void settleWear(ServerPlayer player, PendingWear wear) {
        double preservation = attributeValue(player, ModAttributes.TOOL_PRESERVATION);
        if (preservation > 0 && wear.roll() < preservation) {
            wear.stack().setDamageValue(wear.damageBefore());
        } else if (preservation < 0 && wear.roll() < -preservation) {
            wear.stack().hurtAndBreak(1, player.serverLevel(), player, item -> { });
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EffectEngine.updateConditionals(player);
        }
    }

    public static void forget(UUID id) {
        VANILLA_CRIT_TICK.remove(id);
        PENDING_WEAR.remove(id);
    }
}
