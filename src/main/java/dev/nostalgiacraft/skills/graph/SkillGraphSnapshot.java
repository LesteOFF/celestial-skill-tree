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

/**
 * Immutable, validated graph shared by the server's eligibility checks and the client's renderer.
 * The same instance (or its network copy) backs both, so drawn edges and purchasable edges cannot differ.
 */
public final class SkillGraphSnapshot {
    private final String treeId;
    private final Map<String, SkillNodeDefinition> nodes;
    private final List<SkillConnection> connections;
    private final Map<String, List<String>> accessFrom;
    private final Map<String, List<String>> neighbours;
    private final List<Sector> sectors;
    private final List<String> roots;
    private final Map<String, ExclusiveGroup> groups;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;
    private final String fingerprint;

    public SkillGraphSnapshot(String treeId, Collection<SkillNodeDefinition> nodeDefs, List<SkillConnection> connections,
                              List<Sector> sectors, List<ExclusiveGroup> groups) {
        this.treeId = treeId;
        Map<String, SkillNodeDefinition> sorted = new TreeMap<>();
        for (SkillNodeDefinition n : nodeDefs) {
            sorted.put(n.id(), n);
        }
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        this.connections = List.copyOf(connections);
        Map<String, List<String>> access = new HashMap<>();
        Map<String, List<String>> nb = new HashMap<>();
        for (SkillConnection c : connections) {
            access.computeIfAbsent(c.b(), k -> new ArrayList<>()).add(c.a());
            if (!c.directed()) {
                access.computeIfAbsent(c.a(), k -> new ArrayList<>()).add(c.b());
            }
            nb.computeIfAbsent(c.a(), k -> new ArrayList<>()).add(c.b());
            nb.computeIfAbsent(c.b(), k -> new ArrayList<>()).add(c.a());
        }
        access.replaceAll((k, v) -> List.copyOf(v.stream().sorted().toList()));
        nb.replaceAll((k, v) -> List.copyOf(v.stream().sorted().toList()));
        this.accessFrom = Collections.unmodifiableMap(access);
        this.neighbours = Collections.unmodifiableMap(nb);
        this.sectors = List.copyOf(sectors);
        this.roots = nodes.values().stream().filter(SkillNodeDefinition::isRoot).map(SkillNodeDefinition::id).toList();
        Map<String, ExclusiveGroup> g = new LinkedHashMap<>();
        for (ExclusiveGroup eg : groups) {
            g.put(eg.id(), eg);
        }
        this.groups = Collections.unmodifiableMap(g);
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (SkillNodeDefinition n : nodes.values()) {
            x0 = Math.min(x0, n.position().x());
            y0 = Math.min(y0, n.position().y());
            x1 = Math.max(x1, n.position().x());
            y1 = Math.max(y1, n.position().y());
        }
        if (nodes.isEmpty()) {
            x0 = y0 = x1 = y1 = 0;
        }
        this.minX = x0;
        this.minY = y0;
        this.maxX = x1;
        this.maxY = y1;
        this.fingerprint = computeFingerprint();
    }

    private String computeFingerprint() {
        StringBuilder sb = new StringBuilder(treeId);
        for (SkillNodeDefinition n : nodes.values()) {
            sb.append('|').append(n);
        }
        connections.stream().map(SkillConnection::toString).sorted().forEach(s -> sb.append('|').append(s));
        groups.values().forEach(gr -> sb.append('|').append(gr));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", d[i]));
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

    /** Nodes whose allocation grants access to {@code id} (undirected edges count both ways). */
    public List<String> accessFrom(String id) {
        return accessFrom.getOrDefault(id, List.of());
    }

    /** All drawn neighbours regardless of direction. */
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

    public Optional<ExclusiveGroup> groupOf(String nodeId) {
        SkillNodeDefinition n = nodes.get(nodeId);
        if (n == null || n.exclusiveGroup().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(groups.get(n.exclusiveGroup().get()));
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

    /** Content hash; identical definitions always give the same fingerprint (used for reconciliation markers). */
    public String fingerprint() {
        return fingerprint;
    }

    public Set<String> ids() {
        return nodes.keySet();
    }
}
