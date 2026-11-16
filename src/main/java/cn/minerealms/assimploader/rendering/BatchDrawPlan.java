package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.config.GpuBatchPolicy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Opaque-only stable grouping; geometry keys use identity, texture keys use value equality. */
public final class BatchDrawPlan<T> {
    public static final int COMMAND_BYTES = 5 * Integer.BYTES;

    public record Draw<T>(Object geometry, T texture, int count, int firstIndex, int baseVertex) {
        public Draw {
            Objects.requireNonNull(geometry, "Geometry identity");
            Objects.requireNonNull(texture, "Texture key");
            if (count <= 0 || count % 3 != 0 || firstIndex < 0 || baseVertex < 0
                    || (long) firstIndex + count > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid triangle range in shared geometry arena");
        }
    }

    /** DrawElementsIndirectCommand: firstIndex is in indices, baseVertex in vertices. */
    public record Command(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        public Command {
            if (count <= 0 || count % 3 != 0 || instanceCount < 1 || firstIndex < 0 || baseVertex < 0
                    || baseInstance < 0 || (long) firstIndex + count > Integer.MAX_VALUE
                    || (long) baseInstance + instanceCount > 16384)
                throw new IllegalArgumentException("Invalid indirect command");
        }

        private void write(ByteBuffer target) {
            target.putInt(count).putInt(instanceCount).putInt(firstIndex).putInt(baseVertex).putInt(baseInstance);
        }
    }

    public record TextureGroup<T>(T texture, int firstCommand, int commandCount) {
        public TextureGroup {
            Objects.requireNonNull(texture, "Texture key");
            if (firstCommand < 0 || commandCount < 1 || (long) firstCommand + commandCount > 16384)
                throw new IllegalArgumentException("Invalid texture command range");
        }
        public long byteOffset() { return (long) firstCommand * COMMAND_BYTES; }
    }

    private final List<Integer> order;
    private final List<Command> commands;
    private final List<TextureGroup<T>> textureGroups;

    private BatchDrawPlan(List<Integer> order, List<Command> commands, List<TextureGroup<T>> textureGroups) {
        this.order = List.copyOf(order);
        this.commands = List.copyOf(commands);
        this.textureGroups = List.copyOf(textureGroups);
    }

    /** Ordered source indices used to upload instance records and per-instance palettes. */
    public List<Integer> order() { return order; }
    public List<Command> commands() { return commands; }
    public List<TextureGroup<T>> textureGroups() { return textureGroups; }
    public int commandBytes() { return Math.multiplyExact(commands.size(), COMMAND_BYTES); }

    public void writeCommands(ByteBuffer target) {
        Objects.requireNonNull(target, "Command target");
        if (target.remaining() < commandBytes()) throw new IllegalArgumentException("Insufficient command storage");
        ByteOrder previous = target.order();
        try {
            target.order(ByteOrder.nativeOrder());
            commands.forEach(command -> command.write(target));
        } finally {
            target.order(previous);
        }
    }

    public static <T> BatchDrawPlan<T> build(List<Draw<T>> draws, GpuBatchPolicy.Mode mode,
                                            boolean hiZEnabled, int maxInstances) {
        Objects.requireNonNull(draws, "Draws");
        Objects.requireNonNull(mode, "Mode");
        if (mode == GpuBatchPolicy.Mode.OFF) throw new IllegalArgumentException("OFF has no batch draw plan");
        if (maxInstances < 1 || maxInstances > 16384 || draws.size() > maxInstances)
            throw new IllegalArgumentException("Batch instance capacity exceeded");
        boolean individualCommands = mode == GpuBatchPolicy.Mode.MDI && hiZEnabled;
        Map<T, GeometryGroups> textures = new LinkedHashMap<>();
        Map<Object, Draw<T>> geometryRanges = new IdentityHashMap<>();
        for (int index = 0; index < draws.size(); index++) {
            Draw<T> draw = Objects.requireNonNull(draws.get(index), "Draw");
            Draw<T> previous = geometryRanges.putIfAbsent(draw.geometry(), draw);
            if (previous != null && (draw.count() != previous.count() || draw.firstIndex() != previous.firstIndex()
                    || draw.baseVertex() != previous.baseVertex()))
                throw new IllegalArgumentException("One geometry identity cannot name different arena ranges");
            textures.computeIfAbsent(draw.texture(), unused -> new GeometryGroups()).add(draw.geometry(), index);
        }
        List<Integer> order = new ArrayList<>(draws.size());
        List<Command> commands = new ArrayList<>();
        List<TextureGroup<T>> groups = new ArrayList<>(textures.size());
        for (var texture : textures.entrySet()) {
            int firstCommand = commands.size();
            for (List<Integer> indices : texture.getValue().ordered) {
                Draw<T> draw = draws.get(indices.get(0));
                int baseInstance = order.size();
                order.addAll(indices);
                if (individualCommands) {
                    for (int index = 0; index < indices.size(); index++)
                        commands.add(new Command(draw.count(), 1, draw.firstIndex(), draw.baseVertex(), baseInstance + index));
                } else commands.add(new Command(draw.count(), indices.size(), draw.firstIndex(), draw.baseVertex(), baseInstance));
            }
            groups.add(new TextureGroup<>(texture.getKey(), firstCommand, commands.size() - firstCommand));
        }
        return new BatchDrawPlan<>(order, commands, groups);
    }

    private static final class GeometryGroups {
        private final Map<Object, List<Integer>> identities = new IdentityHashMap<>();
        private final List<List<Integer>> ordered = new ArrayList<>();

        private void add(Object geometry, int sourceIndex) {
            List<Integer> indices = identities.get(geometry);
            if (indices == null) {
                indices = new ArrayList<>();
                identities.put(geometry, indices);
                ordered.add(indices);
            }
            indices.add(sourceIndex);
        }
    }
}
