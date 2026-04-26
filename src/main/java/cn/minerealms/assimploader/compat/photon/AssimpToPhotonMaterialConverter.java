package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.material.PBRMaterial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PBRMaterial → Photon Material 转换器
 *
 * 将 AssimpLoader 的 PBRMaterial 转换为 Photon 可用的材质格式。
 *
 * 支持的转换：
 * 1. TextureMaterial - 基础纹理材质
 *    - BaseColorTexture → Texture
 *    - BaseColor → Tint Color
 *    - AlphaMode → BlendMode
 *
 * 2. CustomShaderMaterial - 自发光材质（高级）
 *    - BaseColorTexture → Sampler0
 *    - EmissiveFactor → Uniform
 *    - 支持 Bloom 效果
 *
 * 限制：
 * - Photon 不支持完整 PBR（Metallic/Roughness 会丢失）
 * - 法线贴图需要自定义 Shader
 * - 粒子系统本质上不适合复杂 PBR 渲染
 *
 * 使用场景：
 * - 为粒子发射器提供纹理和颜色
 * - 自发光粒子（火焰、魔法效果）
 */
public class AssimpToPhotonMaterialConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("AssimpToPhotonMaterialConverter");

    /**
     * 转换为 Photon TextureMaterial（基础材质）
     *
     * 映射关系：
     * - BaseColorTexture → Texture
     * - BaseColor → (暂不支持 tint，Photon 的 TextureMaterial 没有 tint 字段)
     * - AlphaMode → BlendMode
     *
     * @param pbr PBR 材质
     * @return Photon TextureMaterial 对象（通过反射创建）
     * @throws Exception 如果 Photon 类不可用或转换失败
     */
    public static Object convertToTextureMaterial(PBRMaterial pbr) throws Exception {
        if (!PhotonCompat.isPhotonLoaded()) {
            throw new IllegalStateException("Photon is not loaded");
        }

        LOGGER.info("Converting PBRMaterial to TextureMaterial: {}", pbr.getName());

        // 使用反射创建 TextureMaterial
        Class<?> textureMaterialClass = Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial");
        Object textureMaterial = textureMaterialClass.getDeclaredConstructor().newInstance();

        // 设置纹理
        if (pbr.getBaseColorTexture() != null) {
            textureMaterialClass.getField("texture").set(textureMaterial, pbr.getBaseColorTexture());
            LOGGER.debug("Set texture: {}", pbr.getBaseColorTexture());
        }

        // 设置混合模式
        Class<?> blendModeClass = Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode");
        Object blendMode = convertAlphaMode(pbr.getAlphaMode(), blendModeClass);
        textureMaterialClass.getField("blendMode").set(textureMaterial, blendMode);
        LOGGER.debug("Set blend mode: {}", pbr.getAlphaMode());

        LOGGER.info("TextureMaterial conversion complete");
        return textureMaterial;
    }

    /**
     * 转换为 Photon CustomShaderMaterial（支持自发光）
     *
     * 映射关系：
     * - BaseColorTexture → Sampler0
     * - EmissiveFactor → EmissiveColor uniform
     * - 使用 Photon 默认粒子 Shader
     *
     * @param pbr PBR 材质
     * @return Photon CustomShaderMaterial 对象（通过反射创建）
     * @throws Exception 如果 Photon 类不可用或转换失败
     */
    public static Object convertToCustomShader(PBRMaterial pbr) throws Exception {
        if (!PhotonCompat.isPhotonLoaded()) {
            throw new IllegalStateException("Photon is not loaded");
        }

        LOGGER.info("Converting PBRMaterial to CustomShaderMaterial: {}", pbr.getName());

        // 使用反射创建 CustomShaderMaterial
        Class<?> customShaderClass = Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial");
        Object customShader = customShaderClass.getDeclaredConstructor().newInstance();

        // 设置 Shader（使用 Photon 默认粒子 Shader）
        Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
        Object shaderLocation = resourceLocationClass.getConstructor(String.class, String.class)
                .newInstance("photon", "particle");
        customShaderClass.getField("shader").set(customShader, shaderLocation);

        // 获取 uniformTag (CompoundTag)
        Object uniformTag = customShaderClass.getField("uniformTag").get(customShader);
        Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");

        // 设置纹理（Sampler0）
        if (pbr.getBaseColorTexture() != null) {
            Object samplersTag = compoundTagClass.getDeclaredConstructor().newInstance();
            compoundTagClass.getMethod("putString", String.class, String.class)
                    .invoke(samplersTag, "0", pbr.getBaseColorTexture().toString());
            compoundTagClass.getMethod("put", String.class, Class.forName("net.minecraft.nbt.Tag"))
                    .invoke(uniformTag, "samplers", samplersTag);
            LOGGER.debug("Set sampler0: {}", pbr.getBaseColorTexture());
        }

        // 设置自发光颜色（EmissiveColor uniform）
        if (pbr.isEmissive()) {
            Object uniformsTag = compoundTagClass.getDeclaredConstructor().newInstance();
            Class<?> listTagClass = Class.forName("net.minecraft.nbt.ListTag");
            Class<?> floatTagClass = Class.forName("net.minecraft.nbt.FloatTag");

            Object emissiveList = listTagClass.getDeclaredConstructor().newInstance();
            emissiveList.getClass().getMethod("add", Object.class)
                    .invoke(emissiveList, floatTagClass.getMethod("valueOf", float.class)
                            .invoke(null, pbr.getEmissiveFactor().x));
            emissiveList.getClass().getMethod("add", Object.class)
                    .invoke(emissiveList, floatTagClass.getMethod("valueOf", float.class)
                            .invoke(null, pbr.getEmissiveFactor().y));
            emissiveList.getClass().getMethod("add", Object.class)
                    .invoke(emissiveList, floatTagClass.getMethod("valueOf", float.class)
                            .invoke(null, pbr.getEmissiveFactor().z));
            emissiveList.getClass().getMethod("add", Object.class)
                    .invoke(emissiveList, floatTagClass.getMethod("valueOf", float.class)
                            .invoke(null, 1.0f));

            uniformsTag.getClass().getMethod("put", String.class, Class.forName("net.minecraft.nbt.Tag"))
                    .invoke(uniformsTag, "EmissiveColor", emissiveList);
            compoundTagClass.getMethod("put", String.class, Class.forName("net.minecraft.nbt.Tag"))
                    .invoke(uniformTag, "uniforms", uniformsTag);

            LOGGER.debug("Set emissive color: ({}, {}, {})",
                    pbr.getEmissiveFactor().x,
                    pbr.getEmissiveFactor().y,
                    pbr.getEmissiveFactor().z);
        }

        LOGGER.info("CustomShaderMaterial conversion complete");
        return customShader;
    }

    /**
     * 转换 AlphaMode 到 Photon BlendMode
     */
    private static Object convertAlphaMode(PBRMaterial.AlphaMode alphaMode, Class<?> blendModeClass) throws Exception {
        String blendModeName = switch (alphaMode) {
            case OPAQUE -> "CLOSE_CIRCUITS";
            case MASK -> "ALPHA";
            case BLEND -> "ADDITIVE_ALPHA";
        };

        return blendModeClass.getField(blendModeName).get(null);
    }

    /**
     * 自动选择最佳材质类型
     *
     * 规则：
     * - 如果有自发光 → CustomShaderMaterial
     * - 否则 → TextureMaterial
     *
     * @param pbr PBR 材质
     * @return Photon 材质对象
     * @throws Exception 如果转换失败
     */
    public static Object convertAuto(PBRMaterial pbr) throws Exception {
        if (pbr.isEmissive()) {
            LOGGER.info("Material has emissive, using CustomShaderMaterial");
            return convertToCustomShader(pbr);
        } else {
            LOGGER.info("Material is basic, using TextureMaterial");
            return convertToTextureMaterial(pbr);
        }
    }
}
