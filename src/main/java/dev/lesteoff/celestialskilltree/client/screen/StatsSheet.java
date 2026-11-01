package dev.lesteoff.celestialskilltree.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

record StatsSheet(Component player, Optional<Sign> sign, int skills, Points points, List<Section> sections, List<Effect> effects,
                  List<Effect> keystones) {
    record Sign(String id, Component name) {
    }

    record Points(int total, int spent, int free, int pending) {
    }

    record Section(Component title, List<Stat> stats) {
    }

    record Stat(ResourceLocation icon, Component label, String value, Optional<String> treeBonus, Optional<String> preview) {
    }

    record Effect(Component name, Component description, int count) {
    }

    static List<Effect> merge(List<Effect> effects) {
        Map<String, Effect> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Effect effect : effects) {
            merged.merge(effect.name().getString() + '\n' + effect.description().getString(), effect,
                    (held, again) -> new Effect(held.name(), held.description(), held.count() + again.count()));
        }
        return List.copyOf(merged.values());
    }
}
