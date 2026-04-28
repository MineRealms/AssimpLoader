package cn.minerealms.assimploader.io.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads Gaussian splat data from various file formats.
 *
 * Supports:
 * - .ply (PLY format) - Basic implementation
 * - .splat (Gsplat binary format)
 *
 * Note: This is a simplified loader. For full PLY/SPZ support, use external tools to convert to .splat format.
 */
public class SplatLoader {

    /**
     * Load splat data from a resource location
     *
     * @param location Resource location (e.g., "assimploader:splats/example.ply")
     * @param resourceManager Minecraft resource manager
     * @return Loaded splat data
     * @throws IOException If loading fails
     */
    public static GaussianSplatData load(ResourceLocation location, ResourceManager resourceManager) throws IOException {
        String path = location.getPath();
        String extension = getExtension(path);

        var resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            throw new IOException("Resource not found: " + location);
        }

        try (InputStream stream = resource.get().open()) {
            return load(stream, extension);
        }
    }

    /**
     * Load splat data from an input stream
     *
     * @param stream Input stream
     * @param extension File extension (ply, splat)
     * @return Loaded splat data
     * @throws IOException If loading fails
     */
    public static GaussianSplatData load(InputStream stream, String extension) throws IOException {
        return switch (extension.toLowerCase()) {
            case "splat" -> loadSplat(stream);
            case "ply" -> loadPly(stream);
            default -> throw new IllegalArgumentException("Unsupported splat format: " + extension);
        };
    }

    /**
     * Load .splat binary format (simple binary format)
     * Format: [count:int][splat1][splat2]...
     * Each splat: pos(3f) rot(4f) scale(3f) opacity(1f) sh(N*3f)
     */
    private static GaussianSplatData loadSplat(InputStream stream) throws IOException {
        byte[] data = stream.readAllBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int count = buffer.getInt();
        List<GaussianSplat> splats = new ArrayList<>(count);

        // Assume SH degree 3 (48 coefficients = 16 * 3)
        int shDegree = 3;
        int shDim = (shDegree + 1) * (shDegree + 1); // 16

        for (int i = 0; i < count; i++) {
            // Position
            Vector3f position = new Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat());

            // Rotation (quaternion: w, x, y, z)
            Quaternionf rotation = new Quaternionf(
                buffer.getFloat(), buffer.getFloat(), buffer.getFloat(), buffer.getFloat()
            );

            // Log-space scale
            Vector3f logScale = new Vector3f(buffer.getFloat(), buffer.getFloat(), buffer.getFloat());

            // Logit-space opacity
            float logitOpacity = buffer.getFloat();

            // Spherical harmonics
            float[] sh = new float[shDim * 3];
            for (int j = 0; j < sh.length; j++) {
                sh[j] = buffer.getFloat();
            }

            splats.add(new GaussianSplat(position, rotation, logScale, logitOpacity, sh));
        }

        return new GaussianSplatData(splats, shDegree);
    }

    /**
     * Load .ply format (simplified - only supports basic Gaussian Splatting PLY)
     * This is a minimal implementation. For full PLY support, convert to .splat format externally.
     */
    private static GaussianSplatData loadPly(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        // Parse PLY header
        String line;
        int vertexCount = 0;
        boolean inHeader = true;

        while (inHeader && (line = reader.readLine()) != null) {
            if (line.startsWith("element vertex")) {
                vertexCount = Integer.parseInt(line.split("\\s+")[2]);
            } else if (line.equals("end_header")) {
                inHeader = false;
            }
        }

        if (vertexCount == 0) {
            throw new IOException("Invalid PLY file: no vertices found");
        }

        // TODO: Implement full PLY parsing
        // For now, throw an exception and recommend conversion
        throw new UnsupportedOperationException(
            "PLY format not yet fully implemented. " +
            "Please convert your .ply file to .splat format using external tools."
        );
    }

    /**
     * Get file extension from path
     */
    private static String getExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) {
            throw new IllegalArgumentException("No file extension found: " + path);
        }
        return path.substring(lastDot + 1);
    }
}
