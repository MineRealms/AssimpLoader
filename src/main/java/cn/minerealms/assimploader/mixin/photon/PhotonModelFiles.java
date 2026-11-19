package cn.minerealms.assimploader.mixin.photon;

import java.io.File;
import java.util.Locale;

/**
 * Shared helpers for the Photon editor resource mixins.
 * <p>
 * Photon already imports the glTF/OBJ families natively; Assimp is only offered for the formats it
 * understands and Photon does not, so a file never has to fight over an importer. The list lives
 * here because both the mesh and the material mixin need the same answer.
 */
final class PhotonModelFiles {

    private static final String[] ASSIMP_ONLY_EXTENSIONS = {".fbx", ".dae", ".3ds", ".blend", ".x3d"};

    private PhotonModelFiles() {}

    static boolean isAssimpOnly(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String extension : ASSIMP_ONLY_EXTENSIONS) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }

    /**
     * File name without its extension, used as the base for imported Photon resource names.
     */
    static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
