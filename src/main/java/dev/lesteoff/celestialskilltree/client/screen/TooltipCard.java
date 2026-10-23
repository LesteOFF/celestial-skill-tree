package dev.lesteoff.celestialskilltree.client.screen;

import dev.lesteoff.celestialskilltree.CelestialSkillTree;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record TooltipCard(ResourceLocation frame, ResourceLocation icon, Component title, Component subtitle, Component badge,
                          List<List<Row>> sections) {
    public enum Marker {
        GAIN, COST, EFFECT, BLACK_HOLE, SEALED, PAIR, PORTAL, REQUIREMENT, LOCK, CLICK, CHECK, RANK;

        private final ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(CelestialSkillTree.MODID,
                "tooltip/" + name().toLowerCase(Locale.ROOT));

        public ResourceLocation sprite() {
            return sprite;
        }
    }

    public record Row(Optional<Marker> marker, Component text) {
        public static Row of(Marker marker, Component text) {
            return new Row(Optional.of(marker), text);
        }

        public static Row plain(Component text) {
            return new Row(Optional.empty(), text);
        }
    }
}
