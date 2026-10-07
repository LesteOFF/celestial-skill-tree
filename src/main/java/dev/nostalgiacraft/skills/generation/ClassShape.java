package dev.nostalgiacraft.skills.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ClassShape(int index, Slot hub, List<Slot> trunk, List<Rib> ribs, List<Slot> keystones, List<Slot> portals) {
    public List<Slot> slots() {
        List<Slot> all = new ArrayList<>();
        all.add(hub);
        all.addAll(trunk);
        ribs.forEach(rib -> all.addAll(rib.slots()));
        all.addAll(keystones);
        all.addAll(portals);
        return all;
    }

    public int skillCount() {
        return slots().size() - portals.size();
    }

    public Optional<Rib> rib(int ring, Rib.Side side) {
        return ribs.stream().filter(rib -> rib.ring() == ring && rib.side() == side).findFirst();
    }
}
