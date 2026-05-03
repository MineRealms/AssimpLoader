package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages VAO/VBO/SSBO for Gaussian splat rendering.
 *
 * Uses a single quad (4 vertices, 6 indices) with instanced rendering.
 * Splat data is stored in SSBO binding 0, sorted indices in SSBO binding 1.
 */
public class GaussianSplatVBO {

    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 5000; // Log every 5 seconds

    private int vaoId;
    private int quadVboId;
    private int quadEboId;
    private int splatSsboId;
    private int orderSsboId;
    private GaussianSplatTBO splatTbo;
    private GaussianSplatTBO orderTbo;

    private int splatCount;
    private boolean uploaded = false;
    private boolean useSSBO = true;

    /**
     * Upload splat data to GPU
     *
     * @param data Gaussian splat data
     * @param useSSBO Whether to use SSBO (true) or fallback to TBO (false)
     */
    public void upload(GaussianSplatData data, boolean useSSBO) {
        if (uploaded) {
            delete();
        }

        this.splatCount = data.getSplatCount();
        this.useSSBO = useSSBO;

        // Create VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Create quad VBO (single quad for instancing)
        quadVboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVboId);

        // Quad vertices: position (2D) + texcoord
        float[] quadVertices = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 1.0f
        };

        // IMPORTANT: Must use direct buffer for OpenGL
        ByteBuffer quadByteBuffer = ByteBuffer.allocateDirect(quadVertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        FloatBuffer quadBuffer = quadByteBuffer.asFloatBuffer();
        quadBuffer.put(quadVertices);
        quadBuffer.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuffer, GL15.GL_STATIC_DRAW);

        // Position (location 0)
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);

        // TexCoord (location 1)
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        // Create quad EBO
        quadEboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadEboId);
        int[] quadIndices = {0, 1, 2, 2, 3, 0};

        // IMPORTANT: Must use direct buffer for OpenGL
        ByteBuffer indexByteBuffer = ByteBuffer.allocateDirect(quadIndices.length * Integer.BYTES)
                .order(ByteOrder.nativeOrder());
        IntBuffer indexBuffer = indexByteBuffer.asIntBuffer();
        indexBuffer.put(quadIndices);
        indexBuffer.flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);

        // Create SSBO for splat data
        if (useSSBO) {
            splatSsboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, splatSsboId);

            GaussianSplatBuffer packer = new GaussianSplatBuffer(data.getShDegree());
            FloatBuffer splatBuffer = packer.pack(data);

            // Debug: Check buffer validity
            if (splatBuffer == null) {
                throw new RuntimeException("Splat buffer is null!");
            }
            if (splatBuffer.capacity() == 0) {
                throw new RuntimeException("Splat buffer is empty!");
            }

            long bufferSizeBytes = (long) splatBuffer.remaining() * Float.BYTES;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogTime > LOG_INTERVAL_MS) {
                DebugManager.info(DebugCategory.RENDER, "Uploading SSBO: " + splatBuffer.remaining() +
                               " floats (" + (bufferSizeBytes / 1024 / 1024) + " MB)");
                DebugManager.info(DebugCategory.RENDER, "Buffer details: isDirect=" + splatBuffer.isDirect() +
                               ", position=" + splatBuffer.position() + ", limit=" + splatBuffer.limit() +
                               ", capacity=" + splatBuffer.capacity());
                lastLogTime = currentTime;
            }

            // Check if buffer is too large (> 100MB is suspicious)
            if (bufferSizeBytes > 100 * 1024 * 1024) {
                throw new RuntimeException("Splat buffer too large: " + (bufferSizeBytes / 1024 / 1024) + " MB");
            }

            // Ensure buffer position is at 0 for OpenGL
            splatBuffer.rewind();

            // Use GL15.glBufferData with explicit size in bytes
            long sizeInBytes = (long) splatBuffer.remaining() * Float.BYTES;

            // Clear any previous errors
            while (GL11.glGetError() != GL11.GL_NO_ERROR);

            DebugManager.info(DebugCategory.RENDER,
                "SSBO upload: allocating " + sizeInBytes + " bytes for " + splatCount + " splats");
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, sizeInBytes, GL15.GL_STATIC_DRAW);

            int error1 = GL11.glGetError();
            if (error1 != GL11.GL_NO_ERROR) {
                throw new RuntimeException("glBufferData failed: 0x" + Integer.toHexString(error1) +
                    " (size=" + sizeInBytes + " bytes)");
            }

            DebugManager.info(DebugCategory.RENDER,
                "SSBO upload: uploading data (buffer pos=" + splatBuffer.position() +
                ", limit=" + splatBuffer.limit() + ", remaining=" + splatBuffer.remaining() + ")");
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, splatBuffer);

            int error2 = GL11.glGetError();
            if (error2 != GL11.GL_NO_ERROR) {
                throw new RuntimeException("glBufferSubData failed: 0x" + Integer.toHexString(error2) +
                    " (offset=0, size=" + (splatBuffer.remaining() * Float.BYTES) + " bytes)");
            }

            // Check for OpenGL errors (throttled to prevent log spam)
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                String errorKey = "glBufferData_SSBO_" + error;
                if (cn.minerealms.assimploader.util.ErrorThrottler.shouldLog(errorKey, 5000)) {
                    DebugManager.error(DebugCategory.RENDER,
                        "OpenGL error after glBufferData (SSBO): 0x" + Integer.toHexString(error) +
                        " (further identical errors will be throttled)");
                }
            }

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, splatSsboId);

            // Create SSBO for sorted indices
            orderSsboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, orderSsboId);

            // IMPORTANT: Must use direct buffer for OpenGL
            ByteBuffer orderByteBuffer = ByteBuffer.allocateDirect(splatCount * Integer.BYTES)
                    .order(ByteOrder.nativeOrder());
            IntBuffer orderBuffer = orderByteBuffer.asIntBuffer();

            for (int i = 0; i < splatCount; i++) {
                orderBuffer.put(i);
            }
            orderBuffer.flip();

            // Use GL15.glBufferData with explicit size in bytes
            long orderSizeInBytes = (long) orderBuffer.remaining() * Integer.BYTES;

            // Clear any previous errors
            while (GL11.glGetError() != GL11.GL_NO_ERROR);

            DebugManager.info(DebugCategory.RENDER,
                "Order SSBO upload: allocating " + orderSizeInBytes + " bytes for " + splatCount + " indices");
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, orderSizeInBytes, GL15.GL_DYNAMIC_DRAW);

            int error3 = GL11.glGetError();
            if (error3 != GL11.GL_NO_ERROR) {
                throw new RuntimeException("glBufferData (order) failed: 0x" + Integer.toHexString(error3) +
                    " (size=" + orderSizeInBytes + " bytes)");
            }

            DebugManager.info(DebugCategory.RENDER,
                "Order SSBO upload: uploading data (buffer pos=" + orderBuffer.position() +
                ", limit=" + orderBuffer.limit() + ", remaining=" + orderBuffer.remaining() + ")");
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, orderBuffer);

            int error4 = GL11.glGetError();
            if (error4 != GL11.GL_NO_ERROR) {
                throw new RuntimeException("glBufferSubData (order) failed: 0x" + Integer.toHexString(error4) +
                    " (offset=0, size=" + (orderBuffer.remaining() * Integer.BYTES) + " bytes)");
            }

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, orderSsboId);

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        } else {
            // TBO fallback for Oculus compatibility
            GaussianSplatBuffer packer = new GaussianSplatBuffer(data.getShDegree());
            FloatBuffer splatBuffer = packer.pack(data);

            splatTbo = new GaussianSplatTBO();
            splatTbo.upload(data, splatBuffer);

            // Create order TBO (initially sequential)
            orderTbo = new GaussianSplatTBO();

            // IMPORTANT: Must use direct buffer for OpenGL
            ByteBuffer orderByteBuffer = ByteBuffer.allocateDirect(splatCount * Integer.BYTES)
                    .order(ByteOrder.nativeOrder());
            IntBuffer orderBuffer = orderByteBuffer.asIntBuffer();

            for (int i = 0; i < splatCount; i++) {
                orderBuffer.put(i);
            }
            orderBuffer.flip();

            // Convert int buffer to float buffer for TBO (TBO expects float data)
            ByteBuffer orderFloatByteBuffer = ByteBuffer.allocateDirect(splatCount * Float.BYTES)
                    .order(ByteOrder.nativeOrder());
            FloatBuffer orderFloatBuffer = orderFloatByteBuffer.asFloatBuffer();

            orderBuffer.rewind();
            while (orderBuffer.hasRemaining()) {
                orderFloatBuffer.put((float) orderBuffer.get());
            }
            orderFloatBuffer.flip();

            // Create dummy GaussianSplatData for order TBO
            GaussianSplatData orderData = new GaussianSplatData(new ArrayList<>(), 0);
            orderTbo.upload(orderData, orderFloatBuffer);

            DebugManager.info(DebugCategory.RENDER, "Using TBO fallback for Oculus compatibility");
        }

        // Unbind
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        uploaded = true;

        DebugManager.debug(DebugCategory.RENDER, "Uploaded Gaussian splat VBO: {} splats", splatCount);
    }

    /**
     * Upload sorted indices to GPU
     */
    public void uploadOrder(int[] sortedIndices) {
        if (!uploaded) {
            return;
        }

        if (useSSBO) {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, orderSsboId);

            // LOD system may return fewer indices than original splatCount
            // We need to match the SSBO size allocated in upload()
            int uploadCount = Math.min(sortedIndices.length, splatCount);

            // IMPORTANT: Must use direct buffer for OpenGL
            // Allocate buffer matching SSBO size to avoid GL_INVALID_VALUE
            ByteBuffer orderByteBuffer = ByteBuffer.allocateDirect(splatCount * Integer.BYTES)
                    .order(ByteOrder.nativeOrder());
            IntBuffer orderBuffer = orderByteBuffer.asIntBuffer();

            // Fill with sorted indices (may be less than splatCount due to LOD)
            orderBuffer.put(sortedIndices, 0, uploadCount);

            // Fill remaining slots with last valid index to avoid out-of-bounds
            for (int i = uploadCount; i < splatCount; i++) {
                orderBuffer.put(uploadCount > 0 ? sortedIndices[uploadCount - 1] : 0);
            }
            orderBuffer.flip();

            // Clear any previous errors
            while (GL11.glGetError() != GL11.GL_NO_ERROR);

            long now = System.currentTimeMillis();
            if (now - lastLogTime >= LOG_INTERVAL_MS) {
                DebugManager.info(DebugCategory.RENDER,
                    "uploadOrder: updating " + sortedIndices.length + "/" + splatCount + " indices, buffer state: pos=" +
                    orderBuffer.position() + ", limit=" + orderBuffer.limit() + ", remaining=" + orderBuffer.remaining());
                lastLogTime = now;
            }

            // Use ByteBuffer for glBufferSubData
            orderByteBuffer.position(0);
            orderByteBuffer.limit(splatCount * Integer.BYTES);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, orderByteBuffer);

            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                String errorKey = "glBufferSubData_order_" + error;
                if (cn.minerealms.assimploader.util.ErrorThrottler.shouldLog(errorKey, 5000)) {
                    DebugManager.error(DebugCategory.RENDER,
                        "glBufferSubData (uploadOrder) failed: 0x" + Integer.toHexString(error) +
                        " (offset=0, size=" + (orderBuffer.remaining() * Integer.BYTES) + " bytes, " +
                        "indices.length=" + sortedIndices.length + ") " +
                        "(further identical errors will be throttled)");
                }
            }

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        } else {
            // TBO fallback: convert int to float
            // IMPORTANT: Must use direct buffer for OpenGL
            ByteBuffer orderFloatByteBuffer = ByteBuffer.allocateDirect(sortedIndices.length * Float.BYTES)
                    .order(ByteOrder.nativeOrder());
            FloatBuffer orderFloatBuffer = orderFloatByteBuffer.asFloatBuffer();

            for (int idx : sortedIndices) {
                orderFloatBuffer.put((float) idx);
            }
            orderFloatBuffer.flip();

            GaussianSplatData orderData = new GaussianSplatData(new ArrayList<>(), 0);
            orderTbo.upload(orderData, orderFloatBuffer);
        }
    }

    /**
     * Bind VAO for rendering
     */
    public void bind() {
        if (!uploaded) {
            throw new IllegalStateException("VBO not uploaded");
        }
        GL30.glBindVertexArray(vaoId);

        if (useSSBO) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, splatSsboId);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, orderSsboId);
        } else {
            // Bind TBOs to texture units 0 and 1
            splatTbo.bind(0);
            orderTbo.bind(1);
        }
    }

    /**
     * Unbind VAO
     */
    public void unbind() {
        if (!useSSBO) {
            splatTbo.unbind(0);
            orderTbo.unbind(1);
        }
        GL30.glBindVertexArray(0);
    }

    /**
     * Render instanced splats
     */
    public void render() {
        if (!uploaded) {
            throw new IllegalStateException("VBO not uploaded");
        }
        bind();
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0, splatCount);
        unbind();
    }

    /**
     * Delete GPU resources
     */
    public void delete() {
        if (uploaded) {
            GL30.glDeleteVertexArrays(vaoId);
            GL15.glDeleteBuffers(quadVboId);
            GL15.glDeleteBuffers(quadEboId);
            if (useSSBO) {
                GL15.glDeleteBuffers(splatSsboId);
                GL15.glDeleteBuffers(orderSsboId);
            } else {
                if (splatTbo != null) {
                    splatTbo.cleanup();
                }
                if (orderTbo != null) {
                    orderTbo.cleanup();
                }
            }
            uploaded = false;
        }
    }

    public int getSplatCount() {
        return splatCount;
    }

    public boolean isUploaded() {
        return uploaded;
    }
}
