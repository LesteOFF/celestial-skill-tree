package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.generation.TreeDraw.ClassDraw;
import dev.nostalgiacraft.skills.generation.TreeDraw.NotableChoice;
import dev.nostalgiacraft.skills.generation.TreeDraw.RibDraw;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DrawJson {
    private static final String CLASS = "class";
    private static final String TRUNK = "trunk";
    private static final String RIBS = "ribs";
    private static final String KEYSTONES = "keystones";
    private static final String THEME = "theme";
    private static final String NOTABLE = "notable";
    private static final String MINORS = "minors";
    private static final String OPENING_THEME = "opening_theme";
    private static final String OPENING = "opening";
    private static final String MIDDLE_THEME = "middle_theme";
    private static final String MIDDLE = "middle";

    private DrawJson() {
    }

    public static String write(TreeDraw draw) {
        JsonArray classes = new JsonArray();
        draw.classes().forEach(drawn -> classes.add(write(drawn)));
        return classes.toString();
    }

    public static TreeDraw read(String text) {
        if (text.isEmpty()) {
            return TreeDraw.EMPTY;
        }
        try {
            List<ClassDraw> classes = new ArrayList<>();
            JsonParser.parseString(text).getAsJsonArray().forEach(drawn -> classes.add(readClass(drawn.getAsJsonObject())));
            return new TreeDraw(List.copyOf(classes));
        } catch (RuntimeException unreadable) {
            return TreeDraw.EMPTY;
        }
    }

    private static JsonObject write(ClassDraw drawn) {
        JsonObject ribs = new JsonObject();
        drawn.ribs().forEach((rib, choice) -> ribs.add(rib, write(choice)));
        JsonObject json = new JsonObject();
        json.addProperty(CLASS, drawn.classId());
        json.add(TRUNK, strings(drawn.trunk()));
        json.add(RIBS, ribs);
        json.add(KEYSTONES, strings(drawn.keystones()));
        return json;
    }

    private static JsonObject write(RibDraw rib) {
        JsonObject json = new JsonObject();
        rib.notable().ifPresent(notable -> {
            json.addProperty(THEME, notable.theme());
            json.addProperty(NOTABLE, notable.notable());
        });
        rib.opening().ifPresent(opening -> {
            json.addProperty(OPENING_THEME, opening.theme());
            json.addProperty(OPENING, opening.notable());
        });
        rib.middle().ifPresent(middle -> {
            json.addProperty(MIDDLE_THEME, middle.theme());
            json.addProperty(MIDDLE, middle.notable());
        });
        json.add(MINORS, strings(rib.minors()));
        return json;
    }

    private static ClassDraw readClass(JsonObject json) {
        Map<String, RibDraw> ribs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> rib : json.getAsJsonObject(RIBS).entrySet()) {
            ribs.put(rib.getKey(), readRib(rib.getValue().getAsJsonObject()));
        }
        return new ClassDraw(json.get(CLASS).getAsString(), strings(json.getAsJsonArray(TRUNK)), ribs,
                strings(json.getAsJsonArray(KEYSTONES)));
    }

    private static RibDraw readRib(JsonObject json) {
        return new RibDraw(choice(json, OPENING_THEME, OPENING), choice(json, MIDDLE_THEME, MIDDLE), choice(json, THEME, NOTABLE),
                strings(json.getAsJsonArray(MINORS)));
    }

    private static Optional<NotableChoice> choice(JsonObject json, String theme, String notable) {
        return json.has(notable) ? Optional.of(new NotableChoice(json.get(theme).getAsString(), json.get(notable).getAsString()))
                : Optional.empty();
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.getAsString()));
        return List.copyOf(values);
    }
}
