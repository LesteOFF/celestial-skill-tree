package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonElement;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.JsonFields;
import dev.nostalgiacraft.skills.graph.KnownIds;
import dev.nostalgiacraft.skills.graph.ValidationReport;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

public final class CatalogParser {
    private static final String CLASSES = "skill_classes/";
    private static final String THEMES = "skill_themes/";
    private static final String KEYSTONES = "skill_keystones/";

    public record Outcome(Optional<Catalog> catalog, ValidationReport report) {
    }

    private interface EntryReader<T> {
        T read(String id, JsonFields file);
    }

    private final CatalogEntryParser entries;

    public CatalogParser(KnownIds known) {
        this.entries = new CatalogEntryParser(known);
    }

    public Outcome parse(CatalogSources sources) {
        ValidationReport report = new ValidationReport();
        Map<String, ClassDefinition> classes = readAll(sources.classes(), CLASSES, report, entries::classDefinition);
        Set<String> unusableClasses = unusableClasses(sources, classes);
        Map<String, ThemeDefinition> themes = readAll(ofUsableClasses(sources.themes(), unusableClasses), THEMES, report,
                entries::theme);
        Map<String, KeystoneDefinition> keystones = readAll(ofUsableClasses(sources.keystones(), unusableClasses), KEYSTONES,
                report, (id, file) -> entries.keystone(id, file, classes));
        TreeRules rules = sources.rules().map(file -> RulesParser.parse(file, report)).orElse(TreeRules.DEFAULT);
        requireClasses(classes, report);
        requireKnownClass(themes, THEMES, ThemeDefinition::classId, classes, report);
        requireKnownClass(keystones, KEYSTONES, KeystoneDefinition::classId, classes, report);
        Map<String, ClassDefinition> resolved = withUsableFallbacks(classes, unusableClasses, report);
        if (report.hasErrors()) {
            return new Outcome(Optional.empty(), report);
        }
        return new Outcome(Optional.of(new Catalog(resolved, themes, keystones, rules)), report);
    }

    private static <T> Map<String, T> readAll(Map<String, JsonElement> files, String directory, ValidationReport report,
                                              EntryReader<T> reader) {
        Map<String, T> entries = new TreeMap<>();
        for (Map.Entry<String, JsonElement> file : new TreeMap<>(files).entrySet()) {
            String source = directory + file.getKey();
            int errorsBefore = report.errors().size();
            read(file.getKey(), file.getValue(), source, report, reader)
                    .filter(entry -> report.errors().size() == errorsBefore)
                    .ifPresent(entry -> entries.put(file.getKey(), entry));
        }
        return Collections.unmodifiableMap(entries);
    }

    private static <T> Optional<T> read(String id, JsonElement file, String source, ValidationReport report, EntryReader<T> reader) {
        if (!Ids.isValid(id)) {
            report.error(source, "id", "invalid id derived from the file path: '" + id + "'");
            return Optional.empty();
        }
        if (!file.isJsonObject()) {
            report.error(source, "", "file must be a JSON object");
            return Optional.empty();
        }
        try {
            JsonFields fields = new JsonFields(file.getAsJsonObject(), source, "", report);
            fields.checkSchemaVersion();
            return Optional.of(reader.read(id, fields));
        } catch (RuntimeException unreadable) {
            report.error(source, "", "unreadable: " + unreadable.getMessage());
            return Optional.empty();
        }
    }

    private static Set<String> unusableClasses(CatalogSources sources, Map<String, ClassDefinition> parsed) {
        Set<String> unusable = new TreeSet<>(sources.classesWithoutTheirMods());
        sources.classes().keySet().stream().filter(id -> !parsed.containsKey(id)).forEach(unusable::add);
        return unusable;
    }

    private static Map<String, JsonElement> ofUsableClasses(Map<String, JsonElement> files, Set<String> unusableClasses) {
        Map<String, JsonElement> kept = new TreeMap<>();
        files.forEach((id, file) -> {
            if (!unusableClasses.contains(declaredClass(file))) {
                kept.put(id, file);
            }
        });
        return kept;
    }

    private static String declaredClass(JsonElement file) {
        boolean declared = file.isJsonObject() && file.getAsJsonObject().has("class")
                && file.getAsJsonObject().get("class").isJsonPrimitive();
        return declared ? file.getAsJsonObject().get("class").getAsString() : "";
    }

    private static void requireClasses(Map<String, ClassDefinition> classes, ValidationReport report) {
        if (classes.isEmpty()) {
            report.error("skill_classes", "", "the catalog has no usable class");
        }
    }

    private static <T> void requireKnownClass(Map<String, T> entries, String directory, Function<T, String> classOf,
                                              Map<String, ClassDefinition> classes, ValidationReport report) {
        entries.forEach((id, entry) -> {
            if (!classes.containsKey(classOf.apply(entry))) {
                report.error(directory + id, "class", "unknown class '" + classOf.apply(entry) + "'");
            }
        });
    }

    private static Map<String, ClassDefinition> withUsableFallbacks(Map<String, ClassDefinition> classes,
                                                                    Set<String> unusableClasses, ValidationReport report) {
        Map<String, ClassDefinition> resolved = new TreeMap<>();
        classes.forEach((id, definition) -> {
            Optional<String> fallback = definition.fallbackClass().filter(target -> !unusableClasses.contains(target));
            fallback.filter(target -> !classes.containsKey(target))
                    .ifPresent(target -> report.error(CLASSES + id, "fallback_class", "unknown class '" + target + "'"));
            resolved.put(id, new ClassDefinition(id, definition.priority(), fallback, definition.skills(), definition.hub(),
                    definition.trunk()));
        });
        resolved.keySet().stream().filter(id -> fallsBackToItself(id, resolved))
                .forEach(id -> report.error(CLASSES + id, "fallback_class", "the chain of fallback classes loops back to this class"));
        return Collections.unmodifiableMap(resolved);
    }

    private static boolean fallsBackToItself(String start, Map<String, ClassDefinition> classes) {
        Set<String> visited = new HashSet<>();
        Optional<String> current = classes.get(start).fallbackClass();
        while (current.isPresent() && classes.containsKey(current.get()) && visited.add(current.get())) {
            if (current.get().equals(start)) {
                return true;
            }
            current = classes.get(current.get()).fallbackClass();
        }
        return false;
    }
}
