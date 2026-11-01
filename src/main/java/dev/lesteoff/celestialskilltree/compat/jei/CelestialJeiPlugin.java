package dev.lesteoff.celestialskilltree.compat.jei;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import dev.lesteoff.celestialskilltree.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class CelestialJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, "atlas");

    public static String infoKey(String itemPath) {
        return "jei." + CelestialSkillTree.MODID + ".info." + itemPath;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ModItems.ITEMS.getEntries().forEach(item -> registration.addItemStackInfo(new ItemStack(item.get()),
                Component.translatable(infoKey(item.getId().getPath()))));
    }
}
