package dev.nostalgiacraft.skills.compat;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OptionalEvents {
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();

    private OptionalEvents() {
    }

    @SuppressWarnings("unchecked")
    public static <E extends Event> void listen(String integration, String eventClass, Consumer<E> handler) {
        try {
            Class<E> type = (Class<E>) Class.forName(eventClass);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, event -> guard(integration, () -> handler.accept(event)));
        } catch (ReflectiveOperationException | LinkageError failure) {
            disable(integration, "listening to " + eventClass, failure);
        }
    }

    public static void guard(String integration, Runnable action) {
        if (DISABLED.contains(integration)) {
            return;
        }
        try {
            action.run();
        } catch (LinkageError | RuntimeException failure) {
            disable(integration, "handling an event", failure);
        }
    }

    public static <T> T guarded(String integration, T fallback, Supplier<T> query) {
        if (DISABLED.contains(integration)) {
            return fallback;
        }
        try {
            return query.get();
        } catch (LinkageError | RuntimeException failure) {
            disable(integration, "querying it", failure);
            return fallback;
        }
    }

    private static void disable(String integration, String step, Throwable failure) {
        if (DISABLED.add(integration)) {
            NostalgiaSkills.LOGGER.warn("{} integration disabled: failed while {} with the installed version", integration, step, failure);
        }
    }
}
