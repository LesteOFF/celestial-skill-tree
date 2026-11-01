package dev.lesteoff.celestialskilltree.client;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@JeiPlugin
public final class JeiAtlasPages implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID, "atlas_qa");

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    static boolean ready() {
        return runtime != null;
    }

    static boolean hasPage(ItemStack stack) {
        return !pages(stack).isEmpty();
    }

    static void show(ItemStack stack) {
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(RecipeTypes.INFORMATION), pages(stack),
                List.of(focus(stack)));
    }

    private static List<IJeiIngredientInfoRecipe> pages(ItemStack stack) {
        return runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).limitFocus(List.of(focus(stack))).get().toList();
    }

    private static IFocus<ItemStack> focus(ItemStack stack) {
        return runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
    }
}
