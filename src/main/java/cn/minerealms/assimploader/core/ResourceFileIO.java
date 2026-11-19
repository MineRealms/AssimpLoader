package cn.minerealms.assimploader.core;

import net.minecraft.resources.ResourceLocation;
import org.lwjgl.assimp.AIFile;
import org.lwjgl.assimp.AIFileCloseProc;
import org.lwjgl.assimp.AIFileIO;
import org.lwjgl.assimp.AIFileOpenProc;
import org.lwjgl.assimp.AIFileReadProc;
import org.lwjgl.assimp.AIFileSeek;
import org.lwjgl.assimp.AIFileTellProc;
import org.lwjgl.assimp.AIFileWriteProc;
import org.lwjgl.assimp.AIFileFlushProc;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.assimp.Assimp.*;
import cn.minerealms.assimploader.util.ResourceLocations;
/** Read-only Assimp IO over one resource namespace. All native ownership stays within one import. */
public final class ResourceFileIO implements AutoCloseable {
    @FunctionalInterface
    public interface ResourceReader {
        byte[] read(ResourceLocation resource) throws IOException;
    }

    private final ResourceLocation model;
    private final ResourceReader reader;
    private final Map<Long, OpenFile> files = new HashMap<>();
    private final Map<ResourceLocation, byte[]> contents = new HashMap<>();
    private final AIFileOpenProc openProc;
    private final AIFileCloseProc closeProc;
    private final AIFileIO io;
    private IOException lastFailure;

    public ResourceFileIO(ResourceLocation model, ResourceReader reader) {
        this.model = model;
        this.reader = reader;
        this.openProc = AIFileOpenProc.create((address, requested, mode) -> open(MemoryUtil.memUTF8(requested), MemoryUtil.memUTF8(mode)));
        this.closeProc = AIFileCloseProc.create((address, file) -> closeFile(file));
        this.io = AIFileIO.calloc().OpenProc(openProc).CloseProc(closeProc);
    }

    public AIFileIO nativeIO() {
        return io;
    }

    public IOException getLastFailure() {
        return lastFailure;
    }

    private long open(String requested, String mode) {
        if (!mode.equals("r") && !mode.equals("rb") && !mode.equals("rt")) return MemoryUtil.NULL;
        try {
            ResourceLocation resource = resolve(model, requested);
            byte[] data = contents.get(resource);
            if (data == null) {
                data = reader.read(resource);
                if (data == null) throw new IOException("Missing model resource: " + resource);
                contents.put(resource, data);
            }
            OpenFile file = new OpenFile(data);
            files.put(file.nativeFile.address(), file);
            return file.nativeFile.address();
        } catch (IOException | IllegalArgumentException exception) {
            lastFailure = exception instanceof IOException ioException ? ioException
                    : new IOException("Rejected model resource path: " + requested, exception);
            return MemoryUtil.NULL;
        }
    }

    /** Assimp may pass a sibling name or the already joined model path. Namespace changes and root escapes are rejected. */
    public static ResourceLocation resolve(ResourceLocation model, String requested) throws IOException {
        String path = requested.replace('\\', '/');
        if (path.isBlank() || path.indexOf('\0') >= 0 || path.startsWith("/") || path.contains(":") || path.contains("%")) {
            throw new IOException("Absolute, encoded or cross-namespace model path is not allowed: " + requested);
        }
        while (path.startsWith("./")) path = path.substring(2);
        String directory = model.getPath().substring(0, model.getPath().lastIndexOf('/') + 1);
        if (!path.startsWith(directory) && !path.equals(model.getPath())) path = directory + path;
        ArrayList<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (segments.isEmpty()) throw new IOException("Model side file escapes the resource root: " + requested);
                segments.remove(segments.size() - 1);
            } else {
                segments.add(segment);
            }
        }
        String normalized = String.join("/", segments);
        // Sibling models and textures may share the namespace, but traversal above its root is rejected.
        if (normalized.isEmpty()) {
            throw new IOException("Model side file escapes the resource root: " + requested);
        }
        try {
            return ResourceLocations.of(model.getNamespace(), normalized);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid model side file path: " + requested, exception);
        }
    }

    private void closeFile(long address) {
        OpenFile file = files.remove(address);
        if (file != null) file.close();
    }

    @Override
    public void close() {
        for (OpenFile file : files.values()) file.close();
        files.clear();
        contents.clear();
        io.free();
        closeProc.free();
        openProc.free();
    }

    private static final class OpenFile implements AutoCloseable {
        private final ByteBuffer data;
        private final AIFileReadProc read;
        private final AIFileTellProc tell;
        private final AIFileTellProc size;
        private final AIFileSeek seek;
        private final AIFileWriteProc write;
        private final AIFileFlushProc flush;
        private final AIFile nativeFile;

        private OpenFile(byte[] bytes) {
            data = MemoryUtil.memAlloc(bytes.length);
            data.put(bytes).flip();
            read = AIFileReadProc.create((file, output, elementSize, count) -> {
                if (elementSize <= 0 || count <= 0) return 0;
                long actual = Math.min(count, data.remaining() / elementSize);
                long byteCount = actual * elementSize;
                if (byteCount > 0) {
                    MemoryUtil.memCopy(MemoryUtil.memAddress(data), output, byteCount);
                    data.position(data.position() + (int) byteCount);
                }
                return actual;
            });
            tell = AIFileTellProc.create(file -> data.position());
            size = AIFileTellProc.create(file -> data.limit());
            seek = AIFileSeek.create((file, offset, origin) -> {
                long position = switch (origin) {
                    case aiOrigin_SET -> offset;
                    case aiOrigin_CUR -> (long) data.position() + offset;
                    case aiOrigin_END -> (long) data.limit() + offset;
                    default -> -1;
                };
                if (position < 0 || position > data.limit()) return aiReturn_FAILURE;
                data.position((int) position);
                return aiReturn_SUCCESS;
            });
            write = AIFileWriteProc.create((file, source, elementSize, count) -> 0);
            flush = AIFileFlushProc.create(file -> {});
            nativeFile = AIFile.calloc().ReadProc(read).WriteProc(write).TellProc(tell).FileSizeProc(size).SeekProc(seek).FlushProc(flush);
        }

        @Override
        public void close() {
            nativeFile.free();
            flush.free();
            write.free();
            seek.free();
            size.free();
            tell.free();
            read.free();
            MemoryUtil.memFree(data);
        }
    }
}
