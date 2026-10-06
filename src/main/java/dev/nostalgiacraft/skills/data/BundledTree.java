package dev.nostalgiacraft.skills.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.GraphParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The verified tree shipped inside the mod jar, read straight from the classpath (bypassing datapacks) using the
 * index written by tools/layout/export.py. Used only when the datapack-assembled tree fails on initial load.
 */
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
        for (JsonElement p : paths) {
            String path = p.getAsString();
            out.put(NostalgiaSkills.MODID + ":" + path, read("/data/" + NostalgiaSkills.MODID + "/" + dir + "/" + path + ".json"));
        }
    }

    private static JsonElement read(String resource) throws IOException {
        try (InputStream in = BundledTree.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing bundled resource " + resource);
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r);
            }
        }
    }
}
