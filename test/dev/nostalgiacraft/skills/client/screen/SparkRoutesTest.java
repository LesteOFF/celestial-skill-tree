package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.TestGraphs;
import dev.nostalgiacraft.skills.graph.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.nostalgiacraft.skills.graph.TestGraphs.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkRoutesTest {
    private static SkillGraphSnapshot line() {
        TestGraphs layout = new TestGraphs().sector("a");
        layout.minor("a/m1", "a");
        layout.minor("a/m2", "a");
        layout.edge("a/hub", "a/m1");
        layout.edge("a/m2", "a/m1");
        return layout.build();
    }

    @Test
    void eachConfirmedNodeGetsARouteFromAnAlreadyLitNeighbour() {
        List<List<Vec2>> routes = SparkRoutes.of(line(), Set.of(id("a/hub")), List.of(id("a/m1"), id("a/m2")));

        assertEquals(2, routes.size());
        assertEquals(List.of(new Vec2(0, 0), new Vec2(40, 0)), routes.get(0));
        assertEquals(List.of(new Vec2(40, 0), new Vec2(80, 0)), routes.get(1), "a reversed edge still runs toward the new node");
    }

    @Test
    void aStartingHubHasNoRoute() {
        assertTrue(SparkRoutes.of(line(), Set.of(), List.of(id("a/hub"))).isEmpty());
    }

    @Test
    void pointsAlongARouteFollowItsLength() {
        List<Vec2> route = List.of(new Vec2(0, 0), new Vec2(20, 0), new Vec2(20, 20));

        assertEquals(new Vec2(10, 0), SparkRoutes.along(route, 0.25));
        assertEquals(new Vec2(20, 10), SparkRoutes.along(route, 0.75));
        assertEquals(new Vec2(20, 20), SparkRoutes.along(route, 1));
        assertEquals(new Vec2(0, 0), SparkRoutes.along(route, -1));
    }

    @Test
    void theOpeningWaveGrowsEachNodeOnlyOnceItArrives() {
        assertEquals(0f, OpeningReveal.grown(-0.2));
        assertEquals(1f, OpeningReveal.grown(5));
        assertTrue(OpeningReveal.grown(0.1) > 0 && OpeningReveal.grown(0.1) < 1);
    }
}
