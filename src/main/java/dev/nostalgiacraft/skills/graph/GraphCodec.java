package dev.nostalgiacraft.skills.graph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class GraphCodec {
    public static final int FORMAT = 3;
    public static final int MAX_BYTES = 900 * 1024;
    private static final int MAX_INFLATED = 16 * 1024 * 1024;
    private static final int MAX_SECTORS = 64;
    private static final int MAX_GROUPS = 1024;
    private static final int MAX_STRINGS = 4096;
    private static final int MAX_CONDITION_PARAMETERS = 16;

    private interface Writer<T> {
        void write(DataOutputStream out, T value) throws IOException;
    }

    private interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }

    private GraphCodec() {
    }

    public static byte[] encode(SkillGraphSnapshot graph) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(FORMAT);
            out.writeUTF(graph.treeId());
            writeAll(out, graph.sectors(), GraphCodec::writeSector);
            writeAll(out, graph.exclusiveGroups().values(), GraphCodec::writeGroup);
            writeAll(out, graph.nodes().values(), GraphCodec::writeNode);
            writeAll(out, graph.connections(), GraphCodec::writeConnection);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static SkillGraphSnapshot decode(byte[] data) throws IOException {
        if (data.length > MAX_BYTES) {
            throw new IOException("graph payload too large: " + data.length);
        }
        InputStream inflated = new LimitedStream(new InflaterInputStream(new ByteArrayInputStream(data)), MAX_INFLATED);
        try (DataInputStream in = new DataInputStream(inflated)) {
            if (in.readInt() != FORMAT) {
                throw new IOException("unsupported graph format");
            }
            String treeId = in.readUTF();
            List<Sector> sectors = readAll(in, MAX_SECTORS, GraphCodec::readSector);
            List<ExclusiveGroup> groups = readAll(in, MAX_GROUPS, GraphCodec::readGroup);
            List<SkillNodeDefinition> nodes = readAll(in, GraphParser.MAX_NODES, GraphCodec::readNode);
            List<SkillConnection> connections = readAll(in, GraphParser.MAX_CONNECTIONS, GraphCodec::readConnection);
            return new SkillGraphSnapshot(treeId, nodes, connections, sectors, groups);
        }
    }

    private static <T> void writeAll(DataOutputStream out, Collection<T> values, Writer<T> writer) throws IOException {
        out.writeInt(values.size());
        for (T value : values) {
            writer.write(out, value);
        }
    }

    private static <T> List<T> readAll(DataInputStream in, int max, Reader<T> reader) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > max) {
            throw new IOException("collection size out of range: " + size);
        }
        List<T> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add(reader.read(in));
        }
        return List.copyOf(values);
    }

    private static void writeStrings(DataOutputStream out, Collection<String> values) throws IOException {
        writeAll(out, values, DataOutputStream::writeUTF);
    }

    private static List<String> readStrings(DataInputStream in) throws IOException {
        return readAll(in, MAX_STRINGS, input -> input.readUTF());
    }

    private static Optional<String> optional(String value) {
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static void writeSector(DataOutputStream out, Sector sector) throws IOException {
        out.writeUTF(sector.id());
        out.writeUTF(sector.root());
        out.writeUTF(sector.nameKey());
    }

    private static Sector readSector(DataInputStream in) throws IOException {
        return new Sector(in.readUTF(), in.readUTF(), in.readUTF());
    }

    private static void writeGroup(DataOutputStream out, ExclusiveGroup group) throws IOException {
        out.writeUTF(group.id());
        out.writeUTF(group.nameKey());
        out.writeInt(group.maxActive());
        writeStrings(out, group.members());
    }

    private static ExclusiveGroup readGroup(DataInputStream in) throws IOException {
        return new ExclusiveGroup(in.readUTF(), in.readUTF(), in.readInt(), readStrings(in));
    }

    private static void writeNode(DataOutputStream out, SkillNodeDefinition node) throws IOException {
        out.writeUTF(node.id());
        out.writeUTF(node.tree());
        out.writeUTF(node.sector());
        out.writeByte(node.type().ordinal());
        writePoint(out, node.position());
        out.writeUTF(node.nameKey());
        out.writeUTF(node.descriptionKey());
        out.writeUTF(node.icon());
        out.writeUTF(node.frame());
        out.writeInt(node.cost());
        out.writeInt(node.maxRank());
        writeStrings(out, node.requirements().allOf());
        writeStrings(out, node.requirements().anyOf());
        out.writeInt(node.requirements().minimumSectorPoints());
        writeAll(out, node.effects(), GraphCodec::writeEffect);
        writeStrings(out, node.tags());
        out.writeUTF(node.cluster());
        out.writeUTF(node.exclusiveGroup().orElse(""));
        out.writeDouble(node.traits().neighbourMultiplier());
        out.writeUTF(node.traits().hiddenUntil().orElse(""));
        out.writeBoolean(node.traits().toggleable());
    }

    private static SkillNodeDefinition readNode(DataInputStream in) throws IOException {
        String id = in.readUTF();
        String tree = in.readUTF();
        String sector = in.readUTF();
        NodeType type = readNodeType(in);
        Vec2 position = readPoint(in);
        String name = in.readUTF();
        String description = in.readUTF();
        String icon = in.readUTF();
        String frame = in.readUTF();
        int cost = in.readInt();
        int maxRank = in.readInt();
        Requirements requirements = new Requirements(readStrings(in), readStrings(in), in.readInt());
        List<SkillEffect> effects = readAll(in, GraphParser.MAX_EFFECTS, GraphCodec::readEffect);
        Set<String> tags = Collections.unmodifiableSet(new TreeSet<>(readStrings(in)));
        String cluster = in.readUTF();
        Optional<String> exclusiveGroup = optional(in.readUTF());
        NodeTraits traits = new NodeTraits(in.readDouble(), optional(in.readUTF()), in.readBoolean());
        return new SkillNodeDefinition(id, tree, sector, type, position, name, description, icon, frame, cost, maxRank,
                requirements, effects, tags, cluster, exclusiveGroup, traits);
    }

    private static NodeType readNodeType(DataInputStream in) throws IOException {
        int ordinal = in.readByte();
        if (ordinal < 0 || ordinal >= NodeType.values().length) {
            throw new IOException("bad node type");
        }
        return NodeType.values()[ordinal];
    }

    private static void writePoint(DataOutputStream out, Vec2 point) throws IOException {
        out.writeDouble(point.x());
        out.writeDouble(point.y());
    }

    private static Vec2 readPoint(DataInputStream in) throws IOException {
        return new Vec2(in.readDouble(), in.readDouble());
    }

    private static void writeConnection(DataOutputStream out, SkillConnection connection) throws IOException {
        out.writeUTF(connection.id());
        out.writeUTF(connection.a());
        out.writeUTF(connection.b());
        out.writeBoolean(connection.directed());
        out.writeUTF(connection.style());
        writeAll(out, connection.points(), GraphCodec::writePoint);
        out.writeBoolean(connection.bridge());
        out.writeUTF(connection.corridor().orElse(""));
    }

    private static SkillConnection readConnection(DataInputStream in) throws IOException {
        String id = in.readUTF();
        String a = in.readUTF();
        String b = in.readUTF();
        boolean directed = in.readBoolean();
        String style = in.readUTF();
        List<Vec2> points = readAll(in, GraphParser.MAX_POINTS, GraphCodec::readPoint);
        boolean bridge = in.readBoolean();
        return new SkillConnection(id, a, b, directed, style, points, bridge, optional(in.readUTF()));
    }

    private static void writeEffect(DataOutputStream out, SkillEffect effect) throws IOException {
        out.writeUTF(effect.type());
        switch (effect) {
            case SkillEffect.Attribute attribute -> {
                out.writeUTF(attribute.attribute());
                out.writeUTF(attribute.operation());
                out.writeDouble(attribute.amount());
            }
            case SkillEffect.ConditionalAttribute conditional -> {
                out.writeUTF(conditional.attribute());
                out.writeUTF(conditional.operation());
                out.writeDouble(conditional.amount());
                writeCondition(out, conditional.condition());
            }
            case SkillEffect.DamageModifier damage -> {
                out.writeBoolean(damage.dealt());
                out.writeUTF(damage.scope());
                out.writeDouble(damage.amount());
                out.writeBoolean(damage.condition().isPresent());
                if (damage.condition().isPresent()) {
                    writeCondition(out, damage.condition().get());
                }
            }
            case SkillEffect.Trigger trigger -> {
                out.writeUTF(trigger.trigger());
                out.writeInt(trigger.cooldownTicks());
                writeAction(out, trigger.action());
                out.writeBoolean(trigger.condition().isPresent());
                if (trigger.condition().isPresent()) {
                    writeCondition(out, trigger.condition().get());
                }
            }
        }
    }

    private static SkillEffect readEffect(DataInputStream in) throws IOException {
        String type = in.readUTF();
        return switch (type) {
            case "attribute" -> new SkillEffect.Attribute(in.readUTF(), in.readUTF(), in.readDouble());
            case "conditional_attribute" -> new SkillEffect.ConditionalAttribute(in.readUTF(), in.readUTF(),
                    in.readDouble(), readCondition(in));
            case "damage_modifier" -> readDamageModifier(in);
            case "trigger" -> readTrigger(in);
            default -> throw new IOException("unknown effect type " + type);
        };
    }

    private static SkillEffect readDamageModifier(DataInputStream in) throws IOException {
        boolean dealt = in.readBoolean();
        String scope = in.readUTF();
        double amount = in.readDouble();
        Optional<Condition> condition = in.readBoolean() ? Optional.of(readCondition(in)) : Optional.empty();
        return new SkillEffect.DamageModifier(dealt, scope, amount, condition);
    }

    private static SkillEffect readTrigger(DataInputStream in) throws IOException {
        String trigger = in.readUTF();
        int cooldownTicks = in.readInt();
        SkillEffect.Action action = readAction(in);
        Optional<Condition> condition = in.readBoolean() ? Optional.of(readCondition(in)) : Optional.empty();
        return new SkillEffect.Trigger(trigger, action, cooldownTicks, condition);
    }

    private static void writeAction(DataOutputStream out, SkillEffect.Action action) throws IOException {
        out.writeUTF(action.type());
        out.writeDouble(action.amount());
        out.writeUTF(action.effect());
        out.writeInt(action.durationTicks());
        out.writeInt(action.amplifier());
    }

    private static SkillEffect.Action readAction(DataInputStream in) throws IOException {
        return new SkillEffect.Action(in.readUTF(), in.readDouble(), in.readUTF(), in.readInt(), in.readInt());
    }

    private static void writeCondition(DataOutputStream out, Condition condition) throws IOException {
        out.writeUTF(condition.type());
        out.writeInt(condition.params().size());
        for (Map.Entry<String, Object> parameter : new TreeMap<>(condition.params()).entrySet()) {
            out.writeUTF(parameter.getKey());
            if (parameter.getValue() instanceof Double number) {
                out.writeBoolean(true);
                out.writeDouble(number);
            } else {
                out.writeBoolean(false);
                out.writeUTF(String.valueOf(parameter.getValue()));
            }
        }
    }

    private static Condition readCondition(DataInputStream in) throws IOException {
        String type = in.readUTF();
        int size = in.readInt();
        if (size < 0 || size > MAX_CONDITION_PARAMETERS) {
            throw new IOException("collection size out of range: " + size);
        }
        Map<String, Object> parameters = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            parameters.put(key, in.readBoolean() ? (Object) in.readDouble() : in.readUTF());
        }
        return new Condition(type, Collections.unmodifiableMap(parameters));
    }

    private static final class LimitedStream extends FilterInputStream {
        private long remaining;

        LimitedStream(InputStream inputStream, long limit) {
            super(inputStream);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            requireRemaining();
            remaining--;
            return super.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            requireRemaining();
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        private void requireRemaining() throws IOException {
            if (remaining <= 0) {
                throw new IOException("inflated graph exceeds limit");
            }
        }
    }
}
