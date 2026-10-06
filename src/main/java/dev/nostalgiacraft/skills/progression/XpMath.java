package dev.nostalgiacraft.skills.progression;

/**
 * Exact raw-XP arithmetic matching Minecraft 1.21.1 {@code Player#getXpNeededForNextLevel}:
 * levels 0-15 need 2L+7, levels 15-30 need 5L-38, levels 30+ need 9L-158 points.
 * The player's spendable balance is derived from level and bar progress (not the unreliable totalExperience
 * field), and after a debit level, progress and total are rewritten together from one exact integer.
 */
public final class XpMath {
    public static final int MAX_LEVEL = 21_863; // keeps totals inside int range

    private XpMath() {
    }

    public static int xpNeededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    /** Total raw XP required to reach {@code level} with an empty bar. */
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

    /**
     * Spendable raw XP for a level and bar progress. Rounds the bar down (with a tiny tolerance for float error)
     * so a purchase can never be paid with XP the player does not have.
     */
    public static long balance(int level, float progress) {
        int lv = Math.max(0, level);
        float p = Math.max(0f, Math.min(1f, progress));
        long inBar = (long) Math.floor(p * (double) xpNeededForNextLevel(lv) + 1e-3);
        inBar = Math.min(inBar, xpNeededForNextLevel(lv) - 1L);
        return totalForLevel(lv) + Math.max(0, inBar);
    }

    /** Level and bar progress representing exactly {@code total} raw XP. */
    public record LevelState(int level, float progress, int total) {
    }

    public static LevelState fromTotal(long total) {
        if (total < 0) {
            throw new IllegalArgumentException("negative experience");
        }
        int level = 0;
        while (level < MAX_LEVEL && totalForLevel(level + 1) <= total) {
            level++;
        }
        long rem = total - totalForLevel(level);
        float progress = (float) rem / (float) xpNeededForNextLevel(level);
        return new LevelState(level, progress, (int) Math.min(Integer.MAX_VALUE, total));
    }
}
