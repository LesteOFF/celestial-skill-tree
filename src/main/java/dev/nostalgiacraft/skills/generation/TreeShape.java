package dev.nostalgiacraft.skills.generation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record TreeShape(List<ClassShape> classes, List<Link> links) {
    public enum LinkKind {
        PATH, BRIDGE, PORTAL
    }

    public record Link(String a, String b, LinkKind kind) {
    }

    public Set<String> leaves() {
        return links.stream().flatMap(link -> Stream.of(link.a(), link.b()))
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()))
                .entrySet().stream().filter(degree -> degree.getValue() == 1).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public List<Slot> slots() {
        return classes.stream().flatMap(shape -> shape.slots().stream()).toList();
    }
}
