package dev.nostalgiacraft.skills.graph;

public interface KnownIds {
    boolean isAttribute(String id);

    boolean isMobEffect(String id);

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
