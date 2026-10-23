package dev.lesteoff.celestialskilltree.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public record ClassPool(ClassDefinition definition, List<ThemeDefinition> themes, List<KeystoneDefinition> keystones,
                        List<KeystoneDefinition> hidden) {
    public static List<ClassPool> select(Catalog catalog) {
        Set<String> entering = enteringClasses(catalog);
        Map<String, List<ThemeDefinition>> themes = new TreeMap<>();
        Map<String, List<KeystoneDefinition>> keystones = new TreeMap<>();
        catalog.themes().values().forEach(theme -> home(theme.classId(), entering, catalog)
                .ifPresent(home -> themes.computeIfAbsent(home, id -> new ArrayList<>()).add(theme)));
        catalog.keystones().values().forEach(keystone -> home(keystone.classId(), entering, catalog)
                .ifPresent(home -> keystones.computeIfAbsent(home, id -> new ArrayList<>()).add(keystone)));
        return entering.stream().sorted().map(id -> new ClassPool(catalog.classes().get(id),
                List.copyOf(themes.getOrDefault(id, List.of())), keystonesOf(keystones, id, false),
                keystonesOf(keystones, id, true))).toList();
    }

    private static List<KeystoneDefinition> keystonesOf(Map<String, List<KeystoneDefinition>> byClass, String classId,
                                                       boolean hidden) {
        return byClass.getOrDefault(classId, List.of()).stream()
                .filter(keystone -> keystone.traits().hiddenUntil().isPresent() == hidden).toList();
    }

    public Optional<ThemeDefinition> theme(String id) {
        return themes.stream().filter(theme -> theme.id().equals(id)).findFirst();
    }

    public int pairedKeystones() {
        return (int) keystones.stream().filter(keystone -> keystone.exclusiveWith().flatMap(this::keystone).isPresent()).count();
    }

    public Optional<KeystoneDefinition> keystone(String id) {
        return keystones.stream().filter(keystone -> keystone.id().equals(id)).findFirst();
    }

    private static Set<String> enteringClasses(Catalog catalog) {
        Comparator<ClassDefinition> mostWantedFirst = Comparator.comparingInt(ClassDefinition::priority).reversed()
                .thenComparing(ClassDefinition::id);
        Set<String> entering = new HashSet<>();
        catalog.classes().values().stream()
                .filter(candidate -> catalog.themesOf(candidate.id()).size() >= catalog.rules().minThemesForOwnClass())
                .sorted(mostWantedFirst).limit(catalog.rules().maxClasses())
                .forEach(candidate -> entering.add(candidate.id()));
        return entering;
    }

    private static Optional<String> home(String classId, Set<String> entering, Catalog catalog) {
        Set<String> visited = new HashSet<>();
        Optional<String> current = Optional.of(classId);
        while (current.isPresent() && visited.add(current.get()) && !entering.contains(current.get())) {
            current = Optional.ofNullable(catalog.classes().get(current.get())).flatMap(ClassDefinition::fallbackClass);
        }
        return current.filter(entering::contains);
    }
}
