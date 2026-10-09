package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Item> LIGHT_WEAPONS = item("light_weapons");
    public static final TagKey<Item> HEAVY_WEAPONS = item("heavy_weapons");
    public static final TagKey<Item> BOWS = item("bows");
    public static final TagKey<Item> CROSSBOWS = item("crossbows");
    public static final TagKey<Item> MINING_TOOLS = item("mining_tools");
    public static final TagKey<Item> BONUS_HARVEST = item("bonus_harvest");
    public static final TagKey<DamageType> MAGIC = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "magic"));
    public static final TagKey<DamageType> COUNTS_AS_PROJECTILE = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "counts_as_projectile"));
    public static final TagKey<DamageType> SPELL_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "spell_damage"));

    public static final TagKey<DamageType> MACHINE_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "machine_damage"));
    public static final TagKey<Block> COUNTS_AS_SPAWNER = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "counts_as_spawner"));

    public static final TagKey<EntityType<?>> NEVER_CONSTELLATED = entityType("never_constellated");
    public static final TagKey<EntityType<?>> ALWAYS_DROPS_FRAGMENT = entityType("always_drops_fragment");
    public static final TagKey<EntityType<?>> FIRST_KILL_FRAGMENT = entityType("first_kill_fragment");
    public static final TagKey<EntityType<?>> RITUAL_CONSTELLATED = entityType("ritual_constellated");
    public static final TagKey<EntityType<?>> INHERITS_TREE = entityType("inherits_tree");

    private ModTags() {
    }

    private static TagKey<EntityType<?>> entityType(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, name));
    }

    public static TagKey<EntityType<?>> entityTypeTag(String id) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(id));
    }

    public static TagKey<DamageType> damageTag(String id) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(id));
    }

    public static TagKey<Biome> biomeTag(String id) {
        return TagKey.create(Registries.BIOME, ResourceLocation.parse(id));
    }

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, name));
    }

    public static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }
}
