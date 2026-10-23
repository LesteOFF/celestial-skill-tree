package dev.lesteoff.celestialskilltree.progression;

public final class WorldClock {
    private static final long TICKS_PER_DAY = 24_000;
    private static final long NIGHT_START = 12_542;
    private static final long NIGHT_END = 23_460;

    private WorldClock() {
    }

    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    public static boolean isDaylight(long dayTime) {
        return timeOfDay(dayTime) < NIGHT_START;
    }

    public static boolean isNight(long dayTime) {
        long time = timeOfDay(dayTime);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    public static long timeOfDay(long dayTime) {
        return Math.floorMod(dayTime, TICKS_PER_DAY);
    }
}
