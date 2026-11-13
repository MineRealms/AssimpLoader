package cn.minerealms.assimploader.util;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Resolves mod-owned files from the active Minecraft instance instead of the process CWD. */
public final class AssimpLoaderPaths {
    private AssimpLoaderPaths() {}

    /** Returns the active Forge game directory, with a useful test/tool fallback. */
    public static Path gameDirectory() {
        try {
            return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        } catch (RuntimeException | LinkageError unavailable) {
            return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        }
    }

    /** Returns the active Forge config directory, with a game-directory fallback. */
    public static Path configDirectory() {
        try {
            return FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        } catch (RuntimeException | LinkageError unavailable) {
            return gameDirectory().resolve("config").normalize();
        }
    }

    /** Resolves an optional config path; relative values are always relative to the game directory. */
    public static Path resolveGamePath(String configured, String defaultRelative) {
        return resolve(gameDirectory(), configured, defaultRelative);
    }

    static Path resolve(Path gameDirectory, String configured, String defaultRelative) {
        String value = configured == null || configured.isBlank() ? defaultRelative : configured.trim();
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : gameDirectory.resolve(path)).toAbsolutePath().normalize();
    }
}
