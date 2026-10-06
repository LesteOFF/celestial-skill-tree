package dev.nostalgiacraft.skills.progression;

public final class EclipseSchedule {
    public static final long UNSCHEDULED = -1;

    private EclipseSchedule() {
    }

    public static boolean isActive(long eclipseDay, long worldDay, boolean night) {
        return night && eclipseDay == worldDay;
    }

    public static boolean needsScheduling(long eclipseDay, long worldDay) {
        return eclipseDay < worldDay;
    }

    public static long nextAfter(long worldDay, int averageDays, double roll) {
        int span = Math.max(1, 2 * averageDays - 1);
        return worldDay + 1 + (long) Math.floor(Math.max(0.0, Math.min(roll, 0.999999)) * span);
    }
}
