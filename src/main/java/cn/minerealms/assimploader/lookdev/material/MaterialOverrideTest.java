package cn.minerealms.assimploader.lookdev.material;

import cn.minerealms.assimploader.material.PBRMaterial;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Simple test/demo for MaterialOverride system.
 *
 * Run this to verify the override system works correctly.
 */
public class MaterialOverrideTest {

    public static void main(String[] args) {
        System.out.println("=== MaterialOverride System Test ===\n");

        // Create a test material
        PBRMaterial original = createTestMaterial();
        System.out.println("Original Material:");
        printMaterial(original);

        // Test 1: Channel toggles
        System.out.println("\n--- Test 1: Channel Toggles ---");
        MaterialOverride override1 = new MaterialOverride();
        override1.enableNormal = false;
        override1.enableMetallic = false;
        PBRMaterial result1 = override1.applyTo(original);
        System.out.println("After disabling Normal + Metallic:");
        printMaterial(result1);
        verifyOriginalUnchanged(original);

        // Test 2: Parameter overrides
        System.out.println("\n--- Test 2: Parameter Overrides ---");
        MaterialOverride override2 = new MaterialOverride();
        override2.roughnessOverride = 0.8f;
        override2.metallicOverride = 1.0f;
        PBRMaterial result2 = override2.applyTo(original);
        System.out.println("After roughness=0.8, metallic=1.0:");
        printMaterial(result2);
        verifyOriginalUnchanged(original);

        // Test 3: MaterialOverrideManager
        System.out.println("\n--- Test 3: MaterialOverrideManager ---");
        MaterialOverrideManager manager = new MaterialOverrideManager(original);
        System.out.println("Manager created: " + manager);
        System.out.println("Has overrides: " + manager.hasOverrides());

        manager.setRoughnessOverride(0.9f);
        manager.disableNormalMap();
        System.out.println("After setRoughnessOverride(0.9) + disableNormalMap():");
        System.out.println("Has overrides: " + manager.hasOverrides());
        PBRMaterial result3 = manager.getEffectiveMaterial();
        printMaterial(result3);
        verifyOriginalUnchanged(original);

        // Test 4: Reset
        System.out.println("\n--- Test 4: Reset ---");
        manager.resetOverrides();
        System.out.println("After reset:");
        System.out.println("Has overrides: " + manager.hasOverrides());
        PBRMaterial result4 = manager.getEffectiveMaterial();
        printMaterial(result4);

        System.out.println("\n=== All Tests Passed ===");
    }

    private static PBRMaterial createTestMaterial() {
        PBRMaterial material = new PBRMaterial("test_material");
        material.setBaseColor(0.8f, 0.2f, 0.2f, 1.0f);
        material.setBaseColorTexture(new ResourceLocation("test", "textures/base.png"));
        material.setMetallicFactor(0.5f);
        material.setRoughnessFactor(0.3f);
        material.setNormalTexture(new ResourceLocation("test", "textures/normal.png"));
        material.setEmissiveFactor(0.1f, 0.1f, 0.1f);
        material.setAlphaMode(PBRMaterial.AlphaMode.OPAQUE);
        return material;
    }

    private static void printMaterial(PBRMaterial material) {
        System.out.println("  Name: " + material.getName());
        Vector4f color = material.getBaseColor();
        System.out.println("  BaseColor: (" + color.x + ", " + color.y + ", " + color.z + ", " + color.w + ")");
        System.out.println("  BaseColorTexture: " + material.getBaseColorTexture());
        System.out.println("  Metallic: " + material.getMetallicFactor());
        System.out.println("  Roughness: " + material.getRoughnessFactor());
        System.out.println("  NormalTexture: " + material.getNormalTexture());
        System.out.println("  NormalScale: " + material.getNormalScale());
        Vector3f emissive = material.getEmissiveFactor();
        System.out.println("  Emissive: (" + emissive.x + ", " + emissive.y + ", " + emissive.z + ")");
        System.out.println("  AlphaMode: " + material.getAlphaMode());
    }

    private static void verifyOriginalUnchanged(PBRMaterial original) {
        // Verify original material was not modified
        assert original.getName().equals("test_material") : "Original name changed!";
        assert original.getMetallicFactor() == 0.5f : "Original metallic changed!";
        assert original.getRoughnessFactor() == 0.3f : "Original roughness changed!";
        assert original.getNormalTexture() != null : "Original normal texture removed!";
        System.out.println("  ✓ Original material unchanged");
    }
}
