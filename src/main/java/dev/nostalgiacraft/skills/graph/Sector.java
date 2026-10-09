package dev.nostalgiacraft.skills.graph;

public record Sector(String id, String root, String nameKey) {
    public static Sector rootless(String id, String nameKey) {
        return new Sector(id, "", nameKey);
    }

    public boolean isRootless() {
        return root.isEmpty();
    }
}
