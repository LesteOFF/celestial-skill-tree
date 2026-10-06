package dev.nostalgiacraft.skills.graph;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class SkillGraphSnapshot {
    private static final int FINGERPRINT_BYTES = 12;

    private final String treeId;
    private final Map<String, SkillNodeDefinition> nodes;
    private final List<SkillConnection> connections;
    private final Map<String, List<String>> accessFrom;
    private final Map<String, List<String>> neighbours;
    private final List<Sector> sectors;
    private final List<String> roots;
    private final Map<String, ExclusiveGroup> groups;
    private final Map<String, String> portalTwins;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;
    private final String fingerprint;

    public SkillGraphSnapshot(String treeId, Collection<SkillNodeDefinition> nodeDefs, List<SkillConnection> connections,
                              List<Sector> sectors, List<ExclusiveGroup> groups) {
        this.treeId = treeId;
        Map<String, SkillNodeDefinition> sorted = new TreeMap<>();
        for (SkillNodeDefinition node : nodeDefs) {
            sorted.put(node.id(), node);
        }
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        this.connections = List.copyOf(connections);
        this.accessFrom = adjacency(connections, false);
        this.neighbours = adjacency(connections, true);
        this.sectors = List.copyOf(sectors);
        this.roots = nodes.values().stream().filter(SkillNodeDefinition::isRoot).map(SkillNodeDefinition::id).toList();
        Map<String, ExclusiveGroup> groupsById = new LinkedHashMap<>();
        for (ExclusiveGroup group : groups) {
            groupsById.put(group.id(), group);
        }
        this.groups = Collections.unmodifiableMap(groupsById);
        this.portalTwins = portalTwins(connections);
        this.minX = nodes.values().stream().mapToDouble(node -> node.position().x()).min().orElse(0);
        this.minY = nodes.values().stream().mapToDouble(node -> node.position().y()).min().orElse(0);
        this.maxX = nodes.values().stream().mapToDouble(node -> node.position().x()).max().orElse(0);
        this.maxY = nodes.values().stream().mapToDouble(node -> node.position().y()).max().orElse(0);
        this.fingerprint = computeFingerprint();
    }

    private static Map<String, List<String>> adjacency(List<SkillConnection> connections, boolean ignoreDirection) {
        Map<String, List<String>> adjacent = new HashMap<>();
        for (SkillConnection connection : connections) {
            adjacent.computeIfAbsent(connection.b(), id -> new ArrayList<>()).add(connection.a());
            if (ignoreDirection || !connection.directed()) {
                adjacent.computeIfAbsent(connection.a(), id -> new ArrayList<>()).add(connection.b());
            }
        }
        adjacent.replaceAll((id, others) -> others.stream().sorted().toList());
        return Collections.unmodifiableMap(adjacent);
    }

    private static Map<String, String> portalTwins(List<SkillConnection> connections) {
        Map<String, String> twins = new HashMap<>();
        for (SkillConnection connection : connections) {
            if (connection.isPortal()) {
                twins.putIfAbsent(connection.a(), connection.b());
                twins.putIfAbsent(connection.b(), connection.a());
            }
        }
        return Collections.unmodifiableMap(twins);
    }

    private String computeFingerprint() {
        StringBuilder content = new StringBuilder(treeId);
        for (SkillNodeDefinition node : nodes.values()) {
            content.append('|').append(node);
        }
        connections.stream().map(SkillConnection::toString).sorted().forEach(text -> content.append('|').append(text));
        groups.values().forEach(group -> content.append('|').append(group));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < FINGERPRINT_BYTES; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String treeId() {
        return treeId;
    }

    public Map<String, SkillNodeDefinition> nodes() {
        return nodes;
    }

    public Optional<SkillNodeDefinition> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public boolean contains(String id) {
        return nodes.containsKey(id);
    }

    public List<SkillConnection> connections() {
        return connections;
    }

    public List<String> accessFrom(String id) {
        return accessFrom.getOrDefault(id, List.of());
    }

    public List<String> neighbours(String id) {
        return neighbours.getOrDefault(id, List.of());
    }

    public List<Sector> sectors() {
        return sectors;
    }

    public List<String> roots() {
        return roots;
    }

    public Map<String, ExclusiveGroup> exclusiveGroups() {
        return groups;
    }

    public Optional<String> portalDestination(String nodeId) {
        return Optional.ofNullable(portalTwins.get(nodeId));
    }

    public Optional<ExclusiveGroup> groupOf(String nodeId) {
        SkillNodeDefinition node = nodes.get(nodeId);
        if (node == null || node.exclusiveGroup().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(groups.get(node.exclusiveGroup().get()));
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double maxX() {
        return maxX;
    }

    public double maxY() {
        return maxY;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public Set<String> ids() {
        return nodes.keySet();
    }
}
