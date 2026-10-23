package dev.lesteoff.celestialskilltree.generation;

public record TreeRules(int nodesPerClass, int maxClasses, int minThemesForOwnClass, int maxCopiesPerSkill, boolean varyBySeed,
                        boolean portals) {
    public static final int MIN_NODES_PER_CLASS = 12;
    public static final int MAX_NODES_PER_CLASS = 130;
    public static final int DEFAULT_NODES_PER_CLASS = 100;
    public static final int MAX_CLASSES = 16;
    public static final int MAX_THEMES_FOR_OWN_CLASS = 32;
    public static final int MAX_COPIES_PER_SKILL = 20;
    public static final TreeRules DEFAULT = new TreeRules(DEFAULT_NODES_PER_CLASS, 10, 4, 5, true, true);
}
