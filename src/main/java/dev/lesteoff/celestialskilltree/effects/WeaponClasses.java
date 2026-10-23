package dev.lesteoff.celestialskilltree.effects;

import dev.lesteoff.celestialskilltree.config.ServerConfig;
import dev.lesteoff.celestialskilltree.registry.ModTags;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class WeaponClasses {
    private static final List<Function<ItemStack, Optional<WeaponClass>>> CLASSIFIERS = new CopyOnWriteArrayList<>();

    public enum WeaponClass {
        LIGHT, HEAVY
    }

    private WeaponClasses() {
    }

    public static void registerClassifier(Function<ItemStack, Optional<WeaponClass>> classifier) {
        CLASSIFIERS.add(classifier);
    }

    public static boolean isLight(ItemStack stack) {
        return matches(stack, ServerConfig.LIGHT_WEAPON_TAG.get());
    }

    public static boolean isHeavy(ItemStack stack) {
        return matches(stack, ServerConfig.HEAVY_WEAPON_TAG.get());
    }

    public static boolean isWeapon(ItemStack stack) {
        return isLight(stack) || isHeavy(stack);
    }

    public static boolean matches(ItemStack stack, String tagId) {
        if (stack.is(ModTags.itemTag(tagId))) {
            return true;
        }
        return classAskedBy(tagId).filter(asked -> !isTagged(stack) && classify(stack).filter(asked::equals).isPresent()).isPresent();
    }

    private static Optional<WeaponClass> classAskedBy(String tagId) {
        if (tagId.equals(ServerConfig.LIGHT_WEAPON_TAG.get())) {
            return Optional.of(WeaponClass.LIGHT);
        }
        return tagId.equals(ServerConfig.HEAVY_WEAPON_TAG.get()) ? Optional.of(WeaponClass.HEAVY) : Optional.empty();
    }

    private static boolean isTagged(ItemStack stack) {
        return stack.is(ModTags.itemTag(ServerConfig.LIGHT_WEAPON_TAG.get())) || stack.is(ModTags.itemTag(ServerConfig.HEAVY_WEAPON_TAG.get()));
    }

    private static Optional<WeaponClass> classify(ItemStack stack) {
        return CLASSIFIERS.stream().flatMap(classifier -> classifier.apply(stack).stream()).findFirst();
    }
}
