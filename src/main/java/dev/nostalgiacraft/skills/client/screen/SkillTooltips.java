package dev.nostalgiacraft.skills.client.screen;

import dev.nostalgiacraft.skills.NostalgiaSkills;
import dev.nostalgiacraft.skills.graph.ExclusiveGroup;
import dev.nostalgiacraft.skills.graph.NodeType;
import dev.nostalgiacraft.skills.graph.Sector;
import dev.nostalgiacraft.skills.graph.SkillEffect;
import dev.nostalgiacraft.skills.graph.SkillGraphSnapshot;
import dev.nostalgiacraft.skills.graph.SkillNodeDefinition;
import dev.nostalgiacraft.skills.progression.AllocationService;
import dev.nostalgiacraft.skills.progression.PathFinder;
import dev.nostalgiacraft.skills.progression.PlayerSkillState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds node tooltips (design section 6.5). Rebuilt only when the hovered node or state changes. */
public final class SkillTooltips {
    private SkillTooltips() {
    }

    static String key(String suffix) {
        return "gui." + NostalgiaSkills.MODID + "." + suffix;
    }

    public static List<Component> build(SkillGraphSnapshot g, PlayerSkillState s, Collection<String> pending,
                                        SkillNodeDefinition n, NodeState state, int pendingCost,
                                        Optional<PathFinder.Route> route, boolean detailed) {
        List<Component> out = new ArrayList<>();
        ChatFormatting nameColor = switch (n.type()) {
            case KEYSTONE -> ChatFormatting.GOLD;
            case NOTABLE -> ChatFormatting.LIGHT_PURPLE;
            case ROOT -> ChatFormatting.AQUA;
            case MINOR -> ChatFormatting.WHITE;
        };
        out.add(Component.translatable(n.nameKey()).withStyle(nameColor, ChatFormatting.BOLD));
        String sectorKey = g.sectors().stream().filter(sec -> sec.id().equals(n.sector())).map(Sector::nameKey).findFirst().orElse(n.sector());
        out.add(Component.translatable(sectorKey).append(" · ")
                .append(Component.translatable(key("type." + n.type().serializedName()))).withStyle(ChatFormatting.GRAY));
        boolean freeStart = n.isRoot() && s.startingRoot().isEmpty() && pending.stream().noneMatch(p -> g.nodes().get(p).isRoot());
        int cost = freeStart ? 0 : n.cost();
        boolean has = s.isAllocated(n.id());
        out.add(Component.translatable(key("cost"), has ? s.allocations().get(n.id()).paidCost() : cost)
                .append("   ").append(Component.translatable(key("rank"), has ? 1 : 0, n.maxRank()))
                .withStyle(ChatFormatting.YELLOW));
        out.add(Component.translatable(n.descriptionKey()).withStyle(ChatFormatting.GREEN));
        if (detailed && state != NodeState.ALLOCATED) {
            previews(n, out);
        }
        // requirements (independent of adjacency)
        for (String t : n.requirements().allOf()) {
            boolean ok = holds(g, s, pending, t);
            out.add(Component.translatable(key("requires_all"), term(g, t)).withStyle(ok ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        }
        if (!n.requirements().anyOf().isEmpty()) {
            MutableComponent any = Component.translatable(key("requires_any"));
            boolean ok = false;
            for (int i = 0; i < n.requirements().anyOf().size(); i++) {
                String t = n.requirements().anyOf().get(i);
                ok |= holds(g, s, pending, t);
                any.append(i == 0 ? " " : " / ").append(term(g, t));
            }
            out.add(any.withStyle(ok ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        }
        if (n.requirements().minimumSectorPoints() > 0) {
            out.add(Component.translatable(key("requires_sector_points"), n.requirements().minimumSectorPoints(),
                    Component.translatable(sectorKey)).withStyle(ChatFormatting.GRAY));
        }
        g.groupOf(n.id()).ifPresent(eg -> out.add(exclusive(g, s, pending, eg, n)));
        // why it cannot be selected
        switch (state) {
            case ALLOCATED -> {
                out.add(Component.translatable(key("status.allocated")).withStyle(ChatFormatting.GOLD));
                if (dev.nostalgiacraft.skills.client.ClientSkillState.economy().nodeRefundsEnabled()) {
                    out.add(Component.translatable(key(s.startingRoot().filter(n.id()::equals).isPresent()
                            ? "status.root_reset_only" : "status.refundable")).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
            case PENDING -> out.add(Component.translatable(key("status.pending")).withStyle(ChatFormatting.AQUA));
            case AVAILABLE -> {
                int left = s.availablePoints() - pendingCost;
                if (left < cost) {
                    out.add(Component.translatable(key("status.unaffordable"), cost, Math.max(0, left)).withStyle(ChatFormatting.RED));
                } else {
                    out.add(Component.translatable(key("status.available")).withStyle(ChatFormatting.GREEN));
                }
            }
            case CONFLICTING -> out.add(Component.translatable(key("status.conflict")).withStyle(ChatFormatting.RED));
            case LOCKED -> {
                AllocationService.Rejection why = AllocationService.eligibility(g, s, pending, n.id());
                Component reason = why == null ? Component.translatable(key("status.locked"))
                        : Component.translatable(why.reason().translationKey());
                out.add(Component.translatable(key("status.locked_reason"), reason).withStyle(ChatFormatting.RED));
                route.ifPresent(r -> {
                    if (r.valid()) {
                        out.add(Component.translatable(key("route.hint"), r.cost(), r.newNodes().size()).withStyle(ChatFormatting.YELLOW));
                    } else {
                        out.add(Component.translatable(key("route.unresolved"),
                                Component.translatable(r.unresolved().get().reason().translationKey())).withStyle(ChatFormatting.DARK_RED));
                    }
                });
            }
        }
        return out;
    }

    private static Component exclusive(SkillGraphSnapshot g, PlayerSkillState s, Collection<String> pending, ExclusiveGroup eg,
                                       SkillNodeDefinition self) {
        MutableComponent c = Component.translatable(key("exclusive"), Component.translatable(eg.nameKey()));
        for (String m : eg.members()) {
            if (m.equals(self.id())) {
                continue;
            }
            boolean active = s.isAllocated(m) || pending.contains(m);
            c.append(" ").append(Component.translatable(g.nodes().get(m).nameKey())
                    .withStyle(active ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        return c.withStyle(ChatFormatting.GOLD);
    }

    private static boolean holds(SkillGraphSnapshot g, PlayerSkillState s, Collection<String> pending, String term) {
        if (term.startsWith("#")) {
            String tag = term.substring(1);
            return s.allocations().keySet().stream().anyMatch(id -> g.node(id).map(d -> d.tags().contains(tag)).orElse(false))
                    || pending.stream().anyMatch(id -> g.node(id).map(d -> d.tags().contains(tag)).orElse(false));
        }
        return s.isAllocated(term) || pending.contains(term);
    }

    private static Component term(SkillGraphSnapshot g, String t) {
        if (t.startsWith("#")) {
            return Component.translatable(key("any_tagged"), t.substring(1));
        }
        return g.node(t).<Component>map(d -> Component.translatable(d.nameKey())).orElse(Component.literal(t));
    }

    /** Before -> after values for attribute effects, using the player's live (server-synced) attributes. */
    private static void previews(SkillNodeDefinition n, List<Component> out) {
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        for (SkillEffect e : n.effects()) {
            if (!(e instanceof SkillEffect.Attribute a)) {
                continue;
            }
            Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute>> h =
                    BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(a.attribute()));
            if (h.isEmpty()) {
                continue;
            }
            AttributeInstance inst = p.getAttribute(h.get());
            if (inst == null) {
                continue;
            }
            AttributeModifier.Operation op = switch (a.operation()) {
                case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            };
            double before = inst.getValue();
            double after = AttributeMath.withExtra(inst, List.of(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(NostalgiaSkills.MODID, "preview"), a.amount(), op)));
            boolean pct = a.attribute().startsWith(NostalgiaSkills.MODID + ":");
            out.add(Component.translatable(key("preview"), Component.translatable(h.get().value().getDescriptionId()),
                    fmt(before, pct), fmt(after, pct)).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    static String fmt(double v, boolean percent) {
        if (percent) {
            return String.format(Locale.ROOT, "%.1f%%", v * 100);
        }
        return String.format(Locale.ROOT, Math.abs(v - Math.rint(v)) < 1e-6 ? "%.0f" : "%.2f", v);
    }

    public static boolean isKeystone(SkillNodeDefinition n) {
        return n.type() == NodeType.KEYSTONE;
    }
}
