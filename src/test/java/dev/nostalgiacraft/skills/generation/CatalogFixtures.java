package dev.nostalgiacraft.skills.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nostalgiacraft.skills.graph.KnownIds;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

final class CatalogFixtures {
    static final String WARRIOR = "test:warrior";
    static final String MAGE = "test:mage";
    static final String MAGIC_MOD = "magicmod";
    static final String HEALTH = "{'type':'attribute','attribute':'minecraft:generic.max_health','operation':'add_value','amount':1}";
    static final String ARMOR = "{'type':'attribute','attribute':'minecraft:generic.armor','operation':'add_value','amount':1}";
    static final String ON_KILL = "{'type':'trigger','trigger':'on_kill','action':{'type':'heal','amount':1}}";
    private static final String ICON = "test:textures/icon.png";

    private final Map<String, JsonElement> classes = new TreeMap<>();
    private final Map<String, JsonElement> themes = new TreeMap<>();
    private final Map<String, JsonElement> keystones = new TreeMap<>();
    private Optional<JsonElement> rules = Optional.empty();

    static CatalogFixtures empty() {
        return new CatalogFixtures();
    }

    static CatalogFixtures standard() {
        return empty()
                .classFile(WARRIOR, classJson("Warrior", ""))
                .classFile(MAGE, classJson("Mage", "'requires_any_mod':['" + MAGIC_MOD + "'],'priority':5,'fallback_class':'" + WARRIOR + "',"))
                .theme("test:warrior/might", themeJson(WARRIOR, "Might", "'notables':[" + notableJson("crusher", HEALTH) + "],"))
                .theme("test:mage/mana", themeJson(MAGE, "Mana", ""))
                .keystone("test:warrior/titan", keystoneJson(WARRIOR, "Titan", "'text':'You are huge.',"))
                .keystone("test:mage/archmage", keystoneJson(MAGE, "Archmage", "'text':'Pure magic.',"));
    }

    static CatalogFixtures world(int classCount, int themesPerClass, int notablesPerTheme, int keystonesPerClass) {
        CatalogFixtures fixtures = empty();
        for (int classIndex = 0; classIndex < classCount; classIndex++) {
            String classId = "test:c" + classIndex;
            fixtures.classFile(classId, classJson("Class " + classIndex, ""));
            for (int theme = 0; theme < themesPerClass; theme++) {
                StringBuilder notables = new StringBuilder();
                for (int notable = 0; notable < notablesPerTheme; notable++) {
                    notables.append(notable == 0 ? "" : ",").append(notableJson("n" + (theme * notablesPerTheme + notable), HEALTH));
                }
                fixtures.theme(classId + "/t" + theme, themeJson(classId, "Theme " + theme, "'notables':[" + notables + "],"));
            }
            for (int keystone = 0; keystone < keystonesPerClass; keystone++) {
                fixtures.keystone(classId + "/k" + keystone, keystoneJson(classId, "Keystone " + keystone, "'text':'Plain.',"));
            }
        }
        return fixtures;
    }

    Catalog catalog() {
        return parse().catalog().orElseThrow(() -> new AssertionError(parse().report().errors().toString()));
    }

    static String classJson(String name, String extraFields) {
        return "{'name':'" + name + "','icon':'" + ICON + "'," + extraFields
                + "'hub':{'effects':[" + HEALTH + "]},'trunk':{'name':'" + name + " Training','effects':[" + ARMOR + "]}}";
    }

    static String themeJson(String classId, String name, String extraFields) {
        return "{'class':'" + classId + "','name':'" + name + "','icon':'" + ICON + "'," + extraFields
                + "'minor':{'effects':[" + HEALTH + "]}}";
    }

    static String notableJson(String id, String effects) {
        return "{'id':'" + id + "','name':'" + id + "','effects':[" + effects + "]}";
    }

    static String keystoneJson(String classId, String name, String extraFields) {
        return "{'class':'" + classId + "','name':'" + name + "'," + extraFields + "'effects':[" + HEALTH + "," + ARMOR + "]}";
    }

    CatalogFixtures classFile(String id, String json) {
        classes.put(id, JsonParser.parseString(json));
        return this;
    }

    CatalogFixtures theme(String id, String json) {
        themes.put(id, JsonParser.parseString(json));
        return this;
    }

    CatalogFixtures keystone(String id, String json) {
        keystones.put(id, JsonParser.parseString(json));
        return this;
    }

    CatalogFixtures withoutTheme(String id) {
        themes.remove(id);
        return this;
    }

    CatalogFixtures withoutKeystone(String id) {
        keystones.remove(id);
        return this;
    }

    CatalogFixtures withoutThemeNotables(String classId, int themesThatKeepTheirs) {
        for (String id : java.util.List.copyOf(themes.keySet())) {
            boolean ofClass = id.startsWith(classId + "/t");
            if (ofClass && Integer.parseInt(id.substring(id.lastIndexOf('t') + 1)) >= themesThatKeepTheirs) {
                themes.get(id).getAsJsonObject().remove("notables");
            }
        }
        return this;
    }

    CatalogFixtures rules(String json) {
        rules = Optional.of(JsonParser.parseString(json));
        return this;
    }

    CatalogSources sources() {
        return new CatalogSources(classes, themes, keystones, rules);
    }

    CatalogParser.Outcome parse() {
        return new CatalogParser(KnownIds.PERMISSIVE).parse(sources());
    }

    CatalogParser.Outcome parseWithMods(String... loadedMods) {
        return new CatalogParser(KnownIds.PERMISSIVE).parse(sources().availableWith(java.util.Set.of(loadedMods)::contains));
    }
}
