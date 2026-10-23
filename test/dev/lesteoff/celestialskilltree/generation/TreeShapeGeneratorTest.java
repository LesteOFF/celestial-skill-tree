package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.graph.GraphValidator;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeShapeGeneratorTest {
    private static final int USUAL_KEYSTONES = 3;
    private static final int[] SKILL_BUDGETS = {12, 20, 40, 60, 80, 100};

    private static TreeShape uniform(int classCount, int skills, boolean portals) {
        return TreeShapeGenerator.generate(ShapeRequest.uniform(classCount, skills, USUAL_KEYSTONES, portals));
    }

    @Test
    void everySizeIsAConnectedTreeWithoutOverlappingSkills() {
        for (int classCount = 1; classCount <= 12; classCount++) {
            for (int skills : SKILL_BUDGETS) {
                for (boolean portals : new boolean[]{true, false}) {
                    ValidationReport report = new GraphValidator().validate(ShapeGraphs.placeholder(uniform(classCount, skills, portals)));
                    String label = classCount + " classes of " + skills + (portals ? " with portals" : "");
                    assertTrue(report.issues().isEmpty(), () -> label + ": " + report.issues());
                }
            }
        }
    }
}
