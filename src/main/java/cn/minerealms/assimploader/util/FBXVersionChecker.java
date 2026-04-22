package cn.minerealms.assimploader.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility to check FBX file version before loading
 */
public class FBXVersionChecker {

    /**
     * Check if FBX file is binary format and get version
     *
     * @param filePath Path to FBX file
     * @return FBX version number (e.g., 7400, 7500) or -1 if not binary FBX
     */
    public static int getFBXVersion(String filePath) {
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            // Read first 27 bytes (FBX header)
            byte[] header = new byte[27];
            file.read(header);

            // Check magic bytes: "Kaydara FBX Binary  \0"
            String magic = new String(header, 0, 21);
            if (!magic.startsWith("Kaydara FBX Binary")) {
                return -1; // Not binary FBX or ASCII FBX
            }

            // Bytes 23-26 contain version as little-endian int
            ByteBuffer buffer = ByteBuffer.wrap(header, 23, 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int version = buffer.getInt();

            return version;
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Check if FBX version is supported by Assimp
     *
     * @param version FBX version number
     * @return true if likely supported
     */
    public static boolean isVersionSupported(int version) {
        // Assimp generally supports FBX 7100-7400 well
        // 7500+ (FBX 2020+) may have issues
        return version >= 7100 && version <= 7400;
    }

    /**
     * Get human-readable version string
     */
    public static String getVersionString(int version) {
        switch (version) {
            case 7500: return "FBX 2020 (7.5)";
            case 7400: return "FBX 2014-2019 (7.4)";
            case 7300: return "FBX 2013 (7.3)";
            case 7200: return "FBX 2012 (7.2)";
            case 7100: return "FBX 2011 (7.1)";
            default: return "FBX version " + version;
        }
    }
}
