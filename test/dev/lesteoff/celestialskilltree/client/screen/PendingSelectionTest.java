package dev.lesteoff.celestialskilltree.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingSelectionTest {
    @Test
    void undoAndRedoWalkThroughEveryChange() {
        PendingSelection pending = new PendingSelection();
        pending.add("a");
        pending.add("b");
        pending.addRank("a");

        assertTrue(pending.undo());
        assertEquals(List.of("a", "b"), pending.request());
        assertTrue(pending.undo());
        assertEquals(List.of("a"), pending.request());
        assertTrue(pending.redo());
        assertTrue(pending.redo());
        assertEquals(List.of("a", "b", "a"), pending.request());
        assertFalse(pending.redo());
    }

    @Test
    void aNewChangeDropsTheRedoneFutureAndForgetClearsHistory() {
        PendingSelection pending = new PendingSelection();
        pending.add("a");
        pending.undo();
        pending.add("c");

        assertFalse(pending.redo());
        pending.forget();
        assertTrue(pending.isEmpty());
        assertFalse(pending.undo());
    }

    @Test
    void clearingAnEmptySelectionLeavesNothingToUndo() {
        PendingSelection pending = new PendingSelection();
        pending.clear();

        assertFalse(pending.undo());
    }
}
