package dev.nostalgiacraft.skills.graph;

import java.util.Locale;

public enum NodeType {
    ROOT(1),
    MINOR(1),
    NOTABLE(2),
    KEYSTONE(3),
    PORTAL(3);

    private final int defaultCost;

    NodeType(int defaultCost) {
        this.defaultCost = defaultCost;
    }

    public int defaultCost() {
        return defaultCost;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NodeType parse(String raw) {
        for (NodeType nodeType : values()) {
            if (nodeType.serializedName().equals(raw)) {
                return nodeType;
            }
        }
        throw new IllegalArgumentException("unknown node type '" + raw + "'");
    }
}
