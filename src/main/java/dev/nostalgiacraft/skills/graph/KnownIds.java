package dev.nostalgiacraft.skills.graph;

/** Registry lookups injected by the game layer so the parser itself stays free of Minecraft classes. */
public interface KnownIds {
    boolean isAttribute(String id);

    boolean isMobEffect(String id);

    /** Accepts any syntactically valid id; used by unit tests and offline tools. */
    KnownIds PERMISSIVE = new KnownIds() {
        @Override
        public boolean isAttribute(String id) {
            return Ids.isValid(id);
        }

        @Override
        public boolean isMobEffect(String id) {
            return Ids.isValid(id);
        }
    };
}
