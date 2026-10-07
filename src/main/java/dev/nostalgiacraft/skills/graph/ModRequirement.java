package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonElement;

import java.util.function.Predicate;

public final class ModRequirement {
    public static final String FIELD = "requires_mod";

    private ModRequirement() {
    }

    public static boolean isMet(JsonElement file, Predicate<String> isModLoaded) {
        if (!file.isJsonObject() || !file.getAsJsonObject().has(FIELD)) {
            return true;
        }
        JsonElement required = file.getAsJsonObject().get(FIELD);
        if (required.isJsonArray()) {
            return required.getAsJsonArray().asList().stream().map(JsonElement::getAsString).allMatch(isModLoaded);
        }
        return isModLoaded.test(required.getAsString());
    }
}
