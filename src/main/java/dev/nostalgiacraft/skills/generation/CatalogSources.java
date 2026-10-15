package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nostalgiacraft.skills.graph.ModRequirement;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

public record CatalogSources(Map<String, JsonElement> classes, Map<String, JsonElement> themes,
                             Map<String, JsonElement> keystones, Optional<JsonElement> rules,
                             Set<String> classesWithoutTheirMods) {
    private static final String NOTABLES = "notables";

    public CatalogSources(Map<String, JsonElement> classes, Map<String, JsonElement> themes,
                          Map<String, JsonElement> keystones, Optional<JsonElement> rules) {
        this(classes, themes, keystones, rules, Set.of());
    }

    public CatalogSources availableWith(Predicate<String> isModLoaded) {
        Map<String, JsonElement> availableClasses = available(classes, isModLoaded);
        Set<String> missing = new TreeSet<>(classes.keySet());
        missing.removeAll(availableClasses.keySet());
        missing.addAll(classesWithoutTheirMods);
        return new CatalogSources(availableClasses, availableThemes(isModLoaded), available(keystones, isModLoaded), rules, missing);
    }

    private Map<String, JsonElement> availableThemes(Predicate<String> isModLoaded) {
        Map<String, JsonElement> kept = new TreeMap<>();
        available(themes, isModLoaded).forEach((id, theme) -> kept.put(id, withAvailableNotables(theme, isModLoaded)));
        return kept;
    }

    private static JsonElement withAvailableNotables(JsonElement theme, Predicate<String> isModLoaded) {
        if (!theme.isJsonObject() || !theme.getAsJsonObject().has(NOTABLES)) {
            return theme;
        }
        JsonObject copy = theme.getAsJsonObject().deepCopy();
        JsonArray notables = new JsonArray();
        copy.getAsJsonArray(NOTABLES).forEach(notable -> {
            if (ModRequirement.isMet(notable, isModLoaded)) {
                notables.add(notable);
            }
        });
        copy.add(NOTABLES, notables);
        return copy;
    }

    private static Map<String, JsonElement> available(Map<String, JsonElement> files, Predicate<String> isModLoaded) {
        Map<String, JsonElement> kept = new TreeMap<>();
        files.forEach((id, file) -> {
            if (ModRequirement.isMet(file, isModLoaded)) {
                kept.put(id, file);
            }
        });
        return kept;
    }
}
