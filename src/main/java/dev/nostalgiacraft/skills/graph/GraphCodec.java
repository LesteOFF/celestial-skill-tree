package dev.nostalgiacraft.skills.graph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compact, deflated binary form of a validated snapshot for server-to-client sync. Sent once per join or graph
 * revision change (never per click). Decoding enforces the same size limits as JSON loading.
 */
public final class GraphCodec {
    public static final int FORMAT = 1;
    /** Hard cap on the compressed payload; well below the 1 MiB server-to-client limit. */
    public static final int MAX_BYTES = 900 * 1024;
    private static final int MAX_INFLATED = 16 * 1024 * 1024;

    private GraphCodec() {
    }

    public static byte[] encode(SkillGraphSnapshot g) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(FORMAT);
            out.writeUTF(g.treeId());
            out.writeInt(g.sectors().size());
            for (Sector s : g.sectors()) {
                out.writeUTF(s.id());
                out.writeUTF(s.root());
                out.writeUTF(s.nameKey());
            }
            out.writeInt(g.exclusiveGroups().size());
            for (ExclusiveGroup eg : g.exclusiveGroups().values()) {
                out.writeUTF(eg.id());
                out.writeUTF(eg.nameKey());
                out.writeInt(eg.maxActive());
                writeStrings(out, eg.members());
            }
            out.writeInt(g.nodes().size());
            for (SkillNodeDefinition n : g.nodes().values()) {
                out.writeUTF(n.id());
                out.writeUTF(n.tree());
                out.writeUTF(n.sector());
                out.writeByte(n.type().ordinal());
                out.writeDouble(n.position().x());
                out.writeDouble(n.position().y());
                out.writeUTF(n.nameKey());
                out.writeUTF(n.descriptionKey());
                out.writeUTF(n.icon());
                out.writeUTF(n.frame());
                out.writeInt(n.cost());
                out.writeInt(n.maxRank());
                writeStrings(out, n.requirements().allOf());
                writeStrings(out, n.requirements().anyOf());
                out.writeInt(n.requirements().minimumSectorPoints());
                out.writeInt(n.effects().size());
                for (SkillEffect e : n.effects()) {
                    writeEffect(out, e);
                }
                writeStrings(out, List.copyOf(n.tags()));
                out.writeUTF(n.cluster());
                out.writeUTF(n.exclusiveGroup().orElse(""));
            }
            out.writeInt(g.connections().size());
            for (SkillConnection c : g.connections()) {
                out.writeUTF(c.id());
                out.writeUTF(c.a());
                out.writeUTF(c.b());
                out.writeBoolean(c.directed());
                out.writeUTF(c.style());
                out.writeInt(c.points().size());
                for (Vec2 p : c.points()) {
                    out.writeDouble(p.x());
                    out.writeDouble(p.y());
                }
                out.writeBoolean(c.bridge());
                out.writeUTF(c.corridor().orElse(""));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static SkillGraphSnapshot decode(byte[] data) throws IOException {
        if (data.length > MAX_BYTES) {
            throw new IOException("graph payload too large: " + data.length);
        }
        try (DataInputStream in = new DataInputStream(new LimitedStream(new InflaterInputStream(new ByteArrayInputStream(data)), MAX_INFLATED))) {
            if (in.readInt() != FORMAT) {
                throw new IOException("unsupported graph format");
            }
            String tree = in.readUTF();
            List<Sector> sectors = new ArrayList<>();
            for (int i = count(in, 64); i > 0; i--) {
                sectors.add(new Sector(in.readUTF(), in.readUTF(), in.readUTF()));
            }
            List<ExclusiveGroup> groups = new ArrayList<>();
            for (int i = count(in, 1024); i > 0; i--) {
                groups.add(new ExclusiveGroup(in.readUTF(), in.readUTF(), in.readInt(), readStrings(in)));
            }
            List<SkillNodeDefinition> nodes = new ArrayList<>();
            for (int i = count(in, GraphParser.MAX_NODES); i > 0; i--) {
                String id = in.readUTF();
                String nt = in.readUTF();
                String sector = in.readUTF();
                int typeIdx = in.readByte();
                if (typeIdx < 0 || typeIdx >= NodeType.values().length) {
                    throw new IOException("bad node type");
                }
                Vec2 pos = new Vec2(in.readDouble(), in.readDouble());
                String name = in.readUTF();
                String desc = in.readUTF();
                String icon = in.readUTF();
                String frame = in.readUTF();
                int cost = in.readInt();
                int maxRank = in.readInt();
                Requirements req = new Requirements(readStrings(in), readStrings(in), in.readInt());
                List<SkillEffect> effects = new ArrayList<>();
                for (int e = count(in, GraphParser.MAX_EFFECTS); e > 0; e--) {
                    effects.add(readEffect(in));
                }
                Set<String> tags = Collections.unmodifiableSet(new TreeSet<>(readStrings(in)));
                String cluster = in.readUTF();
                String group = in.readUTF();
                nodes.add(new SkillNodeDefinition(id, nt, sector, NodeType.values()[typeIdx], pos, name, desc, icon,
                        frame, cost, maxRank, req, List.copyOf(effects), tags, cluster,
                        group.isEmpty() ? Optional.empty() : Optional.of(group)));
            }
            List<SkillConnection> conns = new ArrayList<>();
            for (int i = count(in, GraphParser.MAX_CONNECTIONS); i > 0; i--) {
                String id = in.readUTF();
                String a = in.readUTF();
                String b = in.readUTF();
                boolean directed = in.readBoolean();
                String style = in.readUTF();
                List<Vec2> pts = new ArrayList<>();
                for (int p = count(in, GraphParser.MAX_POINTS); p > 0; p--) {
                    pts.add(new Vec2(in.readDouble(), in.readDouble()));
                }
                boolean bridge = in.readBoolean();
                String corridor = in.readUTF();
                conns.add(new SkillConnection(id, a, b, directed, style, List.copyOf(pts), bridge,
                        corridor.isEmpty() ? Optional.empty() : Optional.of(corridor)));
            }
            return new SkillGraphSnapshot(tree, nodes, conns, sectors, groups);
        }
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) {
            throw new IOException("collection size out of range: " + n);
        }
        return n;
    }

