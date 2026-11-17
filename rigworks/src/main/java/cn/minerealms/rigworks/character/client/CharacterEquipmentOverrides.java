package cn.minerealms.rigworks.character.client;

import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Per-profile equipment resources stored below the configured game config directory. */
final class CharacterEquipmentOverrides {
    private static final long MAX_BYTES = 1024 * 1024;
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4",
            "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");
    private final Path configDirectory;
    private final Path output;

    CharacterEquipmentOverrides(Path configDirectory, String profileId) {
        this.configDirectory = Objects.requireNonNull(configDirectory).toAbsolutePath().normalize();
        ResourceLocation profile = new ResourceLocation(Objects.requireNonNull(profileId));
        safeSegment(profile.getNamespace());
        for (String segment : profile.getPath().split("/", -1)) safeSegment(segment);
        String relative = profile.getPath().endsWith(".json") ? profile.getPath() : profile.getPath() + ".json";
        Path root = this.configDirectory.resolve("rigworks/equipment");
        output = root.resolve(profile.getNamespace()).resolve(relative).normalize();
        if (!output.startsWith(root) || output.equals(root)) throw new IllegalArgumentException("Equipment profile path escapes override directory");
    }

    Path path() { return output; }

    CharacterEquipmentDefinition loadOrDefault(CharacterEquipmentDefinition resource, Consumer<CharacterEquipmentDefinition> validate,
                                               BiConsumer<Path, Exception> onInvalid) {
        try {
            if (Files.notExists(output, LinkOption.NOFOLLOW_LINKS)) return resource;
            checkConfined();
            if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) || Files.size(output) > MAX_BYTES)
                throw new IOException("Equipment override must be a regular JSON file below 1 MiB");
            CharacterEquipmentDefinition result;
            try (var input = Files.newInputStream(output)) {
                byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("Equipment override exceeds 1 MiB");
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                result = CharacterEquipmentDefinition.read(new StringReader(json));
            }
            validate.accept(result);
            return result;
        } catch (IOException | RuntimeException error) {
            onInvalid.accept(output, error);
            return resource;
        }
    }

    Path save(CharacterEquipmentDefinition definition) throws IOException {
        StringWriter writer = new StringWriter();
        definition.write(writer);
        String json = writer.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Equipment override exceeds 1 MiB");
        CharacterEquipmentDefinition.read(new StringReader(json));
        Files.createDirectories(configDirectory);
        checkConfined();
        Files.createDirectories(output.getParent());
        checkConfined();
        Path temporary = Files.createTempFile(output.getParent(), ".equipment-", ".json.tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            checkConfined();
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return output;
    }

    private void checkConfined() throws IOException {
        Path realConfig = configDirectory.toRealPath();
        Path current = configDirectory;
        for (Path segment : configDirectory.relativize(output)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IOException("Equipment override path contains a symbolic link: " + current);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.toRealPath().equals(realConfig.resolve(configDirectory.relativize(current))))
                throw new IOException("Equipment override path is redirected outside its profile directory: " + current);
        }
    }

    private static void safeSegment(String segment) {
        String stem = segment.contains(".") ? segment.substring(0, segment.indexOf('.')) : segment;
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith(".") || RESERVED.contains(stem))
            throw new IllegalArgumentException("Unsafe equipment profile path component: " + segment);
    }
}
