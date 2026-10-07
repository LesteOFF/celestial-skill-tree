package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonElement;
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
    public CatalogSources(Map<String, JsonElement> classes, Map<String, JsonElement> themes,
                          Map<String, JsonElement> keystones, Optional<JsonElement> rules) {
        this(classes, themes, keystones, rules, Set.of());
    }

    public CatalogSources availableWith(Predicate<String> isModLoaded) {
        Map<String, JsonElement> availableClasses = available(classes, isModLoaded);
        Set<String> missing = new TreeSet<>(classes.keySet());
        missing.removeAll(availableClasses.keySet());
        missing.addAll(classesWithoutTheirMods);
        return new CatalogSources(availableClasses, available(themes, isModLoaded), available(keystones, isModLoaded), rules, missing);
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
