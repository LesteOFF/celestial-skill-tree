package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class TreeValidatorCli {
    private TreeValidatorCli() {
    }

    public static void main(String[] args) throws IOException {
        Path data = Path.of(args.length > 0 ? args[0] : "src/main/resources/data");
        String tree = args.length > 1 ? args[1] : "nostalgiaskills:main";
        Map<String, JsonElement> trees = new HashMap<>();
        Map<String, JsonElement> nodes = new HashMap<>();
        Map<String, JsonElement> conns = new HashMap<>();
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path ns : namespaces.filter(Files::isDirectory).toList()) {
                read(ns, "skill_trees", trees);
                read(ns, "skill_nodes", nodes);
                read(ns, "skill_connections", conns);
            }
        }
        GraphParser.Outcome outcome = new GraphParser(KnownIds.PERMISSIVE).parse(tree, new GraphParser.Sources(trees, nodes, conns));
        outcome.report().issues().forEach(System.out::println);
        System.out.printf("%s: %d node files, %d error(s), %d warning(s) -> %s%n", tree, nodes.size(),
                outcome.report().errors().size(), outcome.report().warnings().size(), outcome.snapshot().isPresent() ? "VALID" : "REJECTED");
        outcome.snapshot().ifPresent(g -> System.out.printf("fingerprint %s, bounds x %.1f..%.1f y %.1f..%.1f%n",
                g.fingerprint(), g.minX(), g.maxX(), g.minY(), g.maxY()));
        if (outcome.snapshot().isEmpty()) {
            System.exit(1);
        }
    }

    private static void read(Path ns, String dir, Map<String, JsonElement> out) throws IOException {
        Path base = ns.resolve(dir);
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> s = Files.walk(base)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = base.relativize(p).toString().replace('\\', '/');
                String id = ns.getFileName() + ":" + rel.substring(0, rel.length() - 5);
                try {
                    out.put(id, JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)));
                } catch (RuntimeException e) {
                    System.out.println("ERROR " + dir + "/" + id + ": unreadable JSON: " + e.getMessage());
                }
            }
        }
    }
}
