package dev.nostalgiacraft.skills.effects;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.config.ServerConfig;
import dev.nostalgiacraft.skills.registry.ModTags;
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

@EventBusSubscriber(modid = NostalgiaSkills.MODID)
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
        EffectEngine.build(owner).attributeSums().forEach((sumKey, sum) -> {
            String[] attributeAndOperation = sumKey.split("\\|");
            if (INHERITED.contains(attributeAndOperation[0])) {
                apply(pet, sumKey, attributeAndOperation, EffectEngine.scaled(sum) * share);
            }
        });
        pet.setHealth(Math.min(pet.getMaxHealth(), healthBefore * pet.getMaxHealth()));
    }

    private static void clearInherited(AttributeInstance instance) {
        if (instance == null) {
            return;
        }
        List.copyOf(instance.getModifiers()).stream().map(AttributeModifier::id)
                .filter(PetInheritance::isInherited).forEach(instance::removeModifier);
    }

    private static boolean isInherited(ResourceLocation modifierId) {
        return modifierId.getNamespace().equals(NostalgiaSkills.MODID) && modifierId.getPath().startsWith(MODIFIER_KIND + "/");
    }

    private static void apply(LivingEntity pet, String sumKey, String[] attributeAndOperation, double amount) {
        AttributeInstance instance = instanceOf(pet, attributeAndOperation[0]);
        if (instance != null && amount != 0) {
            ResourceLocation id = EffectEngine.modifierId(MODIFIER_KIND, sumKey);
            instance.addPermanentModifier(new AttributeModifier(id, amount, EffectEngine.op(attributeAndOperation[1])));
        }
    }

    private static AttributeInstance instanceOf(LivingEntity pet, String attributeId) {
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attributeId)).orElse(null);
        return attribute == null ? null : pet.getAttribute(attribute);
    }
}
