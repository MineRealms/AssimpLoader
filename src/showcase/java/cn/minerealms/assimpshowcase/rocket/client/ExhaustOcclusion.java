package cn.minerealms.assimpshowcase.rocket.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.lwjgl.opengl.GL33C.*;

/** Half-block occupancy from loaded collision shapes, for local-light visibility rather than scene GI. */
final class ExhaustOcclusion {
    private static final int BLOCKS = 32, CELLS = BLOCKS * 2;
    private static final ByteBuffer DATA = BufferUtils.createByteBuffer(CELLS * CELLS * CELLS);
    private static int texture, hash;
    private static long lastTick = Long.MIN_VALUE;
    private static BlockPos minimum;
    private static boolean ready;
    private static final MethodHandle OCULUS_SAMPLER = findOculusSampler();

    private ExhaustOcclusion() {}

    private static MethodHandle findOculusSampler() {
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.gl.IrisRenderSystem", false,
                    ExhaustOcclusion.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(type, "bindSamplerToUnit",
                    MethodType.methodType(void.class, int.class, int.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported Oculus sampler API", error);
        }
    }

    private static void bindSampler(int unit) {
        if (OCULUS_SAMPLER == null) { glBindSampler(unit, 0); return; }
        try {
            OCULUS_SAMPLER.invokeExact(unit, 0);
        } catch (Throwable error) {
            throw new IllegalStateException("Could not bind exhaust sampler through Oculus", error);
        }
    }

    static void update(Vec3 nozzle) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long tick = mc.level.getGameTime();
        BlockPos corner = BlockPos.containing(nozzle).offset(-16, -20, -16);
        if (corner.equals(minimum) && tick >= lastTick && tick - lastTick < 20) return;
        lastTick = tick;
        int newHash = 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < BLOCKS; z++) for (int y = 0; y < BLOCKS; y++) for (int x = 0; x < BLOCKS; x++) {
            pos.set(corner.getX() + x, corner.getY() + y, corner.getZ() + z);
            newHash = 31 * newHash + (mc.level.hasChunkAt(pos) ? mc.level.getBlockState(pos).hashCode() : 0);
        }
        if (ready && corner.equals(minimum) && newHash == hash) return;
        hash = newHash; minimum = corner;
        for (int i = 0; i < DATA.capacity(); i++) DATA.put(i, (byte) 0);
        for (int z = 0; z < BLOCKS; z++) for (int y = 0; y < BLOCKS; y++) for (int x = 0; x < BLOCKS; x++) {
            pos.set(corner.getX() + x, corner.getY() + y, corner.getZ() + z);
            if (!mc.level.hasChunkAt(pos)) continue;
            var state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(mc.level, pos);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                for (int dz = 0; dz < 2; dz++) for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                    AABB cell = new AABB(dx * .5, dy * .5, dz * .5, (dx + 1) * .5, (dy + 1) * .5, (dz + 1) * .5);
                    if (box.intersects(cell)) DATA.put((x * 2 + dx) + CELLS * ((y * 2 + dy) + CELLS * (z * 2 + dz)), (byte) 255);
                }
            }
        }
        int active = glGetInteger(GL_ACTIVE_TEXTURE), binding = glGetInteger(GL_TEXTURE_BINDING_3D);
        int[] unpackNames = {GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT,
                GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_IMAGES, GL_UNPACK_SWAP_BYTES, GL_UNPACK_LSB_FIRST};
        int[] unpack = new int[unpackNames.length];
        for (int i = 0; i < unpack.length; i++) unpack[i] = glGetInteger(unpackNames[i]);
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            if (texture == 0) texture = glGenTextures();
            glBindTexture(GL_TEXTURE_3D, texture); glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            for (int name : unpackNames) glPixelStorei(name, name == GL_UNPACK_ALIGNMENT ? 1 : 0);
            DATA.position(0); DATA.limit(DATA.capacity());
            glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, CELLS, CELLS, CELLS, 0, GL_RED, GL_UNSIGNED_BYTE, DATA);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
            ready = true;
        } finally {
            for (int i = 0; i < unpack.length; i++) glPixelStorei(unpackNames[i], unpack[i]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            glBindTexture(GL_TEXTURE_3D, binding); glActiveTexture(active);
        }
    }

    static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(ExhaustOcclusion::clear); return; }
        if (texture != 0) glDeleteTextures(texture);
        texture = 0; ready = false; minimum = null; lastTick = Long.MIN_VALUE;
    }

    static final class Binding {
        private final int sampler, origin, valid, unit;
        Binding(int program) {
            sampler = glGetUniformLocation(program, "assimpExhaustOcclusion");
            origin = glGetUniformLocation(program, "assimpExhaustOcclusionOrigin");
            valid = glGetUniformLocation(program, "assimpExhaustOcclusionValid");
            unit = sampler < 0 ? -1 : freeSampler(program);
        }

        void upload() {
            if (valid >= 0) glUniform1i(valid, ready && unit >= 0 ? 1 : 0);
            if (sampler < 0 || unit < 0) return;
            // Active sampler types must never alias, including when the shader's dynamic branch is disabled.
            glUniform1i(sampler, unit);
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0 + unit); bindSampler(unit);
            glBindTexture(GL_TEXTURE_3D, ready ? texture : 0); glActiveTexture(active);
            if (ready) {
                Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                glUniform4f(origin, (float) (minimum.getX() - camera.x), (float) (minimum.getY() - camera.y),
                        (float) (minimum.getZ() - camera.z), 0.5f);
            }
        }

        private static int freeSampler(int program) {
            BitSet used = new BitSet();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var size = stack.mallocInt(1); var type = stack.mallocInt(1);
                for (int i = 0; i < glGetProgrami(program, GL_ACTIVE_UNIFORMS); i++) {
                    String name = glGetActiveUniform(program, i, size, type);
                    if (name.equals("assimpExhaustOcclusion")) continue;
                    int t = type.get(0);
                    if (isSampler(t)) {
                        for (int element = 0; element < size.get(0); element++) {
                            String indexed = name.contains("[0]") ? name.replace("[0]", "[" + element + "]") : name;
                            int location = glGetUniformLocation(program, indexed);
                            if (location >= 0) used.set(glGetUniformi(program, location));
                        }
                    }
                }
            }
            for (int i = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS) - 1; i >= 0; i--) if (!used.get(i)) return i;
            return -1;
        }

        private static boolean isSampler(int type) {
            return switch (type) {
                case GL_SAMPLER_1D, GL_SAMPLER_2D, GL_SAMPLER_3D, GL_SAMPLER_CUBE,
                        GL_SAMPLER_1D_SHADOW, GL_SAMPLER_2D_SHADOW, GL_SAMPLER_CUBE_SHADOW,
                        GL_SAMPLER_2D_RECT, GL_SAMPLER_2D_RECT_SHADOW, GL_SAMPLER_1D_ARRAY,
                        GL_SAMPLER_2D_ARRAY, GL_SAMPLER_1D_ARRAY_SHADOW, GL_SAMPLER_2D_ARRAY_SHADOW,
                        GL_SAMPLER_BUFFER, GL_INT_SAMPLER_1D, GL_INT_SAMPLER_2D, GL_INT_SAMPLER_3D,
                        GL_INT_SAMPLER_CUBE, GL_INT_SAMPLER_1D_ARRAY, GL_INT_SAMPLER_2D_ARRAY,
                        GL_INT_SAMPLER_2D_RECT, GL_INT_SAMPLER_BUFFER, GL_UNSIGNED_INT_SAMPLER_1D,
                        GL_UNSIGNED_INT_SAMPLER_2D, GL_UNSIGNED_INT_SAMPLER_3D, GL_UNSIGNED_INT_SAMPLER_CUBE,
                        GL_UNSIGNED_INT_SAMPLER_1D_ARRAY, GL_UNSIGNED_INT_SAMPLER_2D_ARRAY,
                        GL_UNSIGNED_INT_SAMPLER_2D_RECT, GL_UNSIGNED_INT_SAMPLER_BUFFER,
                        GL_SAMPLER_2D_MULTISAMPLE, GL_SAMPLER_2D_MULTISAMPLE_ARRAY,
                        GL_INT_SAMPLER_2D_MULTISAMPLE, GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY,
                        GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE, GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY -> true;
                default -> false;
            };
        }
    }
}
