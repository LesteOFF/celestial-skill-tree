package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

final class JsonFields {
    private static final double MAX_MAGNITUDE = 1_000;

    private final JsonObject json;
    private final String source;
    private final String prefix;
    private final ValidationReport report;

    JsonFields(JsonObject json, String source, String prefix, ValidationReport report) {
        this.json = json;
        this.source = source;
        this.prefix = prefix;
        this.report = report;
    }

    JsonObject json() {
        return json;
    }

    boolean has(String key) {
        return json.has(key);
    }

    void error(String key, String message) {
        report.error(source, prefix + key, message);
    }

    JsonFields child(JsonObject child, String childPrefix) {
        return new JsonFields(child, source, prefix + childPrefix, report);
    }

    String text(String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            error(key, "missing string");
            return null;
        }
        String value = json.get(key).getAsString();
        if (value.isEmpty() || value.length() > GraphParser.MAX_TEXT) {
            error(key, "length must be 1.." + GraphParser.MAX_TEXT);
            return null;
        }
        return value;
    }

    String id(String key) {
        String value = text(key);
        if (value != null && !Ids.isValid(value)) {
            error(key, "invalid namespaced id '" + value + "'");
            return null;
        }
        return value;
    }

    double number(String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) {
            error(key, "missing number");
            return Double.NaN;
        }
        return json.get(key).getAsDouble();
    }

    void requireBounded(String key, double value) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_MAGNITUDE) {
            error(key, "must be finite and within +/-1000");
        }
    }

    int integer(String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    int integerWithin(String key, int fallback, int min, int max) {
        int value = integer(key, fallback);
        if (value < min || value > max) {
            error(key, "must be within " + min + ".." + max);
        }
        return value;
    }

    boolean flag(String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }

    String rawText(String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    Optional<String> optionalRawText(String key) {
        return json.has(key) ? Optional.of(json.get(key).getAsString()) : Optional.empty();
    }

    Optional<JsonArray> array(String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            error(key, "missing array");
            return Optional.empty();
        }
        return Optional.of(json.getAsJsonArray(key));
    }

    Optional<JsonFields> object(String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonObject()) {
            error(key, "missing object");
            return Optional.empty();
        }
        return Optional.of(child(element.getAsJsonObject(), key + "."));
    }

    void requireSupportedSchema() {
        if (!json.has("schema_version")) {
            error("schema_version", "missing");
        } else if (json.get("schema_version").getAsInt() != GraphParser.SUPPORTED_SCHEMA) {
            error("schema_version", "unsupported version " + json.get("schema_version") + " (supported: "
                    + GraphParser.SUPPORTED_SCHEMA + ")");
        }
    }
}
