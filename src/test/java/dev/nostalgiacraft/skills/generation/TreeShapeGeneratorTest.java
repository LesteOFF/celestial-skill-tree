package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.generation.ShapeRequest.ClassBudget;
import dev.nostalgiacraft.skills.generation.TreeShape.LinkKind;
import dev.nostalgiacraft.skills.graph.GraphValidator;
import dev.nostalgiacraft.skills.graph.ValidationReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeShapeGeneratorTest {
    private static final int MAX_UNUSED_SKILLS = 6;
    private static final int USUAL_KEYSTONES = 3;
    private static final int[] SKILL_BUDGETS = {12, 20, 40, 60, 80, 100};

    private static TreeShape uniform(int classCount, int skills, boolean portals) {
        return TreeShapeGenerator.generate(ShapeRequest.uniform(classCount, skills, USUAL_KEYSTONES, portals));
    }

    private static long links(TreeShape shape, LinkKind kind) {
        return shape.links().stream().filter(link -> link.kind() == kind).count();
    }

    private static List<Integer> ribLengths(ClassShape shape) {
        return shape.ribs().stream().map(rib -> rib.slots().size()).toList();
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

    @Test
    void neverExceedsTheBudgetAndLeavesFewSkillsUnused() {
        for (int classCount = 1; classCount <= 12; classCount++) {
            for (int skills : SKILL_BUDGETS) {
                for (ClassShape shape : uniform(classCount, skills, true).classes()) {
                    assertTrue(shape.skillCount() <= skills, () -> shape.skillCount() + " > " + skills);
                    assertTrue(shape.skillCount() >= skills - MAX_UNUSED_SKILLS, () -> shape.skillCount() + " of " + skills);
                }
            }
        }
    }

    @Test
    void sixClassesOfAHundredSkillsFormSixRingsOfGrowingRibs() {
        ClassShape shape = uniform(6, 100, true).classes().get(0);
        assertEquals(99, shape.skillCount());
        assertEquals(19, shape.trunk().size());
        assertEquals(3, shape.keystones().size());
        assertEquals(List.of(4, 4, 5, 5, 7, 7, 9, 9, 11, 11, 2, 2), ribLengths(shape));
        assertTrue(shape.ribs().stream().allMatch(rib -> rib.tip().kind() == SlotKind.RIB_END));
    }

    @Test
    void classesWithTheSameBudgetShareOneSilhouette() {
        List<ClassShape> classes = uniform(8, 70, true).classes();
        for (ClassShape shape : classes) {
            assertEquals(ribLengths(classes.get(0)), ribLengths(shape));
            assertEquals(classes.get(0).trunk().size(), shape.trunk().size());
        }
    }

    @Test
    void neighboursAreBridgedOnlyOnTheFirstRing() {
        TreeShape shape = uniform(6, 100, false);
        assertEquals(6, links(shape, LinkKind.BRIDGE));
        assertTrue(shape.links().stream().filter(link -> link.kind() == LinkKind.BRIDGE)
                .allMatch(link -> link.a().contains("/rib_1b_") && link.b().contains("/rib_1a_")));
    }

    @Test
    void everyNumberOfClassesHasExactlyOneBridgePerPairOfNeighbours() {
        for (int classCount = 3; classCount <= 16; classCount++) {
            for (int skills : SKILL_BUDGETS) {
                TreeShape shape = uniform(classCount, skills, true);
                assertEquals(classCount, links(shape, LinkKind.BRIDGE), classCount + " classes of " + skills);
                assertTrue(shape.classes().stream().noneMatch(found -> found.portals().isEmpty()), classCount + " classes");
            }
        }
    }

    @Test
    void classesTooSmallToReachTheBoundaryAreStillBridged() {
        TreeShape shape = uniform(6, 12, false);
        assertTrue(shape.classes().get(0).ribs().stream().noneMatch(Rib::reachesBoundary));
        assertEquals(6, links(shape, LinkKind.BRIDGE));
    }

    @Test
    void oppositeClassesShareAPortalAndAnOddClassSharesASecondOneWithItsOpposite() {
        TreeShape even = uniform(6, 60, true);
        assertEquals(3, links(even, LinkKind.PORTAL));
        assertTrue(even.classes().stream().allMatch(shape -> shape.portals().size() == 1));
        TreeShape odd = uniform(7, 60, true);
        assertEquals(4, links(odd, LinkKind.PORTAL));
        assertEquals(List.of(1, 1, 1, 2, 1, 1, 1), odd.classes().stream().map(shape -> shape.portals().size()).toList());
        assertTrue(odd.links().contains(new TreeShape.Link("6/portal", "3/portal_2", LinkKind.PORTAL)));
        assertTrue(uniform(1, 60, true).classes().get(0).portals().isEmpty());
        assertEquals(0, links(uniform(6, 60, false), LinkKind.PORTAL));
    }

    @Test
    void aClassMayCarryUpToAHundredKeystonesWithoutOverlaps() {
        for (int keystones = 0; keystones <= TreeShapeGenerator.MAX_KEYSTONES + 2; keystones++) {
            TreeShape shape = TreeShapeGenerator.generate(ShapeRequest.uniform(7, 100, keystones, true));
            ValidationReport report = new GraphValidator().validate(ShapeGraphs.placeholder(shape));
            int expected = Math.min(keystones, 100 - 2);
            assertTrue(report.issues().isEmpty(), () -> expected + " keystones: " + report.issues());
            assertEquals(expected, shape.classes().get(0).keystones().size());
            assertTrue(shape.classes().get(0).skillCount() <= 100);
        }
    }

    @Test
    void classesMayHaveDifferentBudgets() {
        ShapeRequest request = new ShapeRequest(List.of(new ClassBudget(100, 3), new ClassBudget(30, 1),
                new ClassBudget(60, 0), new ClassBudget(100, 2), new ClassBudget(45, 3)), true);
        TreeShape shape = TreeShapeGenerator.generate(request);
        ValidationReport report = new GraphValidator().validate(ShapeGraphs.placeholder(shape));
        assertTrue(report.issues().isEmpty(), () -> report.issues().toString());
        assertEquals(List.of(3, 1, 0, 2, 3), shape.classes().stream().map(found -> found.keystones().size()).toList());
    }

    @Test
    void theSameRequestAlwaysGivesTheSameShape() {
        assertEquals(uniform(7, 85, true), uniform(7, 85, true));
    }
}
