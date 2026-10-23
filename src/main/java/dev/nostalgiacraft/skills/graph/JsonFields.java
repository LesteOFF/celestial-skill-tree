package dev.nostalgiacraft.skills.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

public final class JsonFields {
    private static final double MAX_MAGNITUDE = 1_000;

    private final JsonObject json;
    private final String source;
    private final String prefix;
    private final ValidationReport report;

    public JsonFields(JsonObject json, String source, String prefix, ValidationReport report) {
        this.json = json;
        this.source = source;
        this.prefix = prefix;
        this.report = report;
    }

    public JsonObject json() {
        return json;
    }

    public boolean has(String key) {
        return json.has(key);
    }

    public void error(String key, String message) {
        report.error(source, prefix + key, message);
    }

    public JsonFields child(JsonObject child, String childPrefix) {
        return new JsonFields(child, source, prefix + childPrefix, report);
    }

    public String text(String key) {
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

    public String id(String key) {
        String value = text(key);
        if (value != null && !Ids.isValid(value)) {
            error(key, "invalid namespaced id '" + value + "'");
            return null;
        }
        return value;
    }

    public Optional<String> optionalText(String key) {
        return has(key) ? Optional.ofNullable(text(key)) : Optional.empty();
    }

    public Optional<String> optionalId(String key) {
        return has(key) ? Optional.ofNullable(id(key)) : Optional.empty();
    }

    public void warn(String key, String message) {
        report.warn(source, prefix + key, message);
    }

    public double number(String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) {
            error(key, "missing number");
            return Double.NaN;
        }
        return json.get(key).getAsDouble();
    }

    public void requireBounded(String key, double value) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_MAGNITUDE) {
            error(key, "must be finite and within +/-1000");
        }
    }

    public int integer(String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    public int integerWithin(String key, int fallback, int min, int max) {
        int value = integer(key, fallback);
        if (value < min || value > max) {
            error(key, "must be within " + min + ".." + max);
        }
        return value;
    }

    public boolean flag(String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }

    public boolean flag(String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    public String rawText(String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    public Optional<String> optionalRawText(String key) {
        return json.has(key) ? Optional.of(json.get(key).getAsString()) : Optional.empty();
    }

    public Optional<JsonArray> array(String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            error(key, "missing array");
            return Optional.empty();
        }
        return Optional.of(json.getAsJsonArray(key));
    }

    public Optional<JsonFields> object(String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonObject()) {
            error(key, "missing object");
            return Optional.empty();
        }
        return Optional.of(child(element.getAsJsonObject(), key + "."));
    }

    public void checkSchemaVersion() {
        if (json.has("schema_version") && json.get("schema_version").getAsInt() != GraphParser.SUPPORTED_SCHEMA) {
            error("schema_version", "unsupported version " + json.get("schema_version") + " (supported: "
                    + GraphParser.SUPPORTED_SCHEMA + ")");
        }
    }
}
