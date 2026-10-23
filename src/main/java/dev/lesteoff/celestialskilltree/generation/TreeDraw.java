package dev.lesteoff.celestialskilltree.generation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record TreeDraw(List<ClassDraw> classes) {
    public static final TreeDraw EMPTY = new TreeDraw(List.of());
    public static final String TRUNK_SKILL = "trunk";

    public record NotableChoice(String theme, String notable) {
    }

    public record RibDraw(Optional<NotableChoice> opening, Optional<NotableChoice> middle, Optional<NotableChoice> notable,
                          List<String> minors) {
        public int notableCount() {
            return (opening.isPresent() ? 1 : 0) + (middle.isPresent() ? 1 : 0) + (notable.isPresent() ? 1 : 0);
        }
    }

    public record ClassDraw(String classId, List<String> trunk, Map<String, RibDraw> ribs, List<String> keystones) {
    }

    public Optional<ClassDraw> of(String classId) {
        return classes.stream().filter(drawn -> drawn.classId().equals(classId)).findFirst();
    }
}
