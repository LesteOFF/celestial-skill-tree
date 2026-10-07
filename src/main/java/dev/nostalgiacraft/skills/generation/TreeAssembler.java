package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.generation.TreeDraw.ClassDraw;
import dev.nostalgiacraft.skills.generation.TreeDraw.NotableChoice;
import dev.nostalgiacraft.skills.generation.TreeDraw.RibDraw;
import dev.nostalgiacraft.skills.generation.TreeShape.Link;
import dev.nostalgiacraft.skills.generation.TreeShape.LinkKind;
import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.Ids;
import dev.nostalgiacraft.skills.graph.NodeTraits;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.Requirements;
import dev.nostalgiacraft.skills.graph.Sector;
import dev.nostalgiacraft.skills.graph.SkillConnection;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.graph.ValidationReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class TreeAssembler {
    private static final String PORTAL_NAME = "skill.nostalgiaskills.portal.name";
    private static final String PORTAL_TEXT = "skill.nostalgiaskills.portal.description";
    private static final List<String> PORTAL_ICONS = List.of("nostalgiaskills:textures/skill_icons/portal_sun.png",
            "nostalgiaskills:textures/skill_icons/portal_moon.png");
    private static final Map<NodeType, String> FRAMES = Map.of(NodeType.ROOT, "stone_medallion", NodeType.MINOR, "bronze_circle",
            NodeType.NOTABLE, "violet_facet", NodeType.KEYSTONE, "gold_ornate", NodeType.PORTAL, "portal_gate");
    private static final int MAX_RANK = 1;

    record Assembled(List<SkillNodeDefinition> nodes, List<SkillConnection> connections, List<Sector> sectors) {
    }

    private final String treeId;
    private final ValidationReport report;
    private final List<SkillNodeDefinition> nodes = new ArrayList<>();
    private final Map<String, String> nodeIdBySlot = new HashMap<>();
    private final Map<String, String> classBySlot = new HashMap<>();
    private final Set<String> usedIds = new HashSet<>();

    private TreeAssembler(String treeId, ValidationReport report) {
        this.treeId = treeId;
        this.report = report;
    }

    static Assembled assemble(String treeId, List<ClassPool> pools, TreeShape shape, TreeDraw draw, ValidationReport report) {
        TreeAssembler assembler = new TreeAssembler(treeId, report);
        List<Sector> sectors = new ArrayList<>();
        for (ClassShape classShape : shape.classes()) {
            ClassPool pool = pools.get(classShape.index());
            assembler.addClass(pool, classShape, draw.of(pool.definition().id()).orElseThrow());
            String classId = pool.definition().id();
            sectors.add(new Sector(classId, classId + CatalogEntryParser.HUB_SUFFIX, pool.definition().name()));
        }
        List<SkillConnection> connections = shape.links().stream().map(assembler::connection).toList();
        return new Assembled(List.copyOf(assembler.nodes), connections, List.copyOf(sectors));
    }

    private void addClass(ClassPool pool, ClassShape shape, ClassDraw draw) {
        ClassDefinition definition = pool.definition();
        String classId = definition.id();
        add(shape.hub(), classId, classId + CatalogEntryParser.HUB_SUFFIX, NodeType.ROOT, definition.hub(), NodeTraits.NONE, classId);
        Map<String, Integer> copies = new HashMap<>();
        for (int position = 0; position < shape.trunk().size(); position++) {
            addMinor(pool, shape.trunk().get(position), draw.trunk().get(position), copies);
        }
        for (Rib rib : shape.ribs()) {
            addRib(pool, rib, draw.ribs().get(TreeDrawer.ribKey(rib)), copies);
        }
        for (int position = 0; position < shape.keystones().size(); position++) {
            KeystoneDefinition keystone = pool.keystone(draw.keystones().get(position)).orElseThrow();
            add(shape.keystones().get(position), classId, keystone.id(), NodeType.KEYSTONE, keystone.skill(), keystone.traits(), classId);
        }
        for (int number = 0; number < shape.portals().size(); number++) {
            String id = classId + CatalogEntryParser.PORTAL_SUFFIX + (number == 0 ? "" : "_" + (number + 1));
            add(shape.portals().get(number), classId, id, NodeType.PORTAL, portalTemplate(shape.index() + number), NodeTraits.NONE,
                    classId);
        }
    }

    private void addRib(ClassPool pool, Rib rib, RibDraw drawn, Map<String, Integer> copies) {
        Map<Slot, NotableChoice> notables = new HashMap<>();
        drawn.opening().ifPresent(choice -> notables.put(rib.slots().get(0), choice));
        drawn.middle().ifPresent(choice -> notables.put(rib.slots().get(rib.middleIndex()), choice));
        drawn.notable().ifPresent(choice -> notables.put(rib.tip(), choice));
        int nextMinor = 0;
        for (Slot slot : rib.slots()) {
            if (notables.containsKey(slot)) {
                addNotable(pool, slot, notables.get(slot));
            } else {
                addMinor(pool, slot, drawn.minors().get(nextMinor++), copies);
            }
        }
    }

    private void addNotable(ClassPool pool, Slot slot, NotableChoice choice) {
        ThemeDefinition theme = pool.theme(choice.theme()).orElseThrow();
        NotableDefinition notable = theme.notables().stream().filter(candidate -> candidate.id().equals(choice.notable()))
                .findFirst().orElseThrow();
        add(slot, pool.definition().id(), CatalogEntryParser.notableNodeId(theme.id(), notable.id()), NodeType.NOTABLE,
                notable.skill(), NodeTraits.NONE, theme.id());
    }

    private void addMinor(ClassPool pool, Slot slot, String skill, Map<String, Integer> copies) {
        String classId = pool.definition().id();
        int rank = copies.merge(skill, 1, Integer::sum);
        if (skill.equals(TreeDraw.TRUNK_SKILL)) {
            add(slot, classId, classId + CatalogEntryParser.TRUNK_INFIX + rank, NodeType.MINOR, pool.definition().trunk(),
                    NodeTraits.NONE, classId);
            return;
        }
        ThemeDefinition theme = pool.theme(skill).orElseThrow();
        add(slot, classId, theme.id() + CatalogEntryParser.MINOR_INFIX + rank, NodeType.MINOR, theme.minor(), NodeTraits.NONE,
                theme.id());
    }

    private static SkillTemplate portalTemplate(int classIndex) {
        return new SkillTemplate(PORTAL_NAME, PORTAL_ICONS.get(classIndex % PORTAL_ICONS.size()), List.of(), Optional.of(PORTAL_TEXT));
    }

    private void add(Slot slot, String classId, String id, NodeType type, SkillTemplate skill, NodeTraits traits, String cluster) {
        if (!usedIds.add(id)) {
            report.error("skill_nodes/" + id, "id", "two catalog entries produce this skill id");
        }
        if (id.length() > GraphParser.MAX_NODE_ID) {
            report.error("skill_nodes/" + id, "id", "skill id longer than " + GraphParser.MAX_NODE_ID + " characters");
        }
        nodeIdBySlot.put(slot.key(), id);
        classBySlot.put(slot.key(), classId);
        Set<String> tags = new TreeSet<>(List.of(type.serializedName(), Ids.path(classId), Ids.path(cluster)));
        nodes.add(new SkillNodeDefinition(id, treeId, classId, type, slot.position(), skill.name(), skill.text().orElse(""),
                skill.icon(), FRAMES.get(type), type.defaultCost(), MAX_RANK, Requirements.NONE, skill.effects(), tags, cluster,
                Optional.empty(), traits));
    }

    private SkillConnection connection(Link link) {
        String a = nodeIdBySlot.get(link.a());
        String b = nodeIdBySlot.get(link.b());
        boolean crossesClasses = !classBySlot.get(link.a()).equals(classBySlot.get(link.b()));
        String style = link.kind() == LinkKind.PORTAL ? SkillConnection.PORTAL_STYLE : "path";
        return new SkillConnection(GraphParser.derivedId(a, b), a, b, false, style, List.of(), crossesClasses, Optional.empty());
    }
}
