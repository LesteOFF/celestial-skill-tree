package dev.lesteoff.celestialskilltree.generation;

import java.util.Collections;
import java.util.List;

public record ShapeRequest(List<ClassBudget> classes, boolean portals) {
    public record ClassBudget(int skills, int keystones) {
    }

    public static ShapeRequest uniform(int classCount, int skillsPerClass, int keystonesPerClass, boolean portals) {
        return new ShapeRequest(Collections.nCopies(classCount, new ClassBudget(skillsPerClass, keystonesPerClass)), portals);
    }
}
