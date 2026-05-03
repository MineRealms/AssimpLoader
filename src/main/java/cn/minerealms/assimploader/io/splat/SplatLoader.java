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
     * Load .ply format (Gaussian Splatting PLY)
     * Standard format from https://github.com/graphdeco-inria/gaussian-splatting
     *
     * Properties order:
     * - x, y, z (position)
     * - nx, ny, nz (normal, optional)
     * - f_dc_0, f_dc_1, f_dc_2 (SH DC component, RGB)
     * - f_rest_0 ... f_rest_44 (SH rest components, 45 values for degree 3)
     * - opacity
     * - scale_0, scale_1, scale_2
     * - rot_0, rot_1, rot_2, rot_3 (quaternion)
     */
    private static GaussianSplatData loadPly(InputStream stream) throws IOException {
        System.out.println("[SplatLoader] Loading PLY file...");

        byte[] data = stream.readAllBytes();
        System.out.println("[SplatLoader] File size: " + data.length + " bytes");

        // Parse PLY header
        StringBuilder headerBuilder = new StringBuilder();
        int headerEndPos = 0;
        boolean foundEndHeader = false;

        // Find end_header
        for (int i = 0; i < data.length - 10; i++) {
            if (data[i] == 'e' && data[i+1] == 'n' && data[i+2] == 'd' && data[i+3] == '_' &&
                data[i+4] == 'h' && data[i+5] == 'e' && data[i+6] == 'a' && data[i+7] == 'd' &&
                data[i+8] == 'e' && data[i+9] == 'r') {

                // Build header up to this point
                for (int j = 0; j < i; j++) {
                    if (data[j] >= 32 && data[j] < 127) { // Printable ASCII
                        headerBuilder.append((char) data[j]);
                    } else if (data[j] == '\n' || data[j] == '\r') {
                        headerBuilder.append((char) data[j]);
                    }
                }

                headerEndPos = i + 10;
                // Skip to next newline
                while (headerEndPos < data.length && data[headerEndPos] != '\n') {
                    headerEndPos++;
                }
                headerEndPos++; // Skip the newline
                foundEndHeader = true;
                break;
            }
        }

        if (!foundEndHeader) {
            throw new IOException("Invalid PLY file: no end_header found");
        }

        String header = headerBuilder.toString();
        System.out.println("[SplatLoader] PLY Header (first 800 chars):\n" +
                         header.substring(0, Math.min(800, header.length())));

        // Parse header properties
        PLYHeader plyHeader = parsePLYHeader(header);
        System.out.println("[SplatLoader] Parsed header: " + plyHeader.vertexCount + " vertices, " +
                         (plyHeader.isBinary ? "binary" : "ascii") + " format");
        System.out.println("[SplatLoader] Properties: " + plyHeader.properties.size());

        // Parse data
        if (plyHeader.isBinary) {
            return loadPlyBinary(data, headerEndPos, plyHeader);
        } else {
            return loadPlyAscii(new String(data, headerEndPos, data.length - headerEndPos), plyHeader);
        }
    }

    /**
     * PLY header information
     */
    private static class PLYHeader {
        int vertexCount;
        boolean isBinary;
        boolean isLittleEndian;
        List<PLYProperty> properties = new ArrayList<>();
    }

    /**
     * PLY property definition
     */
    private static class PLYProperty {
        String name;
        String type; // float, double, uchar, etc.
        int size; // bytes

        PLYProperty(String name, String type) {
            this.name = name;
            this.type = type;
            this.size = switch (type) {
                case "float" -> 4;
                case "double" -> 8;
                case "uchar", "char" -> 1;
                case "ushort", "short" -> 2;
                case "uint", "int" -> 4;
                default -> 4;
            };
        }
    }

    /**
     * Parse PLY header
     */
    private static PLYHeader parsePLYHeader(String header) throws IOException {
        PLYHeader plyHeader = new PLYHeader();
        String[] lines = header.split("\n");

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("format")) {
                if (line.contains("binary_little_endian")) {
                    plyHeader.isBinary = true;
                    plyHeader.isLittleEndian = true;
                } else if (line.contains("binary_big_endian")) {
                    plyHeader.isBinary = true;
                    plyHeader.isLittleEndian = false;
                } else if (line.contains("ascii")) {
                    plyHeader.isBinary = false;
                }
            } else if (line.startsWith("element vertex")) {
                String[] parts = line.split("\\s+");
                plyHeader.vertexCount = Integer.parseInt(parts[2]);
            } else if (line.startsWith("property")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String type = parts[1];
                    String name = parts[2];
                    plyHeader.properties.add(new PLYProperty(name, type));
                }
            }
        }

        return plyHeader;
    }

    /**
     * Load binary PLY format
     */
    private static GaussianSplatData loadPlyBinary(byte[] data, int dataStart, PLYHeader plyHeader) throws IOException {
        System.out.println("[SplatLoader] Loading binary PLY data from offset " + dataStart);

        ByteBuffer buffer = ByteBuffer.wrap(data, dataStart, data.length - dataStart);
        buffer.order(plyHeader.isLittleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

        List<GaussianSplat> splats = new ArrayList<>(plyHeader.vertexCount);
        int shDegree = 3;
        int shDim = (shDegree + 1) * (shDegree + 1); // 16

        // Calculate bytes per vertex
        int bytesPerVertex = 0;
        for (PLYProperty prop : plyHeader.properties) {
            bytesPerVertex += prop.size;
        }
        System.out.println("[SplatLoader] Bytes per vertex: " + bytesPerVertex);
        System.out.println("[SplatLoader] Expected total: " + (bytesPerVertex * plyHeader.vertexCount));
        System.out.println("[SplatLoader] Actual remaining: " + buffer.remaining());

        try {
            for (int i = 0; i < plyHeader.vertexCount; i++) {
                if (buffer.remaining() < bytesPerVertex) {
                    System.err.println("[SplatLoader] Not enough data at vertex " + i +
                                     " (need " + bytesPerVertex + ", have " + buffer.remaining() + ")");
                    break;
                }

                // Read properties in order
                float x = 0, y = 0, z = 0;
                float[] sh = new float[shDim * 3];
                float opacity = 1.0f;
                float scale_0 = 0, scale_1 = 0, scale_2 = 0;
                float rot_0 = 1, rot_1 = 0, rot_2 = 0, rot_3 = 0;

                int shIndex = 0;

                for (PLYProperty prop : plyHeader.properties) {
                    float value = readPropertyValue(buffer, prop);

                    // Map property to field
                    switch (prop.name) {
                        case "x" -> x = value;
                        case "y" -> y = value;
                        case "z" -> z = value;
                        case "nx", "ny", "nz" -> {} // Skip normals
                        case "f_dc_0", "f_dc_1", "f_dc_2" -> {
                            if (shIndex < sh.length) sh[shIndex++] = value;
                        }
                        case "opacity" -> opacity = value;
                        case "scale_0" -> scale_0 = value;
                        case "scale_1" -> scale_1 = value;
                        case "scale_2" -> scale_2 = value;
                        case "rot_0" -> rot_0 = value;
                        case "rot_1" -> rot_1 = value;
                        case "rot_2" -> rot_2 = value;
                        case "rot_3" -> rot_3 = value;
                        default -> {
                            // f_rest_0 ... f_rest_44
                            if (prop.name.startsWith("f_rest_") && shIndex < sh.length) {
                                sh[shIndex++] = value;
                            }
                        }
                    }
                }

                Vector3f position = new Vector3f(x, y, z);
                Vector3f logScale = new Vector3f(scale_0, scale_1, scale_2);
                Quaternionf rotation = new Quaternionf(rot_0, rot_1, rot_2, rot_3);

                // Convert opacity to logit space
                opacity = Math.max(0.001f, Math.min(0.999f, opacity)); // Clamp to avoid log(0)
                float logitOpacity = (float) Math.log(opacity / (1.0f - opacity));

                splats.add(new GaussianSplat(position, rotation, logScale, logitOpacity, sh));

                if (i < 3) {
                    System.out.println("[SplatLoader] Vertex " + i + ": pos=" + position +
                                     ", opacity=" + opacity + ", scale=" + logScale + ", rot=" + rotation);
                }
            }
        } catch (Exception e) {
            System.err.println("[SplatLoader] Error parsing PLY binary data: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to parse PLY binary data", e);
        }

        System.out.println("[SplatLoader] Successfully loaded " + splats.size() + " splats from binary PLY");
        return new GaussianSplatData(splats, shDegree);
    }

    /**
     * Read a property value from buffer based on type
     */
    private static float readPropertyValue(ByteBuffer buffer, PLYProperty prop) {
        return switch (prop.type) {
            case "float" -> buffer.getFloat();
            case "double" -> (float) buffer.getDouble();
            case "uchar" -> (float) (buffer.get() & 0xFF);
            case "char" -> (float) buffer.get();
            case "ushort" -> (float) (buffer.getShort() & 0xFFFF);
            case "short" -> (float) buffer.getShort();
            case "uint" -> (float) (buffer.getInt() & 0xFFFFFFFFL);
            case "int" -> (float) buffer.getInt();
            default -> buffer.getFloat();
        };
    }

    /**
     * Load ASCII PLY format
     */
    private static GaussianSplatData loadPlyAscii(String data, PLYHeader plyHeader) throws IOException {
        System.out.println("[SplatLoader] Loading ASCII PLY data");

        String[] lines = data.split("\n");
        List<GaussianSplat> splats = new ArrayList<>(plyHeader.vertexCount);
        int shDegree = 3;
        int shDim = (shDegree + 1) * (shDegree + 1);

        for (int i = 0; i < Math.min(plyHeader.vertexCount, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] values = line.split("\\s+");
            if (values.length < plyHeader.properties.size()) {
                System.err.println("[SplatLoader] Line " + i + " has too few values: " +
                                 values.length + " (expected " + plyHeader.properties.size() + ")");
                continue;
            }

            try {
                float x = 0, y = 0, z = 0;
                float[] sh = new float[shDim * 3];
                float opacity = 1.0f;
                float scale_0 = 0, scale_1 = 0, scale_2 = 0;
                float rot_0 = 1, rot_1 = 0, rot_2 = 0, rot_3 = 0;

                int shIndex = 0;

                for (int j = 0; j < plyHeader.properties.size() && j < values.length; j++) {
                    PLYProperty prop = plyHeader.properties.get(j);
                    float value = Float.parseFloat(values[j]);

                    switch (prop.name) {
                        case "x" -> x = value;
                        case "y" -> y = value;
                        case "z" -> z = value;
                        case "nx", "ny", "nz" -> {} // Skip normals
                        case "f_dc_0", "f_dc_1", "f_dc_2" -> {
                            if (shIndex < sh.length) sh[shIndex++] = value;
                        }
                        case "opacity" -> opacity = value;
                        case "scale_0" -> scale_0 = value;
                        case "scale_1" -> scale_1 = value;
                        case "scale_2" -> scale_2 = value;
                        case "rot_0" -> rot_0 = value;
                        case "rot_1" -> rot_1 = value;
                        case "rot_2" -> rot_2 = value;
                        case "rot_3" -> rot_3 = value;
                        default -> {
                            if (prop.name.startsWith("f_rest_") && shIndex < sh.length) {
                                sh[shIndex++] = value;
                            }
                        }
                    }
                }

                Vector3f position = new Vector3f(x, y, z);
                Vector3f logScale = new Vector3f(scale_0, scale_1, scale_2);
                Quaternionf rotation = new Quaternionf(rot_0, rot_1, rot_2, rot_3);

                opacity = Math.max(0.001f, Math.min(0.999f, opacity));
                float logitOpacity = (float) Math.log(opacity / (1.0f - opacity));

                splats.add(new GaussianSplat(position, rotation, logScale, logitOpacity, sh));
            } catch (NumberFormatException e) {
                System.err.println("[SplatLoader] Error parsing line " + i + ": " + e.getMessage());
            }
        }

        System.out.println("[SplatLoader] Successfully loaded " + splats.size() + " splats from ASCII PLY");
        return new GaussianSplatData(splats, shDegree);
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
