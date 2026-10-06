package dev.nostalgiacraft.skills.graph;

import java.util.Locale;

/** Playable node categories. Default costs follow the design document (section 7.2). */
public enum NodeType {
    ROOT(1),
    MINOR(1),
    NOTABLE(2),
    KEYSTONE(3);

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
        for (NodeType t : values()) {
            if (t.serializedName().equals(raw)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown node type '" + raw + "'");
    }
}
