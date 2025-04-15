package cn.minerealms.iic.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerService {
    private static final ExecutorService scanExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ImprovedMobs-Scanner");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<Long, ScanData> scanCache = new ConcurrentHashMap<>();

    public static final int MAP_SIZE = 112;
    public static final int CHUNK_RANGE = 3;
    private static final int PIXELS_PER_CHUNK = 16;

    public static final int COLOR_WATER = 0xFF1E90FF;
    public static final int COLOR_GRASS = 0xFF228B22;
    public static final int COLOR_DIRT = 0xFF8B4513;
    public static final int COLOR_SAND = 0xFFF4A460;
    public static final int COLOR_STONE = 0xFF808080;
    public static final int COLOR_WOOD = 0xFFA0522D;
    public static final int COLOR_LEAVES = 0xFF006400;
    public static final int COLOR_UNKNOWN = 0xFF404040;
    public static final int COLOR_VOID = 0xFF000000;

    public static void requestScan(ServerPlayer player, ScanResultCallback callback) {
        ServerLevel level = player.serverLevel();
        int cx = player.chunkPosition().x;
        int cz = player.chunkPosition().z;
        long cacheKey = getCacheKey(level.dimension(), cx, cz);

        ScanData cached = scanCache.get(cacheKey);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }

        scanExecutor(() -> {
            ScanData result = performScan(level, cx, cz);
            scanCache.put(cacheKey, result);
            callback.onResult(result);
        });
    }

    private static void scanExecutor(Runnable task) {
        scanExecutor.execute(task);
    }

    private static ScanData performScan(ServerLevel level, int centerX, int centerZ) {
        int[] pixels = new int[MAP_SIZE * MAP_SIZE];
        long timestamp = System.currentTimeMillis();

        for (int dz = -CHUNK_RANGE; dz <= CHUNK_RANGE; dz++) {
            for (int dx = -CHUNK_RANGE; dx <= CHUNK_RANGE; dx++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;

                if (!level.hasChunkAt(chunkX, chunkZ)) {
                    fillChunkVoid(pixels, dx, dz);
                    continue;
                }

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                scanChunk(chunk, pixels, dx, dz);
            }
        }

        return new ScanData(pixels, timestamp, centerX, centerZ);
    }

    private static void fillChunkVoid(int[] pixels, int chunkDx, int chunkDz) {
        int offsetX = (chunkDx + CHUNK_RANGE) * PIXELS_PER_CHUNK;
        int offsetZ = (chunkDz + CHUNK_RANGE) * PIXELS_PER_CHUNK;

        for (int z = 0; z < PIXELS_PER_CHUNK; z++) {
            for (int x = 0; x < PIXELS_PER_CHUNK; x++) {
                int px = offsetX + x;
                int pz = offsetZ + z;
                pixels[pz * MAP_SIZE + px] = COLOR_VOID;
            }
        }
    }

    private static void scanChunk(LevelChunk chunk, int[] pixels, int chunkDx, int chunkDz) {
        int offsetX = (chunkDx + CHUNK_RANGE) * PIXELS_PER_CHUNK;
        int offsetZ = (chunkDz + CHUNK_RANGE) * PIXELS_PER_CHUNK;

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = (chunk.getPos().x << 4) | localX;
                int worldZ = (chunk.getPos().z << 4) | localZ;

                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(worldX, maxY, worldZ);
                BlockState state = chunk.getBlockState(pos);
                while (state.isAir() && pos.getY() > minY) {
                    pos.setY(pos.getY() - 1);
                    state = chunk.getBlockState(pos);
                }

                int color = getBlockColor(state.getBlock());

                int px = offsetX + localX;
                int pz = offsetZ + localZ;
                pixels[pz * MAP_SIZE + px] = color;
            }
        }
    }

    private static int getBlockColor(Block block) {
        if (block == Blocks.WATER) {
            return COLOR_WATER;
        }
        if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL) {
            return COLOR_GRASS;
        }
        if (block == Blocks.DIRT || block == Blocks.FARMLAND || block == Blocks.MUD) {
            return COLOR_DIRT;
        }
        if (block == Blocks.SAND || block == Blocks.RED_SAND) {
            return COLOR_SAND;
        }
        if (block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.GRANITE ||
                block == Blocks.DIORITE || block == Blocks.ANDESITE || block == Blocks.COBBLESTONE ||
                block == Blocks.COBBLED_DEEPSLATE) {
            return COLOR_STONE;
        }
        if (block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG || block == Blocks.BIRCH_LOG ||
                block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG ||
                block == Blocks.MANGROVE_LOG || block == Blocks.CHERRY_LOG || block == Blocks.STRIPPED_OAK_LOG ||
                block == Blocks.STRIPPED_SPRUCE_LOG || block == Blocks.STRIPPED_BIRCH_LOG ||
                block == Blocks.STRIPPED_JUNGLE_LOG || block == Blocks.STRIPPED_ACACIA_LOG ||
                block == Blocks.STRIPPED_DARK_OAK_LOG || block == Blocks.STRIPPED_MANGROVE_LOG ||
                block == Blocks.STRIPPED_CHERRY_LOG || block == Blocks.OAK_PLANKS ||
                block == Blocks.SPRUCE_PLANKS || block == Blocks.BIRCH_PLANKS || block == Blocks.JUNGLE_PLANKS ||
                block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS || block == Blocks.MANGROVE_PLANKS ||
                block == Blocks.CHERRY_PLANKS) {
            return COLOR_WOOD;
        }
        if (block == Blocks.OAK_LEAVES || block == Blocks.SPRUCE_LEAVES || block == Blocks.BIRCH_LEAVES ||
                block == Blocks.JUNGLE_LEAVES || block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES ||
                block == Blocks.MANGROVE_LEAVES || block == Blocks.CHERRY_LEAVES) {
            return COLOR_LEAVES;
        }
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
            return COLOR_VOID;
        }
        return COLOR_UNKNOWN;
    }

    private static long getCacheKey(ResourceKey<Level> dimension, int cx, int cz) {
        long hash = dimension.location().hashCode();
        hash = 31 * hash + cx;
        hash = 31 * hash + cz;
        return hash;
    }

    public interface ScanResultCallback {
        void onResult(ScanData data);
    }
}