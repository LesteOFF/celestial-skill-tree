package dev.nostalgiacraft.skills.compat;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OptionalEvents {
    private static final int UNEXPECTED_FAILURE_LIMIT = 20;
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicInteger> UNEXPECTED_FAILURES = new ConcurrentHashMap<>();

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
        guard(integration, "handling an event", action);
    }

    public static void guard(String integration, String step, Runnable action) {
        guarded(integration, step, null, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T guarded(String integration, T fallback, Supplier<T> query) {
        return guarded(integration, "querying it", fallback, query);
    }

    private static <T> T guarded(String integration, String step, T fallback, Supplier<T> query) {
        if (DISABLED.contains(integration)) {
            return fallback;
        }
        try {
            return query.get();
        } catch (LinkageError | Reflection.Failure versionMismatch) {
            disable(integration, step, versionMismatch);
        } catch (RuntimeException unexpected) {
            countUnexpected(integration, step, unexpected);
        }
        return fallback;
    }

    public static boolean isDisabled(String integration) {
        return DISABLED.contains(integration);
    }

    public static Set<String> disabled() {
        return Set.copyOf(DISABLED);
    }

    private static void countUnexpected(String integration, String step, RuntimeException failure) {
        int failures = UNEXPECTED_FAILURES.computeIfAbsent(integration, key -> new AtomicInteger()).incrementAndGet();
        if (failures == 1) {
            NostalgiaSkills.LOGGER.error("{} integration failed while {}; it stays on unless this repeats", integration, step, failure);
        }
        if (failures >= UNEXPECTED_FAILURE_LIMIT) {
            disable(integration, step + " (" + failures + " failures)", failure);
        }
    }

    private static void disable(String integration, String step, Throwable failure) {
        if (DISABLED.add(integration)) {
            NostalgiaSkills.LOGGER.warn("{} integration disabled: failed while {} with the installed version", integration, step, failure);
        }
    }
}
