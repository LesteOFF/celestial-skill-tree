package dev.nostalgiacraft.skills.compat.kubejs;

import dev.nostalgiacraft.skills.NostalgiaSkills;

final class KubeJSGuard {
    private static boolean disabled;

    private KubeJSGuard() {
    }

    static void run(String step, Runnable action) {
        if (disabled) {
            return;
        }
        try {
            action.run();
        } catch (LinkageError | RuntimeException failure) {
            disabled = true;
            NostalgiaSkills.LOGGER.warn("KubeJS integration disabled: {} failed with the installed KubeJS version", step, failure);
        }
    }
}
