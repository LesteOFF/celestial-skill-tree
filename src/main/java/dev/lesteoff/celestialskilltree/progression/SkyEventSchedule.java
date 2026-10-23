package dev.lesteoff.celestialskilltree.progression;

public final class SkyEventSchedule {
    public static final long UNSCHEDULED = -1;

    private SkyEventSchedule() {
    }

    public static long dayAfter(long day, long timeOfDayBefore, long timeOfDayNow) {
        return timeOfDayNow < timeOfDayBefore ? day + 1 : day;
    }

    public static SkyEvent current(long eclipseDay, long showerDay, long today, long dayTime) {
        if (eclipseDay == today && WorldClock.isDaylight(dayTime)) {
            return SkyEvent.ECLIPSE;
        }
        if (showerDay == today && WorldClock.isNight(dayTime)) {
            return SkyEvent.STAR_SHOWER;
        }
        return SkyEvent.NONE;
    }

    public static boolean needsScheduling(long scheduledDay, long worldDay) {
        return scheduledDay < worldDay;
    }

    public static long nextAfter(long worldDay, int averageDays, double roll) {
        int span = Math.max(1, 2 * averageDays - 1);
        return worldDay + 1 + (long) Math.floor(Math.max(0.0, Math.min(roll, 0.999999)) * span);
    }
}
