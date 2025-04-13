package io.github.flemmli97.improvedmobs.scanner;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

public class ScanData {
    public static final int MAP_SIZE = 112;
    public static final int CHUNK_RANGE = 3;

    private final int[] pixels;
    private final long scanTimestamp;
    private final int centerChunkX;
    private final int centerChunkZ;

    public ScanData(int[] pixels, long scanTimestamp, int centerChunkX, int centerChunkZ) {
        this.pixels = pixels;
        this.scanTimestamp = scanTimestamp;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
    }

    public int[] getPixels() {
        return this.pixels;
    }

    public long getScanTimestamp() {
        return this.scanTimestamp;
    }

    public int getCenterChunkX() {
        return this.centerChunkX;
    }

    public int getCenterChunkZ() {
        return this.centerChunkZ;
    }

    public static ScanData fromNetwork(FriendlyByteBuf buf) {
        int[] pixels = new int[MAP_SIZE * MAP_SIZE];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = buf.readInt();
        }
        long timestamp = buf.readLong();
        int cx = buf.readInt();
        int cz = buf.readInt();
        return new ScanData(pixels, timestamp, cx, cz);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        for (int pixel : this.pixels) {
            buf.writeInt(pixel);
        }
        buf.writeLong(this.scanTimestamp);
        buf.writeInt(this.centerChunkX);
        buf.writeInt(this.centerChunkZ);
    }
}