package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.generation.ShapeRequest.ClassBudget;
import dev.lesteoff.celestialskilltree.generation.TreeDraw.ClassDraw;
import dev.lesteoff.celestialskilltree.graph.GraphValidator;
import dev.lesteoff.celestialskilltree.graph.SkillGraphSnapshot;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TreeGenerator {
    private static final long SHARED_SEED = 0;

    public record Result(Optional<SkillGraphSnapshot> graph, TreeDraw draw, ValidationReport report) {
    }

    private TreeGenerator() {
    }

    public static Result generate(String treeId, Catalog catalog, long worldSeed, TreeDraw previous) {
        ValidationReport report = new ValidationReport();
        List<ClassPool> pools = ClassPool.select(catalog);
        if (pools.isEmpty()) {
            report.error("skill_classes", "", "no class has at least " + catalog.rules().minThemesForOwnClass()
                    + " themes (min_themes_for_own_class), so the tree would be empty");
            return new Result(Optional.empty(), previous, report);
        }
        TreeShape shape = shapeThatFitsTheThemes(pools, catalog.rules());
        long seed = catalog.rules().varyBySeed() ? worldSeed : SHARED_SEED;
        List<ClassDraw> drawn = new ArrayList<>();
        Set<String> leaves = shape.leaves();
        for (ClassShape classShape : shape.classes()) {
            ClassPool pool = pools.get(classShape.index());
            drawn.add(TreeDrawer.draw(pool, classShape, catalog.rules().maxCopiesPerSkill(), seed, previous.of(pool.definition().id()),
                    leaves));
            int leftOut = TreeDrawer.notableCount(pool) - TreeDrawer.notableCapacity(classShape);
            if (leftOut > 0) {
                report.warn("skill_classes/" + pool.definition().id(), "", leftOut + " notables do not fit in this class and were left out");
            }
            int keystonesLeftOut = pool.keystones().size() - TreeShapeGenerator.MAX_KEYSTONES;
            if (keystonesLeftOut > 0) {
                report.warn("skill_classes/" + pool.definition().id(), "", keystonesLeftOut + " keystones do not fit in this class and were left out");
            }
        }
        TreeDraw draw = new TreeDraw(List.copyOf(drawn));
        TreeAssembler.Assembled assembled = TreeAssembler.assemble(treeId, pools, shape, draw, report);
        if (report.hasErrors()) {
            return new Result(Optional.empty(), previous, report);
        }
        SkillGraphSnapshot graph = new SkillGraphSnapshot(treeId, assembled.nodes(), assembled.connections(), assembled.sectors(), assembled.groups());
        report.addAll(new GraphValidator().validate(graph));
        return report.hasErrors() ? new Result(Optional.empty(), previous, report) : new Result(Optional.of(graph), draw, report);
    }

    private static TreeShape shapeThatFitsTheThemes(List<ClassPool> pools, TreeRules rules) {
        int[] skills = pools.stream()
                .mapToInt(pool -> pool.definition().skills().orElse(rules.nodesPerClass()) + pool.pairedKeystones()).toArray();
        while (true) {
            TreeShape shape = TreeShapeGenerator.generate(new ShapeRequest(budgets(pools, skills), rules.portals()));
            boolean fits = true;
            for (ClassShape classShape : shape.classes()) {
                ClassPool pool = pools.get(classShape.index());
                int excess = TreeDrawer.minorSlots(pool, classShape) - MinorDeck.capacity(pool, rules.maxCopiesPerSkill());
                if (excess > 0) {
                    skills[classShape.index()] -= excess;
                    fits = false;
                }
            }
            if (fits) {
                return shape;
            }
        }
    }

    private static List<ClassBudget> budgets(List<ClassPool> pools, int[] skills) {
        List<ClassBudget> budgets = new ArrayList<>();
        for (int index = 0; index < pools.size(); index++) {
            budgets.add(new ClassBudget(skills[index], Math.min(TreeShapeGenerator.MAX_KEYSTONES, pools.get(index).keystones().size())));
        }
        return budgets;
    }
}
