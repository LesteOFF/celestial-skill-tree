package dev.lesteoff.celestialskilltree.text;

import java.util.List;

public record Phrase(String key, List<Part> parts) implements Part {
    public static Phrase of(String key, Part... parts) {
        return new Phrase(key, List.of(parts));
    }
}
