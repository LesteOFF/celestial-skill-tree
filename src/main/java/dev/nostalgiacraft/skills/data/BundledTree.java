package dev.nostalgiacraft.skills.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.GraphParser;
import dev.nostalgiacraft.skills.graph.ModRequirement;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BundledTree {
    public static final String INDEX = "/nostalgiaskills_bundled_index.json";

    private BundledTree() {
    }

    public static GraphParser.Sources load() throws IOException {
        JsonElement index = read(INDEX);
        Map<String, JsonElement> trees = new HashMap<>();
        Map<String, JsonElement> nodes = new HashMap<>();
        Map<String, JsonElement> conns = new HashMap<>();
        fill(index, "skill_trees", trees);
        fill(index, "skill_nodes", nodes);
        fill(index, "skill_connections", conns);
        return new GraphParser.Sources(trees, nodes, conns);
    }

    private static void fill(JsonElement index, String dir, Map<String, JsonElement> out) throws IOException {
        List<JsonElement> paths = index.getAsJsonObject().getAsJsonArray(dir).asList();
        for (JsonElement element : paths) {
            String path = element.getAsString();
            JsonElement file = read("/data/" + NostalgiaSkills.MODID + "/" + dir + "/" + path + ".json");
            if (ModRequirement.isMet(file, ModList.get()::isLoaded)) {
                out.put(NostalgiaSkills.MODID + ":" + path, file);
            }
        }
    }

    private static JsonElement read(String resource) throws IOException {
        try (InputStream inputStream = BundledTree.class.getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IOException("missing bundled resource " + resource);
            }
            try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        }
    }
}
