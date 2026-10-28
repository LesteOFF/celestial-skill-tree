package dev.lesteoff.celestialskilltree.generation;

import dev.lesteoff.celestialskilltree.generation.TreeDraw.ClassDraw;
import dev.lesteoff.celestialskilltree.generation.TreeDraw.NotableChoice;
import dev.lesteoff.celestialskilltree.generation.TreeDraw.RibDraw;
import dev.lesteoff.celestialskilltree.generation.TreeShape.Link;
import dev.lesteoff.celestialskilltree.generation.TreeShape.LinkKind;
import dev.lesteoff.celestialskilltree.graph.ExclusiveGroup;
import dev.lesteoff.celestialskilltree.graph.GraphParser;
import dev.lesteoff.celestialskilltree.graph.Ids;
import dev.lesteoff.celestialskilltree.graph.NodeTraits;
import dev.lesteoff.celestialskilltree.graph.NodeType;
import dev.lesteoff.celestialskilltree.graph.Requirements;
import dev.lesteoff.celestialskilltree.graph.Sector;
import dev.lesteoff.celestialskilltree.graph.SkillConnection;
import dev.lesteoff.celestialskilltree.graph.SkillNodeDefinition;
import dev.lesteoff.celestialskilltree.graph.ValidationReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class TreeAssembler {
    private static final String PORTAL_NAME = "skill.celestialskilltree.portal.name";
    private static final String PORTAL_TEXT = "skill.celestialskilltree.portal.description";
    private static final List<String> PORTAL_ICONS = List.of("celestialskilltree:textures/skill_icons/portal_sun.png",
            "celestialskilltree:textures/skill_icons/portal_moon.png");
    private static final Map<NodeType, String> FRAMES = Map.of(NodeType.ROOT, "class", NodeType.MINOR, "lesser",
            NodeType.NOTABLE, "notable", NodeType.KEYSTONE, "keystone", NodeType.PORTAL, "gateway");
    private static final String EXCLUSIVE_PAIR_NAME = "gui.celestialskilltree.exclusive_pair";
    private static final int ONE_ACTIVE = 1;
    private static final Sector HIDDEN_SECTOR = Sector.rootless("celestialskilltree:hidden", "gui.celestialskilltree.hidden");

    record Assembled(List<SkillNodeDefinition> nodes, List<SkillConnection> connections, List<Sector> sectors,
                     List<ExclusiveGroup> groups) {
    }

    private final String treeId;
    private final ValidationReport report;
    private final List<SkillNodeDefinition> nodes = new ArrayList<>();
    private final Map<String, String> nodeIdBySlot = new HashMap<>();
    private final Map<String, String> classBySlot = new HashMap<>();
    private final Set<String> usedIds = new HashSet<>();
    private final Map<String, List<String>> groupMembers = new LinkedHashMap<>();

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
        List<HiddenSection.Placement> hidden = HiddenSection.place(pools, shape);
        hidden.forEach(placement -> assembler.addHidden(pools.get(placement.owner().index()).definition().id(), placement));
        if (!hidden.isEmpty()) {
            sectors.add(HIDDEN_SECTOR);
        }
        List<SkillConnection> connections = shape.links().stream().map(assembler::connection).toList();
        return new Assembled(List.copyOf(assembler.nodes), connections, List.copyOf(sectors), assembler.groups());
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
        Map<String, String> groupOf = exclusivePairs(pool, draw.keystones());
        for (int position = 0; position < shape.keystones().size(); position++) {
            KeystoneDefinition keystone = pool.keystone(draw.keystones().get(position)).orElseThrow();
            Optional<String> group = Optional.ofNullable(groupOf.get(keystone.id()));
            group.ifPresent(id -> groupMembers.computeIfAbsent(id, unused -> new ArrayList<>()).add(keystone.id()));
            add(shape.keystones().get(position), classId, keystone.id(), NodeType.KEYSTONE, keystone.skill(), keystone.traits(), classId,
                    SkillNodeDefinition.SINGLE_RANK, group);
        }
        for (int number = 0; number < shape.portals().size(); number++) {
            String id = classId + CatalogEntryParser.PORTAL_SUFFIX + (number == 0 ? "" : "_" + (number + 1));
            add(shape.portals().get(number), classId, id, NodeType.PORTAL, portalTemplate(shape.index() + number), NodeTraits.NONE,
                    classId);
        }
    }

    private static Map<String, String> exclusivePairs(ClassPool pool, List<String> drawn) {
        Map<String, String> groupOf = new HashMap<>();
        for (String id : drawn) {
            pool.keystone(id).flatMap(KeystoneDefinition::exclusiveWith).filter(drawn::contains).ifPresent(partner -> {
                String group = id.compareTo(partner) < 0 ? id + "|" + partner : partner + "|" + id;
                groupOf.put(id, group);
                groupOf.put(partner, group);
            });
        }
        return groupOf;
    }

    private List<ExclusiveGroup> groups() {
        return groupMembers.entrySet().stream()
                .map(group -> new ExclusiveGroup(group.getKey(), EXCLUSIVE_PAIR_NAME, ONE_ACTIVE, List.copyOf(group.getValue()))).toList();
    }

    private void addHidden(String classId, HiddenSection.Placement placement) {
        KeystoneDefinition keystone = placement.keystone();
        add(placement.slot(), HIDDEN_SECTOR.id(), keystone.id(), NodeType.KEYSTONE, keystone.skill(), keystone.traits(), classId);
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
                notable.skill(), notable.traits(), theme.id(), notable.maxRank(), Optional.empty());
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
        add(slot, classId, id, type, skill, traits, cluster, SkillNodeDefinition.SINGLE_RANK, Optional.empty());
    }

    private void add(Slot slot, String classId, String id, NodeType type, SkillTemplate skill, NodeTraits traits, String cluster,
                     int maxRank, Optional<String> exclusiveGroup) {
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
                skill.icon(), FRAMES.get(type), type.defaultCost(), maxRank, Requirements.NONE, skill.effects(), tags, cluster,
                exclusiveGroup, traits));
    }

    private SkillConnection connection(Link link) {
        String a = nodeIdBySlot.get(link.a());
        String b = nodeIdBySlot.get(link.b());
        boolean crossesClasses = !classBySlot.get(link.a()).equals(classBySlot.get(link.b()));
        String style = link.kind() == LinkKind.PORTAL ? SkillConnection.PORTAL_STYLE : "path";
        return new SkillConnection(GraphParser.derivedId(a, b), a, b, false, style, List.of(), crossesClasses, Optional.empty());
    }
}
