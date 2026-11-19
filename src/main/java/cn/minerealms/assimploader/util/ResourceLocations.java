package cn.minerealms.assimploader.util;

import net.minecraft.resources.ResourceLocation;

/**
 * Version-agnostic {@link ResourceLocation} construction.
 * <p>
 * The canonical sources are written in the 1.20.1 dialect, where {@code ResourceLocation} still has
 * public constructors. 1.21 made every constructor private and replaced them with
 * {@code fromNamespaceAndPath}/{@code parse}, so the NeoForge node needs different bodies. Routing all
 * call sites through this type keeps the shared sources free of {@code //?} conditions and gives the
 * "external input" call sites a single place to normalise names.
 * <p>
 * Stonecutter rewrites the {@code //?} blocks per node, so exactly one body survives in
 * {@code versions/<node>/build/generated/stonecutter}.
 */
public final class ResourceLocations {
    private ResourceLocations() {}

    /** Equivalent of the 1.20.1 {@code new ResourceLocation(namespace, path)}. */
    public static ResourceLocation of(String namespace, String path) {
        //? if forge {
        return new ResourceLocation(namespace, path);
        //?}
        //? if neoforge {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?}
    }

    /** Equivalent of the 1.20.1 {@code new ResourceLocation("namespace:path")}. */
    public static ResourceLocation parse(String location) {
        //? if forge {
        return new ResourceLocation(location);
        //?}
        //? if neoforge {
        /*
        return ResourceLocation.parse(location);
        *///?}
    }

    /** Non-throwing parse, present on both versions. */
    public static ResourceLocation tryParse(String location) {
        return ResourceLocation.tryParse(location);
    }

    /**
     * Builds a {@link ResourceLocation} from a path that may originate outside our control (model
     * files, texture names embedded in FBX/GLTF, user-supplied resource pack entries).
     * <p>
     * 1.21 rejects anything outside {@code [a-z0-9/._-]} for the path and {@code [a-z0-9._-]} for the
     * namespace, so an illegal name is lower-cased and has its remaining illegal characters
     * replaced with {@code _} instead of taking the whole game down with a
     * {@code ResourceLocationException}. Returns a {@code minecraft} fallback rather than null so
     * callers can use the result directly.
     */
    public static ResourceLocation safe(String location, String fallbackNamespace) {
        ResourceLocation parsed = ResourceLocation.tryParse(location);
        if (parsed != null) return parsed;

        String namespace = fallbackNamespace == null || fallbackNamespace.isBlank()
                ? "minecraft" : fallbackNamespace.toLowerCase();
        String path = location;
        int separator = location.indexOf(':');
        if (separator >= 0) {
            String candidateNamespace = location.substring(0, separator).toLowerCase();
            if (isValidNamespace(candidateNamespace)) {
                namespace = candidateNamespace;
            }
            path = location.substring(separator + 1);
        }

        ResourceLocation built = ResourceLocation.tryBuild(namespace, path);
        if (built != null) return built;

        String sanitised = sanitizePath(path);
        ResourceLocation fallback = ResourceLocation.tryBuild("minecraft", sanitised);
        if (fallback != null) return fallback;

        // tryBuild only rejects an empty path, so this is unreachable in practice.
        return ResourceLocation.tryBuild("minecraft", "assimploader_fallback");
    }

    private static boolean isValidNamespace(String namespace) {
        if (namespace.isEmpty()) return false;
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (c != '-' && c != '_' && c != '.' && !(c >= '0' && c <= '9')
                    && !(c >= 'a' && c <= 'z')) {
                return false;
            }
        }
        return true;
    }

    private static String sanitizePath(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                out.append(Character.toLowerCase(c));
            } else if (c == '/' || c == '.' || c == '-' || c == '_'
                    || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z')) {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.length() == 0 ? "assimploader_fallback" : out.toString();
    }
}
