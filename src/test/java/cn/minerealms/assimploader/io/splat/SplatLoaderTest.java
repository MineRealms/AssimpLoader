package cn.minerealms.assimploader.io.splat;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SplatLoaderTest {
    @Test
    void graphdecoAsciiKeepsOpacityLogitsWxyzRotationAndInterleavesShChannels() throws Exception {
        StringBuilder source = new StringBuilder("ply\nformat ascii 1.0\nelement vertex 1\n");
        for (String name : new String[]{"x", "y", "z", "f_dc_2", "f_dc_0", "f_dc_1"}) {
            source.append("property float ").append(name).append('\n');
        }
        for (int i = 0; i < 9; i++) source.append("property float f_rest_").append(i).append('\n');
        for (String name : new String[]{"opacity", "scale_0", "scale_1", "scale_2", "rot_0", "rot_1", "rot_2", "rot_3"}) {
            source.append("property float ").append(name).append('\n');
        }
        source.append("end_header\n1 2 3 30 10 20 1 2 3 4 5 6 7 8 9 0 0 0 0 1 0 0 0\n");
        var data = SplatLoader.load(new ByteArrayInputStream(source.toString().getBytes(StandardCharsets.US_ASCII)), "ply");
        assertEquals(1, data.getShDegree());
        var point = data.getSplats().get(0);
        assertEquals(0.5f, point.getOpacity());
        assertEquals(1, point.getRotation().w);
        assertEquals(0, point.getRotation().x);
        assertArrayEquals(new float[]{10, 20, 30, 1, 4, 7, 2, 5, 8, 3, 6, 9}, point.getSH());
    }

    @Test
    void binaryPlyHonorsEndianAndRejectsTruncationInsteadOfSilentlyDroppingVertices() throws Exception {
        for (ByteOrder order : new ByteOrder[]{ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
            String header = "ply\nformat binary_" + (order == ByteOrder.LITTLE_ENDIAN ? "little" : "big")
                    + "_endian 1.0\nelement vertex 1\nproperty float x\nproperty float y\nproperty float z\nend_header\n";
            byte[] prefix = header.getBytes(StandardCharsets.US_ASCII);
            ByteBuffer binary = ByteBuffer.allocate(prefix.length + 12).order(order).put(prefix).putFloat(1).putFloat(2).putFloat(3);
            var data = SplatLoader.load(new ByteArrayInputStream(binary.array()), "ply");
            assertEquals(3, data.getSplats().get(0).getPosition().z);
            assertThrows(IOException.class, () -> SplatLoader.load(new ByteArrayInputStream(
                    java.util.Arrays.copyOf(binary.array(), binary.capacity() - 1)), "ply"));
        }
    }

    @Test
    void malformedCustomCountsFailBeforeLargeAllocation() {
        assertThrows(IOException.class, () -> SplatLoader.load(new ByteArrayInputStream(new byte[0]), "splat"));
        byte[] malformed = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(Integer.MAX_VALUE).array();
        assertThrows(IOException.class, () -> SplatLoader.load(new ByteArrayInputStream(malformed), "splat"));
    }
}
