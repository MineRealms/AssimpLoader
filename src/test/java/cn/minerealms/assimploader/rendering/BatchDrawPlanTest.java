package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.config.GpuBatchPolicy;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static cn.minerealms.assimploader.config.GpuBatchPolicy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchDrawPlanTest {
    private final Object first = new Object(), second = new Object();

    @Test void groupsTexturesByValueAndMeshesByIdentityWhilePreservingSourceMapping() {
        var a0 = new BatchDrawPlan.Draw<>(first, new String("albedo"), 6, 0, 0);
        var b = new BatchDrawPlan.Draw<>(second, "albedo", 12, 6, 4);
        var a1 = new BatchDrawPlan.Draw<>(first, "other", 6, 0, 0);
        var a2 = new BatchDrawPlan.Draw<>(first, new String("albedo"), 6, 0, 0);
        for (var mode : List.of(INSTANCED, MDI)) {
            var plan = BatchDrawPlan.build(List.of(a0, b, a1, a2), mode, false, 4);
            assertEquals(List.of(0, 3, 1, 2), plan.order());
            assertEquals(List.of(new BatchDrawPlan.Command(6, 2, 0, 0, 0),
                    new BatchDrawPlan.Command(12, 1, 6, 4, 2),
                    new BatchDrawPlan.Command(6, 1, 0, 0, 3)), plan.commands());
            assertEquals(List.of(new BatchDrawPlan.TextureGroup<>("albedo", 0, 2),
                    new BatchDrawPlan.TextureGroup<>("other", 2, 1)), plan.textureGroups());
            assertEquals(40, plan.textureGroups().get(1).byteOffset());
            assertThrows(UnsupportedOperationException.class, () -> plan.order().add(0));
        }
    }

    @Test void equalButDistinctGeometryKeysDoNotMerge() {
        Object firstEqual = new String("mesh"), secondEqual = new String("mesh");
        var plan = BatchDrawPlan.build(List.of(draw(firstEqual), draw(secondEqual)), INSTANCED, false, 2);
        assertEquals(2, plan.commands().size());
        assertEquals(1, plan.commands().get(0).instanceCount());
        assertEquals(1, plan.commands().get(1).baseInstance());
    }

    @Test void hiZUsesIndependentCommandsOnlyInMdiAndWritesExactNativeLayout() {
        var source = List.of(draw(first), draw(second), draw(first));
        var plan = BatchDrawPlan.build(source, MDI, true, 3);
        assertEquals(List.of(0, 2, 1), plan.order());
        assertEquals(3, plan.commands().size());
        for (int index = 0; index < 3; index++) {
            assertEquals(1, plan.commands().get(index).instanceCount());
            assertEquals(index, plan.commands().get(index).baseInstance());
        }
        ByteBuffer bytes = ByteBuffer.allocate(plan.commandBytes() + 8).order(ByteOrder.BIG_ENDIAN);
        bytes.position(4);
        plan.writeCommands(bytes);
        assertEquals(64, bytes.position());
        assertEquals(ByteOrder.BIG_ENDIAN, bytes.order());
        bytes.order(ByteOrder.nativeOrder());
        for (int index = 0; index < 3; index++) {
            int offset = 4 + index * BatchDrawPlan.COMMAND_BYTES;
            assertEquals(6, bytes.getInt(offset));
            assertEquals(1, bytes.getInt(offset + 4));
            assertEquals(0, bytes.getInt(offset + 8));
            assertEquals(0, bytes.getInt(offset + 12));
            assertEquals(index, bytes.getInt(offset + 16));
        }
        var instanced = BatchDrawPlan.build(source, INSTANCED, true, 3);
        assertEquals(2, instanced.commands().size());
        assertEquals(2, instanced.commands().get(0).instanceCount());
    }

    @Test void capacitiesAndRangeChecksFailBeforeProducingPartialPlansOrWrites() {
        var draws = List.of(draw(first), draw(second));
        assertThrows(IllegalArgumentException.class, () -> BatchDrawPlan.build(draws, OFF, false, 2));
        assertThrows(IllegalArgumentException.class, () -> BatchDrawPlan.build(draws, INSTANCED, false, 1));
        assertThrows(IllegalArgumentException.class, () -> BatchDrawPlan.build(draws, INSTANCED, false, 0));
        assertThrows(IllegalArgumentException.class, () -> BatchDrawPlan.build(draws, INSTANCED, false, 16385));
        assertThrows(IllegalArgumentException.class, () -> new BatchDrawPlan.Draw<>(first, "albedo", 4, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BatchDrawPlan.Draw<>(first, "albedo", 3, Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> new BatchDrawPlan.Draw<>(first, "albedo", 3, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> BatchDrawPlan.build(List.of(draw(first),
                new BatchDrawPlan.Draw<>(first, "other", 9, 0, 0)), MDI, false, 2));
        assertThrows(IllegalArgumentException.class, () -> new BatchDrawPlan.Command(3, 2, 0, 0, 16383));
        assertThrows(IllegalArgumentException.class, () -> new BatchDrawPlan.TextureGroup<>("a", Integer.MAX_VALUE, 1));
        var plan = BatchDrawPlan.build(draws, INSTANCED, false, 2);
        ByteBuffer insufficient = ByteBuffer.allocate(plan.commandBytes() - 1);
        assertThrows(IllegalArgumentException.class, () -> plan.writeCommands(insufficient));
        assertEquals(0, insufficient.position());
    }

    @Test void maximumMdiIndependentCapacityHasNoIntegerOrByteOffsetOverflow() {
        List<BatchDrawPlan.Draw<String>> draws = new ArrayList<>();
        for (int index = 0; index < 16384; index++) draws.add(draw(first));
        var plan = BatchDrawPlan.build(draws, MDI, true, 16384);
        assertEquals(16384, plan.order().size());
        assertEquals(327680, plan.commandBytes());
        assertEquals(16383, plan.commands().get(16383).baseInstance());
        assertEquals(List.of(), BatchDrawPlan.build(List.<BatchDrawPlan.Draw<String>>of(), INSTANCED, false, 1).commands());
    }

    private static BatchDrawPlan.Draw<String> draw(Object geometry) {
        return new BatchDrawPlan.Draw<>(geometry, "albedo", 6, 0, 0);
    }
}
