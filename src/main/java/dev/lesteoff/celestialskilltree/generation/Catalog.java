package dev.lesteoff.celestialskilltree.generation;

import java.util.List;
import java.util.Map;

public record Catalog(Map<String, ClassDefinition> classes, Map<String, ThemeDefinition> themes,
                      Map<String, KeystoneDefinition> keystones, TreeRules rules) {
    public List<ThemeDefinition> themesOf(String classId) {
        return themes.values().stream().filter(theme -> theme.classId().equals(classId)).toList();
    }

    public List<KeystoneDefinition> keystonesOf(String classId) {
        return keystones.values().stream().filter(keystone -> keystone.classId().equals(classId)).toList();
    }
}
