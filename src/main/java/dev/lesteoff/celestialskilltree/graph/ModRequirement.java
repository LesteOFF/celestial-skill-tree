package dev.lesteoff.celestialskilltree.graph;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.function.Predicate;

public final class ModRequirement {
    public static final String FIELD = "requires_mod";
    public static final String ANY_FIELD = "requires_any_mod";

    private ModRequirement() {
    }

    public static boolean isMet(JsonElement file, Predicate<String> isModLoaded) {
        if (!file.isJsonObject()) {
            return true;
        }
        List<String> all = mods(file, FIELD);
        List<String> any = mods(file, ANY_FIELD);
        return all.stream().allMatch(isModLoaded) && (any.isEmpty() || any.stream().anyMatch(isModLoaded));
    }

    private static List<String> mods(JsonElement file, String field) {
        JsonElement required = file.getAsJsonObject().get(field);
        if (required == null) {
            return List.of();
        }
        if (required.isJsonArray()) {
            return required.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
        }
        return List.of(required.getAsString());
    }
}
