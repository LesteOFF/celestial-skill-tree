package dev.lesteoff.celestialskilltree.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StatsScrollbarTest {
    @Test
    void theThumbFollowsTheScrollBothWays() {
        StatsSheetRenderer.Scrollbar bar = new StatsSheetRenderer.Scrollbar(10, 100, 400);

        assertEquals(300, bar.maxScroll());
        assertEquals(25, bar.thumbHeight());
        assertEquals(10, bar.thumbTop(0));
        assertEquals(85, bar.thumbTop(300));
        assertEquals(150, bar.scrollFor(10 + (100 - 25) / 2.0));
        assertEquals(0, bar.scrollFor(-50));
        assertEquals(300, bar.scrollFor(500));
    }

    @Test
    void aSheetThatFitsHasNoScrollbar() {
        assertFalse(new StatsSheetRenderer.Scrollbar(10, 100, 100).shown());
    }
}
