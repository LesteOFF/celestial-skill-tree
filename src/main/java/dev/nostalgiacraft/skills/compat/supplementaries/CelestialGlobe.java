package dev.nostalgiacraft.skills.compat.supplementaries;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.client.ClientSkillState;
import dev.nostalgiacraft.skills.compat.OptionalEvents;
import dev.nostalgiacraft.skills.compat.Reflection;
import dev.nostalgiacraft.skills.progression.SkyEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

public final class CelestialGlobe {
    public static final String MOD_ID = "supplementaries";
    private static final String MANAGER = "net.mehvahdjukaar.supplementaries.client.GlobeManager";
    private static final String RENDER_DATA = "net.mehvahdjukaar.supplementaries.client.GlobeRenderData";
    private static final String MODEL = "net.mehvahdjukaar.supplementaries.client.GlobeManager$Model";
    private static final List<String> NAMES = List.of("celestial", "celeste", "celestial globe", "globo celeste");
    private static final Map<SkyEvent, ResourceLocation> TEXTURES = Map.of(
            SkyEvent.NONE, texture("celestial"), SkyEvent.ECLIPSE, texture("celestial_eclipse"),
            SkyEvent.STAR_SHOWER, texture("celestial_star_shower"));

    private CelestialGlobe() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "textures/entity/globes/" + name + ".png");
    }

    public static void keepRegistered() {
        if (ModList.get().isLoaded(MOD_ID)) {
            OptionalEvents.guard(MOD_ID, CelestialGlobe::registerIfForgotten);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerIfForgotten() {
        Map<String, Object> globesByName = (Map<String, Object>) Reflection.hiddenStaticField(MANAGER, "NAME_CACHE");
        if (!globesByName.containsKey(NAMES.get(0))) {
            Object globe = celestialGlobe();
            NAMES.forEach(name -> globesByName.put(name, globe));
        }
    }

    private static Object celestialGlobe() {
        Class<?> contract = Reflection.type(RENDER_DATA);
        Object standardModel = Reflection.staticField(MODEL, "GLOBE");
        return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract},
                (proxy, method, arguments) -> answer(proxy, method, arguments, standardModel));
    }

    private static Object answer(Object proxy, Method method, Object[] arguments, Object model) {
        return switch (method.getName()) {
            case "getTexture" -> TEXTURES.get(ClientSkillState.worldRules().skyEvent());
            case "getModel" -> model;
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> "CelestialGlobe";
        };
    }
}
