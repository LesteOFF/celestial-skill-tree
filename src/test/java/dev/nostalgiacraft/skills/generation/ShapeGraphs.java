package dev.nostalgiacraft.skills.generation;

import dev.nostalgiacraft.skills.generation.TreeShape.Link;
import dev.nostalgiacraft.skills.generation.TreeShape.LinkKind;
import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.NodeTraits;
import dev.nostalgiacraft.skills.graph.Requirements;
import dev.nostalgiacraft.skills.graph.Sector;
import dev.nostalgiacraft.skills.graph.SkillConnection;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ShapeGraphs {
    private static final String NAMESPACE = "test:";
    private static final String TREE = "test:main";

    private ShapeGraphs() {
    }

    static SkillGraphSnapshot placeholder(TreeShape shape) {
        List<SkillNodeDefinition> nodes = new ArrayList<>();
        List<Sector> sectors = new ArrayList<>();
        for (ClassShape classShape : shape.classes()) {
            String sector = sector(classShape.hub().key());
            sectors.add(new Sector(sector, NAMESPACE + classShape.hub().key(), sector));
            classShape.slots().forEach(slot -> nodes.add(node(slot, sector)));
        }
        List<SkillConnection> connections = shape.links().stream().map(ShapeGraphs::connection).toList();
        return new SkillGraphSnapshot(TREE, nodes, connections, sectors, List.of());
    }

    private static String sector(String slotKey) {
        return "class_" + slotKey.substring(0, slotKey.indexOf('/'));
    }

    private static SkillNodeDefinition node(Slot slot, String sector) {
        return new SkillNodeDefinition(NAMESPACE + slot.key(), TREE, sector, slot.kind().nodeType(), slot.position(),
                slot.key(), slot.key(), "test:icon", "frame", slot.kind().nodeType().defaultCost(), 1, Requirements.NONE,
                List.of(), Set.of(), sector, Optional.empty(), NodeTraits.NONE);
    }

    private static SkillConnection connection(Link link) {
        String a = NAMESPACE + link.a();
        String b = NAMESPACE + link.b();
        boolean crossesClasses = !sector(link.a()).equals(sector(link.b()));
        String style = link.kind() == LinkKind.PORTAL ? SkillConnection.PORTAL_STYLE : "path";
        return new SkillConnection(GraphParser.derivedId(a, b), a, b, false, style, List.of(), crossesClasses, Optional.empty());
    }
}
