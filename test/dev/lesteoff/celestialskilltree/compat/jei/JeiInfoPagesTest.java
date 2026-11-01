package dev.lesteoff.celestialskilltree.compat.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiInfoPagesTest {
    private static final Path ITEM_MODELS = Path.of("src/main/resources/assets/celestialskilltree/models/item");
    private static final Path LANG = Path.of("src/main/resources/assets/celestialskilltree/lang");
    private static final String INFO_PREFIX = "jei.celestialskilltree.info.";

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "pt_br"})
    void everyItemHasAnInfoPage(String language) throws IOException {
        JsonObject translations = JsonParser.parseString(Files.readString(LANG.resolve(language + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (String item : items()) {
            assertTrue(translations.has(INFO_PREFIX + item), language + " has no JEI page for " + item);
        }
    }

    private static List<String> items() throws IOException {
        try (Stream<Path> models = Files.list(ITEM_MODELS)) {
            return models.map(model -> model.getFileName().toString().replace(".json", "")).toList();
        }
    }
}
