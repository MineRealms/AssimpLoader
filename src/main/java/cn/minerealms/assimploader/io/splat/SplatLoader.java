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
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Uncompressed Graphdeco Gaussian PLY and the repository's count-prefixed .splat format. */
public final class SplatLoader {
    private SplatLoader() {}

    public static GaussianSplatData load(ResourceLocation location, ResourceManager resources) throws IOException {
        String path = location.getPath();
        int extension = path.lastIndexOf('.');
        if (extension < 0) throw new IOException("Missing splat file extension: " + location);
        try (InputStream input = resources.open(location)) {
            return load(input, path.substring(extension + 1));
        }
    }

    public static GaussianSplatData load(InputStream input, String extension) throws IOException {
        try {
            return switch (extension.toLowerCase(Locale.ROOT)) {
                case "splat" -> loadCustom(input.readAllBytes());
                case "ply" -> loadPly(input.readAllBytes());
                default -> throw new IOException("Unsupported splat format: " + extension);
            };
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid Gaussian splat attributes", failure);
        }
    }

    private static GaussianSplatData loadCustom(byte[] bytes) throws IOException {
        if (bytes.length < Integer.BYTES) throw new IOException("Truncated splat header");
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = input.getInt();
        if (count < 0 || (long) count * 59 * Float.BYTES != input.remaining()) {
            throw new IOException("Invalid custom splat count or truncated payload");
        }
        List<GaussianSplat> splats = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vector3f position = new Vector3f(input.getFloat(), input.getFloat(), input.getFloat());
            float w = input.getFloat();
            Quaternionf rotation = new Quaternionf(input.getFloat(), input.getFloat(), input.getFloat(), w);
            Vector3f logScale = new Vector3f(input.getFloat(), input.getFloat(), input.getFloat());
            float logit = input.getFloat();
            float[] sh = new float[48];
            for (int j = 0; j < sh.length; j++) sh[j] = input.getFloat();
            splats.add(new GaussianSplat(position, rotation, logScale, logit, sh));
        }
        return new GaussianSplatData(splats, 3);
    }

    private record Property(String name, String type, int bytes) {
        static Property parse(String name, String type) throws IOException {
            int bytes = switch (type) {
                case "char", "uchar", "int8", "uint8" -> 1;
                case "short", "ushort", "int16", "uint16" -> 2;
                case "float", "int", "uint", "float32", "int32", "uint32" -> 4;
                case "double", "float64" -> 8;
                default -> throw new IOException("Unsupported PLY scalar type: " + type);
            };
            return new Property(name, type, bytes);
        }

        float read(ByteBuffer input) {
            return switch (type) {
                case "char", "int8" -> input.get();
                case "uchar", "uint8" -> input.get() & 0xff;
                case "short", "int16" -> input.getShort();
                case "ushort", "uint16" -> input.getShort() & 0xffff;
                case "int", "int32" -> input.getInt();
                case "uint", "uint32" -> input.getInt() & 0xffffffffL;
                case "double", "float64" -> (float) input.getDouble();
                default -> input.getFloat();
            };
        }
    }

    private record Header(int dataStart, String format, int count, List<Property> properties, int degree) {}

    private static Header header(byte[] data) throws IOException {
        int offset = 0;
        int count = -1;
        boolean vertex = false;
        boolean seenVertex = false;
        String format = null;
        List<Property> properties = new ArrayList<>();
        Set<String> names = new HashSet<>();
        while (offset < data.length && offset < 1024 * 1024) {
            int start = offset;
            while (offset < data.length && data[offset] != '\n') offset++;
            String line = new String(data, start, offset - start, StandardCharsets.US_ASCII).trim();
            if (offset < data.length) offset++;
            if (start == 0 && !line.equals("ply")) throw new IOException("Invalid PLY signature");
            String[] words = line.split("\\s+");
            if (words[0].equals("format") && words.length == 3) format = words[1];
            if (words[0].equals("element") && words.length == 3) {
                vertex = words[1].equals("vertex");
                int elementCount = Integer.parseInt(words[2]);
                if (elementCount < 0) throw new IOException("Negative PLY element count");
                if (vertex) {
                    if (seenVertex) throw new IOException("Duplicate PLY vertex element");
                    count = elementCount;
                    seenVertex = true;
                } else if (!seenVertex && elementCount > 0) {
                    throw new IOException("Compressed or leading non-vertex PLY elements are unsupported");
                }
            }
            if (vertex && words[0].equals("property")) {
                if (words.length != 3 || words[1].equals("list")) throw new IOException("PLY vertex lists are unsupported");
                if (!names.add(words[2])) throw new IOException("Duplicate PLY property: " + words[2]);
                properties.add(Property.parse(words[2], words[1]));
            }
            if (line.equals("end_header")) {
                if (count < 0 || format == null || !names.containsAll(Set.of("x", "y", "z"))) {
                    throw new IOException("PLY vertex positions or format are missing");
                }
                if (!Set.of("ascii", "binary_little_endian", "binary_big_endian").contains(format)) {
                    throw new IOException("Unsupported PLY format: " + format);
                }
                int rest = (int) names.stream().filter(name -> name.startsWith("f_rest_")).count();
                int dimension = rest / 3 + 1;
                int degree = (int) Math.sqrt(dimension) - 1;
                if (rest % 3 != 0 || degree < 0 || degree > 3 || (degree + 1) * (degree + 1) != dimension) {
                    throw new IOException("Unsupported or incomplete Gaussian SH coefficients");
                }
                for (int i = 0; i < rest; i++) if (!names.contains("f_rest_" + i)) {
                    throw new IOException("Non-contiguous Gaussian SH coefficients");
                }
                return new Header(offset, format, count, List.copyOf(properties), degree);
            }
        }
        throw new IOException("Missing or excessive PLY header");
    }

    private static GaussianSplatData loadPly(byte[] bytes) throws IOException {
        Header header = header(bytes);
        List<GaussianSplat> splats = new ArrayList<>(Math.min(header.count, 1_000_000));
        float[] values = new float[header.properties.size()];
        if (header.format.equals("ascii")) {
            try (BufferedReader reader = new BufferedReader(new StringReader(new String(bytes, header.dataStart,
                    bytes.length - header.dataStart, StandardCharsets.US_ASCII)))) {
                for (int i = 0; i < header.count; i++) {
                    String line;
                    do { line = reader.readLine(); } while (line != null && line.isBlank());
                    if (line == null) throw new IOException("Truncated PLY vertex data at " + i);
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length < values.length) throw new IOException("Missing PLY vertex properties at " + i);
                    for (int j = 0; j < values.length; j++) values[j] = Float.parseFloat(tokens[j]);
                    splats.add(splat(header, values));
                }
            }
        } else {
            int stride = header.properties.stream().mapToInt(Property::bytes).sum();
            if ((long) header.count * stride > bytes.length - header.dataStart) throw new IOException("Truncated binary PLY");
            ByteBuffer input = ByteBuffer.wrap(bytes, header.dataStart, bytes.length - header.dataStart)
                    .order(header.format.equals("binary_little_endian") ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < header.count; i++) {
                for (int j = 0; j < values.length; j++) values[j] = header.properties.get(j).read(input);
                splats.add(splat(header, values));
            }
        }
        return new GaussianSplatData(splats, header.degree);
    }

    private static GaussianSplat splat(Header header, float[] values) {
        Vector3f position = new Vector3f();
        Vector3f logScale = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        int dimension = (header.degree + 1) * (header.degree + 1);
        float[] sh = new float[dimension * 3];
        float opacity = 0;
        for (int i = 0; i < values.length; i++) {
            float value = values[i];
            String name = header.properties.get(i).name;
            switch (name) {
                case "x" -> position.x = value;
                case "y" -> position.y = value;
                case "z" -> position.z = value;
                case "scale_0" -> logScale.x = value;
                case "scale_1" -> logScale.y = value;
                case "scale_2" -> logScale.z = value;
                case "rot_0" -> rotation.w = value;
                case "rot_1" -> rotation.x = value;
                case "rot_2" -> rotation.y = value;
                case "rot_3" -> rotation.z = value;
                case "opacity" -> opacity = value; // Graphdeco PLY stores the logit, not sigmoid(opacity).
                case "f_dc_0" -> sh[0] = value;
                case "f_dc_1" -> sh[1] = value;
                case "f_dc_2" -> sh[2] = value;
                default -> {
                    if (name.startsWith("f_rest_")) {
                        int source = Integer.parseInt(name.substring(7));
                        int coefficient = source % (dimension - 1) + 1;
                        int channel = source / (dimension - 1);
                        sh[coefficient * 3 + channel] = value;
                    }
                }
            }
        }
        return new GaussianSplat(position, rotation, logScale, opacity, sh);
    }
}
