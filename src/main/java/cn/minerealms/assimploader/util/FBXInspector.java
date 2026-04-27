package cn.minerealms.assimploader.util;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpAnimation;
import cn.minerealms.assimploader.model.AssimpScene;

import java.nio.file.Path;

/**
 * Utility to inspect FBX files and print animation information
 */
public class FBXInspector {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: FBXInspector <path-to-fbx>");
            return;
        }

        String filePath = args[0];
        inspectFBX(filePath);
    }

    public static void inspectFBX(String filePath) {
        System.out.println("=== FBX Inspector ===");
        System.out.println("File: " + filePath);
        System.out.println();

        try {
            AssimpModelLoader loader = new AssimpModelLoader();
            AssimpScene scene = loader.loadModel(Path.of(filePath));

            if (scene == null) {
                System.err.println("Failed to load FBX file!");
                return;
            }

            System.out.println("✓ File loaded successfully");
            System.out.println();

            // Mesh info
            System.out.println("Meshes: " + scene.getMeshes().size());
            scene.getMeshes().forEach(mesh -> {
                System.out.println("  - " + mesh.getName() +
                    " (vertices: " + mesh.getVertexCount() +
                    ", bones: " + (mesh.hasBones() ? "YES" : "NO") + ")");
            });
            System.out.println();

            // Animation info
            System.out.println("Animations: " + scene.getAnimations().size());
            if (scene.getAnimations().isEmpty()) {
                System.out.println("  ⚠ No animations found in this file");
            } else {
                for (AssimpAnimation anim : scene.getAnimations()) {
                    System.out.println("  - Name: \"" + anim.getName() + "\"");
                    System.out.println("    Duration: " + anim.getDuration() + " ticks");
                    System.out.println("    TPS: " + anim.getTicksPerSecond());
                    System.out.println("    Duration (seconds): " + (anim.getDuration() / anim.getTicksPerSecond()));
                    System.out.println();
                }
            }

            // Bone info
            System.out.println("Bones:");
            boolean hasBones = false;
            for (var mesh : scene.getMeshes()) {
                if (mesh.hasBones()) {
                    hasBones = true;
                    System.out.println("  Mesh \"" + mesh.getName() + "\" has bones");
                }
            }
            if (!hasBones) {
                System.out.println("  ⚠ No bones found in this file");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
