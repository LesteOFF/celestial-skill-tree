package dev.lesteoff.celestialskilltree.progression;

import java.util.Locale;

public enum SkyEvent {
    NONE, ECLIPSE, STAR_SHOWER;

    private static final SkyEvent[] VALUES = values();

    public static SkyEvent byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public static SkyEvent byName(String name) {
        for (SkyEvent event : VALUES) {
            if (event.id().equals(name)) {
                return event;
            }
        }
        return NONE;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
