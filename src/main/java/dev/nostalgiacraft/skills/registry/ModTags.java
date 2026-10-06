package dev.nostalgiacraft.skills.registry;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

/** Classification tags (configurable through datapacks; items are never classified by display name). */
public final class ModTags {
    public static final TagKey<Item> LIGHT_WEAPONS = item("light_weapons");
    public static final TagKey<Item> HEAVY_WEAPONS = item("heavy_weapons");
    public static final TagKey<Item> BOWS = item("bows");
    public static final TagKey<Item> CROSSBOWS = item("crossbows");
    public static final TagKey<Item> MINING_TOOLS = item("mining_tools");
    public static final TagKey<DamageType> MAGIC = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "magic"));

    private ModTags() {
    }

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, name));
    }

    public static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }
}
