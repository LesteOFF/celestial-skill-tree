package dev.nostalgiacraft.skills.graph;

import java.util.regex.Pattern;

/** Namespaced identifier rules, mirroring Minecraft's ResourceLocation character set. */
public final class Ids {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
    public static final int MAX_LENGTH = 128;

    private Ids() {
    }

    public static boolean isValid(String id) {
        if (id == null || id.length() > MAX_LENGTH) {
            return false;
        }
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        return NAMESPACE.matcher(id.substring(0, colon)).matches() && PATH.matcher(id.substring(colon + 1)).matches();
    }

    public static String namespace(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    public static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
