package cn.minerealms.assimploader.light;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.joml.Vector3f;
import org.lwjgl.assimp.AILight;
import org.lwjgl.assimp.AIScene;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts light data from Assimp AIScene.
 *
 * Supports:
 * - Point lights (omni-directional)
 * - Directional lights (sun/moon)
 * - Spot lights (cone-shaped)
 * - Area lights (rectangular/disc)
 *
 * FBX Light Properties:
 * - Position, direction, color
 * - Intensity, attenuation
 * - Spot angle, falloff
 * - Light type
 *
 * References:
 * - https://github.com/assimp/assimp/blob/master/include/assimp/light.h
 * - https://learnopengl.com/Lighting/Light-casters
 */
public class LightDataExtractor {

    /**
     * Light type enum
     */
    public enum LightType {
        UNDEFINED,
        DIRECTIONAL,
        POINT,
        SPOT,
        AMBIENT,
        AREA
    }

    /**
     * Light data container
     */
    public static class LightData {
        private final String name;
        private final LightType type;
        private final Vector3f position;
        private final Vector3f direction;
        private final Vector3f color;
        private final float intensity;
        private final float attenuationConstant;
        private final float attenuationLinear;
        private final float attenuationQuadratic;
        private final float innerConeAngle;
        private final float outerConeAngle;
        private final Vector3f areaSize;

        public LightData(String name, LightType type, Vector3f position, Vector3f direction,
                        Vector3f color, float intensity,
                        float attenuationConstant, float attenuationLinear, float attenuationQuadratic,
                        float innerConeAngle, float outerConeAngle, Vector3f areaSize) {
            this.name = name;
            this.type = type;
            this.position = position;
            this.direction = direction;
            this.color = color;
            this.intensity = intensity;
            this.attenuationConstant = attenuationConstant;
            this.attenuationLinear = attenuationLinear;
            this.attenuationQuadratic = attenuationQuadratic;
            this.innerConeAngle = innerConeAngle;
            this.outerConeAngle = outerConeAngle;
            this.areaSize = areaSize;
        }

        public String getName() { return name; }
        public LightType getType() { return type; }
        public Vector3f getPosition() { return position; }
        public Vector3f getDirection() { return direction; }
        public Vector3f getColor() { return color; }
        public float getIntensity() { return intensity; }
        public float getAttenuationConstant() { return attenuationConstant; }
        public float getAttenuationLinear() { return attenuationLinear; }
        public float getAttenuationQuadratic() { return attenuationQuadratic; }
        public float getInnerConeAngle() { return innerConeAngle; }
        public float getOuterConeAngle() { return outerConeAngle; }
        public Vector3f getAreaSize() { return areaSize; }

        @Override
        public String toString() {
            return String.format("Light{name='%s', type=%s, pos=%s, dir=%s, color=%s, intensity=%.2f}",
                name, type, position, direction, color, intensity);
        }
    }

    /**
     * Extract all lights from scene
     */
    public static List<LightData> extractLights(AIScene aiScene, CoordinateSystemConverter.CoordinateSystem sourceSystem) {
        List<LightData> lights = new ArrayList<>();

        int numLights = aiScene.mNumLights();
        if (numLights == 0) {
            AssimpLoader.LOGGER.debug("No lights found in scene");
            return lights;
        }

        AssimpLoader.LOGGER.debug("Extracting {} lights", numLights);

        for (int i = 0; i < numLights; i++) {
            AILight aiLight = AILight.create(aiScene.mLights().get(i));
            LightData lightData = extractLight(aiLight, sourceSystem);
            lights.add(lightData);

            AssimpLoader.LOGGER.debug("  Light {}: {}", i, lightData);
        }

        return lights;
    }

    /**
     * Extract single light
     */
    private static LightData extractLight(AILight aiLight, CoordinateSystemConverter.CoordinateSystem sourceSystem) {
        String name = aiLight.mName().dataString();

        // Extract light type
        LightType type = convertLightType(aiLight.mType());

        // Extract position
        Vector3f position = new Vector3f(
            aiLight.mPosition().x(),
            aiLight.mPosition().y(),
            aiLight.mPosition().z()
        );
        position = CoordinateSystemConverter.convertPosition(
            position,
            sourceSystem,
            CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED
        );

        // Extract direction
        Vector3f direction = new Vector3f(
            aiLight.mDirection().x(),
            aiLight.mDirection().y(),
            aiLight.mDirection().z()
        );
        direction = CoordinateSystemConverter.convertDirection(
            direction,
            sourceSystem,
            CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED
        );

        // Extract color (RGB)
        Vector3f color = new Vector3f(
            aiLight.mColorDiffuse().r(),
            aiLight.mColorDiffuse().g(),
            aiLight.mColorDiffuse().b()
        );

        // Extract intensity (not directly available in Assimp, use color magnitude)
        float intensity = color.length();
        if (intensity > 0) {
            color.normalize();
        }

        // Extract attenuation
        float attenuationConstant = aiLight.mAttenuationConstant();
        float attenuationLinear = aiLight.mAttenuationLinear();
        float attenuationQuadratic = aiLight.mAttenuationQuadratic();

        // Extract spot light angles
        float innerConeAngle = aiLight.mAngleInnerCone();
        float outerConeAngle = aiLight.mAngleOuterCone();

        // Extract area light size
        Vector3f areaSize = new Vector3f(
            aiLight.mSize().x(),
            aiLight.mSize().y(),
            0.0f
        );

        return new LightData(
            name, type, position, direction, color, intensity,
            attenuationConstant, attenuationLinear, attenuationQuadratic,
            innerConeAngle, outerConeAngle, areaSize
        );
    }

    /**
     * Convert Assimp light type to our enum
     */
    private static LightType convertLightType(int aiLightType) {
        switch (aiLightType) {
            case 0x1: return LightType.DIRECTIONAL;
            case 0x2: return LightType.POINT;
            case 0x3: return LightType.SPOT;
            case 0x4: return LightType.AMBIENT;
            case 0x5: return LightType.AREA;
            default: return LightType.UNDEFINED;
        }
    }

    /**
     * Check if scene has lights
     */
    public static boolean hasLights(AIScene aiScene) {
        return aiScene.mNumLights() > 0;
    }

    /**
     * Get light count
     */
    public static int getLightCount(AIScene aiScene) {
        return aiScene.mNumLights();
    }
}