    private static void writeStrings(DataOutputStream out, List<String> list) throws IOException {
        out.writeInt(list.size());
        for (String s : list) {
            out.writeUTF(s);
        }
    }

    private static List<String> readStrings(DataInputStream in) throws IOException {
        List<String> out = new ArrayList<>();
        for (int i = count(in, 4096); i > 0; i--) {
            out.add(in.readUTF());
        }
        return List.copyOf(out);
    }

    private static void writeEffect(DataOutputStream out, SkillEffect e) throws IOException {
        out.writeUTF(e.type());
        switch (e) {
            case SkillEffect.Attribute a -> {
                out.writeUTF(a.attribute());
                out.writeUTF(a.operation());
                out.writeDouble(a.amount());
            }
            case SkillEffect.ConditionalAttribute a -> {
                out.writeUTF(a.attribute());
                out.writeUTF(a.operation());
                out.writeDouble(a.amount());
                writeCondition(out, a.condition());
            }
            case SkillEffect.DamageModifier d -> {
                out.writeBoolean(d.dealt());
                out.writeUTF(d.scope());
                out.writeDouble(d.amount());
                out.writeBoolean(d.condition().isPresent());
                if (d.condition().isPresent()) {
                    writeCondition(out, d.condition().get());
                }
            }
            case SkillEffect.Trigger t -> {
                out.writeUTF(t.trigger());
                out.writeInt(t.cooldownTicks());
                out.writeUTF(t.action().type());
                out.writeDouble(t.action().amount());
                out.writeUTF(t.action().effect());
                out.writeInt(t.action().durationTicks());
                out.writeInt(t.action().amplifier());
            }
        }
    }

    private static SkillEffect readEffect(DataInputStream in) throws IOException {
        String type = in.readUTF();
        return switch (type) {
            case "attribute" -> new SkillEffect.Attribute(in.readUTF(), in.readUTF(), in.readDouble());
            case "conditional_attribute" -> new SkillEffect.ConditionalAttribute(in.readUTF(), in.readUTF(),
                    in.readDouble(), readCondition(in));
            case "damage_modifier" -> {
                boolean dealt = in.readBoolean();
                String scope = in.readUTF();
                double amount = in.readDouble();
                Optional<Condition> c = in.readBoolean() ? Optional.of(readCondition(in)) : Optional.empty();
                yield new SkillEffect.DamageModifier(dealt, scope, amount, c);
            }
            case "trigger" -> {
                String trig = in.readUTF();
                int cd = in.readInt();
                yield new SkillEffect.Trigger(trig, new SkillEffect.Action(in.readUTF(), in.readDouble(), in.readUTF(),
                        in.readInt(), in.readInt()), cd);
            }
            default -> throw new IOException("unknown effect type " + type);
        };
    }

    private static void writeCondition(DataOutputStream out, Condition c) throws IOException {
        out.writeUTF(c.type());
        out.writeInt(c.params().size());
        for (Map.Entry<String, Object> p : new TreeMap<>(c.params()).entrySet()) {
            out.writeUTF(p.getKey());
            if (p.getValue() instanceof Double d) {
                out.writeBoolean(true);
                out.writeDouble(d);
            } else {
                out.writeBoolean(false);
                out.writeUTF(String.valueOf(p.getValue()));
            }
        }
    }

    private static Condition readCondition(DataInputStream in) throws IOException {
        String type = in.readUTF();
        Map<String, Object> params = new TreeMap<>();
        for (int i = count(in, 16); i > 0; i--) {
            String k = in.readUTF();
            params.put(k, in.readBoolean() ? (Object) in.readDouble() : in.readUTF());
        }
        return new Condition(type, Collections.unmodifiableMap(params));
    }

    /** Guards against decompression bombs. */
    private static final class LimitedStream extends java.io.FilterInputStream {
        private long left;

        LimitedStream(java.io.InputStream in, long limit) {
            super(in);
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) {
                throw new IOException("inflated graph exceeds limit");
            }
            left--;
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) {
                throw new IOException("inflated graph exceeds limit");
            }
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }
    }
}
