package dev.nostalgiacraft.skills.generation;

import java.util.List;

public record Rib(int ring, Side side, List<Slot> slots, boolean reachesBoundary) {
    public enum Side {
        COUNTER_CLOCKWISE(-1, "a"),
        CLOCKWISE(1, "b");

        private final int direction;
        private final String label;

        Side(int direction, String label) {
            this.direction = direction;
            this.label = label;
        }

        public int direction() {
            return direction;
        }

        public String label() {
            return label;
        }
    }

    public Slot tip() {
        return slots.get(slots.size() - 1);
    }

    public int middleIndex() {
        return slots.size() / 2;
    }
}
