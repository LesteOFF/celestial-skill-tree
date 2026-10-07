package dev.nostalgiacraft.skills.graph;

import java.util.Optional;

public final class GraphParser {
    public static final int SUPPORTED_SCHEMA = 1;
    public static final int MAX_NODES = 4096;
    public static final int MAX_CONNECTIONS = 16384;
    public static final int MAX_EFFECTS = 16;
    public static final int MAX_TAGS = 16;
    public static final int MAX_POINTS = 32;
    public static final int MAX_TEXT = 256;
    public static final int MAX_NODE_ID = 64;
    public static final int MAX_COST = 100;
    public static final double MAX_COORDINATE = 100_000;

    public record Outcome(Optional<SkillGraphSnapshot> snapshot, ValidationReport report) {
        static Outcome failed(ValidationReport report) {
            return new Outcome(Optional.empty(), report);
        }
    }

    private GraphParser() {
    }

    public static String derivedId(String a, String b) {
        String first = a.compareTo(b) <= 0 ? a : b;
        String second = a.compareTo(b) <= 0 ? b : a;
        return Ids.namespace(first) + ":edge/" + Ids.path(first).replace('/', '.') + "--" + Ids.path(second).replace('/', '.');
    }
}
