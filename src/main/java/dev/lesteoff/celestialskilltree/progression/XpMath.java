package dev.lesteoff.celestialskilltree.progression;

public final class XpMath {
    public static final int MAX_LEVEL = 21_863;

    private XpMath() {
    }

    public static int xpNeededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    public static long totalForLevel(int level) {
        long l = level;
        if (level <= 16) {
            return l * l + 6 * l;
        }
        if (level <= 31) {
            return (5 * l * l - 81 * l + 720) / 2;
        }
        return (9 * l * l - 325 * l + 4440) / 2;
    }

    public static long balance(int level, float progress) {
        int lv = Math.max(0, level);
        float p = Math.max(0f, Math.min(1f, progress));
        long inBar = (long) Math.floor(p * (double) xpNeededForNextLevel(lv) + 1e-3);
        inBar = Math.min(inBar, xpNeededForNextLevel(lv) - 1L);
        return totalForLevel(lv) + Math.max(0, inBar);
    }

    public record LevelState(int level, float progress, int total) {
    }

    public static LevelState fromTotal(long total) {
        if (total < 0) {
            throw new IllegalArgumentException("negative experience");
        }
        int level = 0;
        int above = MAX_LEVEL + 1;
        while (above - level > 1) {
            int middle = (level + above) >>> 1;
            if (totalForLevel(middle) <= total) {
                level = middle;
            } else {
                above = middle;
            }
        }
        long rem = total - totalForLevel(level);
        float progress = (float) rem / (float) xpNeededForNextLevel(level);
        return new LevelState(level, progress, (int) Math.min(Integer.MAX_VALUE, total));
    }
}
