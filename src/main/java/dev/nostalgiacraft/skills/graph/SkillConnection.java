package dev.nostalgiacraft.skills.graph;

import java.util.List;
import java.util.Optional;

/** An edge declared by endpoint ids. Undirected unless {@code directed}, in which case only a -> b grants access. */
public record SkillConnection(String id, String a, String b, boolean directed, String style, List<Vec2> points,
                              boolean bridge, Optional<String> corridor) {
}
