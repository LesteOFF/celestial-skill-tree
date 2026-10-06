package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import dev.nostalgiacraft.skills.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Character and tree-contribution statistics (design section 6.6). Final values come from the server-synced
 * attribute instances; the tree's share is the difference to the same instance without this mod's modifiers.
 */
public final class StatsPanel {
    private StatsPanel() {
    }

    private static String k(String s) {
        return "gui." + NostalgiaSkills.MODID + ".stats." + s;
    }

    public static List<Component> lines(Player p, PlayerSkillState s, SkillGraphSnapshot g, Collection<String> pending,
                                        int pendingCost) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(k("title")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            double v = hp.getValue();
            out.add(Component.translatable(k("max_health"), fmt(v), fmt(v / 2), signed(v - AttributeMath.withoutTree(hp))));
        }
        vanilla(out, p, Attributes.ATTACK_DAMAGE, "attack_damage");
        vanilla(out, p, Attributes.ATTACK_SPEED, "attack_speed");
        vanilla(out, p, Attributes.ARMOR, "armor");
        vanilla(out, p, Attributes.ARMOR_TOUGHNESS, "armor_toughness");
        AttributeInstance spd = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) {
            double base = AttributeMath.withoutTree(spd);
            out.add(Component.translatable(k("movement_speed"), fmt(spd.getValue() * 43.17),
                    signedPct(base == 0 ? 0 : spd.getValue() / base - 1)));
        }
        vanilla(out, p, Attributes.KNOCKBACK_RESISTANCE, "knockback_resistance");
        out.add(Component.translatable(k("crit"), pct(value(p, ModAttributes.CRIT_CHANCE)),
                fmt(1 + value(p, ModAttributes.CRIT_DAMAGE))));
        out.add(Component.translatable(k("mining"), signedPct(value(p, ModAttributes.MINING_SPEED)),
                signedPct(value(p, ModAttributes.TOOL_PRESERVATION))));
        for (Map.Entry<String, Holder<Attribute>> e : modExtras().entrySet()) {
            double v = value(p, e.getValue());
            if (v != 0) {
                out.add(Component.translatable(k("mod_line"), Component.translatable(e.getValue().value().getDescriptionId()), signedPct(v)));
            }
        }
        // conditional and triggered effects, and keystones with their penalties
        List<Component> cond = new ArrayList<>();
        List<Component> keys = new ArrayList<>();
        for (String id : s.allocations().keySet()) {
            SkillNodeDefinition n = g.nodes().get(id);
            if (n == null) {
                continue;
            }
            if (n.type() == NodeType.KEYSTONE) {
                keys.add(Component.literal(" ◆ ").append(Component.translatable(n.nameKey())).withStyle(ChatFormatting.GOLD));
                keys.add(Component.literal("   ").append(Component.translatable(n.descriptionKey())).withStyle(ChatFormatting.GRAY));
            } else if (n.effects().stream().anyMatch(e -> !(e instanceof SkillEffect.Attribute))) {
                cond.add(Component.literal(" • ").append(Component.translatable(n.nameKey())).append(": ")
                        .append(Component.translatable(n.descriptionKey())).withStyle(ChatFormatting.GRAY));
            }
        }
        out.add(Component.translatable(k("conditional")).withStyle(ChatFormatting.AQUA));
        out.addAll(cond.isEmpty() ? List.of(Component.translatable(k("none")).withStyle(ChatFormatting.DARK_GRAY)) : cond);
        out.add(Component.translatable(k("keystones")).withStyle(ChatFormatting.AQUA));
        out.addAll(keys.isEmpty() ? List.of(Component.translatable(k("none")).withStyle(ChatFormatting.DARK_GRAY)) : keys);
        out.add(Component.translatable(k("points"), s.totalPoints(), s.activeSpentPoints(), s.availablePoints(), pendingCost)
                .withStyle(ChatFormatting.YELLOW));
        // pending preview, clearly unapplied
        Map<String, Double> sums = new TreeMap<>();
        for (String id : pending) {
            SkillNodeDefinition n = g.nodes().get(id);
            if (n == null) {
                continue;
            }
            for (SkillEffect e : n.effects()) {
                if (e instanceof SkillEffect.Attribute a) {
                    sums.merge(a.attribute() + "|" + a.operation(), a.amount(), Double::sum);
                }
            }
        }
        if (!sums.isEmpty()) {
            out.add(Component.translatable(k("pending")).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            sums.forEach((key, v) -> {
                String[] parts = key.split("\\|");
                BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(parts[0])).ifPresent(h -> {
                    boolean mult = !parts[1].equals("add_value") || parts[0].startsWith(NostalgiaSkills.MODID + ":");
                    out.add(Component.literal("   ").append(Component.translatable(h.value().getDescriptionId()))
                            .append(" " + (mult ? signedPct(v) : signed(v))).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
                });
            });
        }
        return out;
    }

    private static Map<String, Holder<Attribute>> modExtras() {
        Map<String, Holder<Attribute>> m = new TreeMap<>();
        for (String n : List.of("dodge_chance", "lifesteal", "damage_reduction", "magic_resistance", "potion_duration",
                "healing_received", "light_weapon_damage", "heavy_weapon_damage", "projectile_damage", "long_range_damage",
                "crossbow_damage", "charged_shot_damage", "low_health_damage")) {
            m.put(n, ModAttributes.ALL.get(n));
        }
        return m;
    }

    private static void vanilla(List<Component> out, Player p, Holder<Attribute> h, String name) {
        AttributeInstance inst = p.getAttribute(h);
        if (inst != null) {
            out.add(Component.translatable(k(name), fmt(inst.getValue()), signed(inst.getValue() - AttributeMath.withoutTree(inst))));
        }
    }

    private static double value(Player p, Holder<Attribute> h) {
        return p.getAttributes().hasAttribute(h) ? p.getAttributeValue(h) : 0;
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, Math.abs(v - Math.rint(v)) < 1e-6 ? "%.0f" : "%.2f", v);
    }

    static String signed(double v) {
        return (v >= 0 ? "+" : "") + fmt(v);
    }

    static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }

    static String signedPct(double v) {
        return (v >= 0 ? "+" : "") + pct(v);
    }
}
