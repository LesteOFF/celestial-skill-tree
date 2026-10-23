package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.registry.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = CelestialSkillTree.MODID)
public final class PetInheritance {
    private static final String MODIFIER_KIND = "pet";
    private static final int REFRESH_PERIOD_TICKS = 100;
    private static final Set<String> INHERITED = Set.of("minecraft:generic.max_health", "minecraft:generic.attack_damage",
            "minecraft:generic.armor", "minecraft:generic.armor_toughness", "minecraft:generic.movement_speed",
            "minecraft:generic.knockback_resistance");

    private PetInheritance() {
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        boolean refreshesNow = event.getEntity() instanceof LivingEntity pet && !pet.level().isClientSide()
                && pet.tickCount % REFRESH_PERIOD_TICKS == 1 && pet instanceof OwnableEntity
                && pet.getType().is(ModTags.INHERITS_TREE);
        if (refreshesNow && ((OwnableEntity) event.getEntity()).getOwner() instanceof ServerPlayer owner) {
            inherit((LivingEntity) event.getEntity(), owner);
        }
    }

    private static void inherit(LivingEntity pet, ServerPlayer owner) {
        double share = ServerConfig.PET_TREE_SHARE.get();
        float healthBefore = pet.getHealth() / pet.getMaxHealth();
        INHERITED.forEach(attributeId -> clearInherited(instanceOf(pet, attributeId)));
        EffectEngine.build(owner).attributeSums().forEach((key, sum) -> {
            if (INHERITED.contains(key.attribute())) {
                apply(pet, key, EffectEngine.scaled(sum) * share);
            }
        });
        pet.setHealth(Math.min(pet.getMaxHealth(), healthBefore * pet.getMaxHealth()));
    }

    private static void clearInherited(AttributeInstance instance) {
        if (instance == null) {
            return;
        }
        List.copyOf(instance.getModifiers()).stream().map(AttributeModifier::id)
                .filter(id -> EffectEngine.isOwnModifier(id, MODIFIER_KIND)).forEach(instance::removeModifier);
    }

    private static void apply(LivingEntity pet, CompiledBuild.AttributeKey key, double amount) {
        AttributeInstance instance = instanceOf(pet, key.attribute());
        if (instance != null && amount != 0) {
            ResourceLocation id = EffectEngine.modifierId(MODIFIER_KIND, key.id());
            instance.addPermanentModifier(new AttributeModifier(id, amount, EffectEngine.op(key.operation())));
        }
    }

    private static AttributeInstance instanceOf(LivingEntity pet, String attributeId) {
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attributeId)).orElse(null);
        return attribute == null ? null : pet.getAttribute(attribute);
    }
}
