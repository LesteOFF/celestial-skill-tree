package dev.lesteoff.celestialskilltree.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.lesteoff.celestialskilltree.graph.KnownIds;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class CatalogValidatorCli {
    private static final String TREE = "celestialskilltree:main";
    private static final String RULES = "celestialskilltree:main";
    private static final String JSON = ".json";

    private CatalogValidatorCli() {
    }

    public static void main(String[] args) throws IOException {
        Path data = Path.of(args.length > 0 ? args[0] : "src/main/resources/data");
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 0;
        CatalogSources sources = new CatalogSources(read(data, "skill_classes"), read(data, "skill_themes"),
                read(data, "skill_keystones"), Optional.ofNullable(read(data, "skill_rules").get(RULES)));
        CatalogParser.Outcome parsed = new CatalogParser(KnownIds.PERMISSIVE).parse(sources);
        print(parsed.report());
        if (parsed.catalog().isEmpty()) {
            System.out.println("catalog REJECTED");
            System.exit(1);
        }
        Catalog catalog = parsed.catalog().get();
        System.out.printf("catalog: %d classes, %d themes, %d keystones (every optional mod assumed installed)%n",
                catalog.classes().size(), catalog.themes().size(), catalog.keystones().size());
        TreeGenerator.Result result = TreeGenerator.generate(TREE, catalog, seed, TreeDraw.EMPTY);
        print(result.report());
        if (result.graph().isEmpty()) {
            System.out.println("tree REJECTED");
            System.exit(1);
        }
        System.out.printf("seed %d: %d classes, %d skills -> VALID%n", seed, result.graph().get().roots().size(),
                result.graph().get().nodes().size());
        result.draw().classes().forEach(CatalogValidatorCli::printSample);
    }

    private static void printSample(TreeDraw.ClassDraw drawn) {
        System.out.println(drawn.classId() + " trunk: " + String.join(", ", drawn.trunk()));
        drawn.ribs().forEach((key, rib) -> System.out.println(drawn.classId() + " branch " + key + ": "
                + rib.opening().map(choice -> "[" + choice.notable() + "] ").orElse("") + String.join(", ", rib.minors())
                + rib.middle().map(choice -> " (middle [" + choice.notable() + "])").orElse("")
                + rib.notable().map(choice -> " [" + choice.notable() + "]").orElse("")));
    }

    private static void print(ValidationReport report) {
        report.issues().forEach(System.out::println);
    }

    static Map<String, JsonElement> read(Path data, String directory) throws IOException {
        Map<String, JsonElement> files = new TreeMap<>();
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                readInto(files, namespace.getFileName().toString(), namespace.resolve(directory));
            }
        }
        return files;
    }

    private static void readInto(Map<String, JsonElement> files, String namespace, Path base) throws IOException {
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(base)) {
            for (Path path : paths.filter(found -> found.toString().endsWith(JSON)).toList()) {
                String relative = base.relativize(path).toString().replace('\\', '/');
                String id = namespace + ":" + relative.substring(0, relative.length() - JSON.length());
                files.put(id, JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)));
            }
        }
    }
}
