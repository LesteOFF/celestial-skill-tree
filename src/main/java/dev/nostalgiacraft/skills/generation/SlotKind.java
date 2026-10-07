package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.graph.NodeType;

public enum SlotKind {
    HUB(NodeType.ROOT),
    TRUNK(NodeType.MINOR),
    RIB(NodeType.MINOR),
    RIB_END(NodeType.NOTABLE),
    KEYSTONE(NodeType.KEYSTONE),
    PORTAL(NodeType.PORTAL);

    private final NodeType nodeType;

    SlotKind(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public NodeType nodeType() {
        return nodeType;
    }
}
