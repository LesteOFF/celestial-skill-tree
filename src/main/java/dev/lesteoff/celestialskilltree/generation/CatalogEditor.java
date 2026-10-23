package dev.lesteoff.celestialskilltree.generation;

import com.google.gson.JsonElement;
import dev.lesteoff.celestialskilltree.graph.ModRequirement;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

public final class CatalogEditor {
    private final Map<String, JsonElement> classes;
    private final Map<String, JsonElement> themes;
    private final Map<String, JsonElement> keystones;
    private final Set<String> absentClasses;
    private final Predicate<String> isModLoaded;
    private Optional<JsonElement> rules;

    public CatalogEditor(CatalogSources available, Predicate<String> isModLoaded) {
        this.classes = new TreeMap<>(available.classes());
        this.themes = new TreeMap<>(available.themes());
        this.keystones = new TreeMap<>(available.keystones());
        this.absentClasses = new TreeSet<>(available.classesWithoutTheirMods());
        this.rules = available.rules();
        this.isModLoaded = isModLoaded;
    }

    public void addClass(String id, JsonElement definition) {
        if (ModRequirement.isMet(definition, isModLoaded)) {
            classes.put(id, definition);
            absentClasses.remove(id);
        } else {
            removeClass(id);
        }
    }

    public void removeClass(String id) {
        classes.remove(id);
        absentClasses.add(id);
    }

    public void addTheme(String id, JsonElement definition) {
        put(themes, id, definition);
    }

    public void removeTheme(String id) {
        themes.remove(id);
    }

    public void addKeystone(String id, JsonElement definition) {
        put(keystones, id, definition);
    }

    public void removeKeystone(String id) {
        keystones.remove(id);
    }

    public void rules(JsonElement definition) {
        rules = Optional.of(definition);
    }

    public CatalogSources sources() {
        return new CatalogSources(Map.copyOf(classes), Map.copyOf(themes), Map.copyOf(keystones), rules, Set.copyOf(absentClasses));
    }

    private void put(Map<String, JsonElement> entries, String id, JsonElement definition) {
        if (ModRequirement.isMet(definition, isModLoaded)) {
            entries.put(id, definition);
        } else {
            entries.remove(id);
        }
    }
}
