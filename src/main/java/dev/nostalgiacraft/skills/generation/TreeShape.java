package dev.nostalgiacraft.skills.generation;

import java.util.List;

public record TreeShape(List<ClassShape> classes, List<Link> links) {
    public enum LinkKind {
        PATH, BRIDGE, PORTAL
    }

    public record Link(String a, String b, LinkKind kind) {
    }

    public List<Slot> slots() {
        return classes.stream().flatMap(shape -> shape.slots().stream()).toList();
    }
}
