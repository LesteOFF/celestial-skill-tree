package dev.lesteoff.celestialskilltree.graph;

import java.util.List;
import java.util.Optional;

public record SkillConnection(String id, String a, String b, boolean directed, String style, List<Vec2> points,
                              boolean bridge, Optional<String> corridor) {
    public static final String PORTAL_STYLE = "portal";

    public boolean isPortal() {
        return PORTAL_STYLE.equals(style);
    }

    public Optional<String> otherEnd(String nodeId) {
        if (a.equals(nodeId)) {
            return Optional.of(b);
        }
        return b.equals(nodeId) ? Optional.of(a) : Optional.empty();
    }
}
