package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.material.PBRMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PBRMaterial → Photon Material 转换器
 */
public class AssimpToPhotonMaterialConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("AssimpToPhotonMaterialConverter");

    /**
     * 转换为 Photon TextureMaterial（基础材质）
     */
    public static TextureMaterial convertToTextureMaterial(PBRMaterial pbr) {
        LOGGER.info("Converting PBRMaterial to TextureMaterial: {}", pbr.getName());

        TextureMaterial textureMaterial = new TextureMaterial();

        // 设置纹理
        if (pbr.getBaseColorTexture() != null) {
            textureMaterial.texture = pbr.getBaseColorTexture();
            LOGGER.debug("Set texture: {}", pbr.getBaseColorTexture());
        }

        LOGGER.info("TextureMaterial conversion complete");
        return textureMaterial;
    }

    /**
     * 转换为 Photon CustomShaderMaterial（支持自发光）
     */
    public static CustomShaderMaterial convertToCustomShader(PBRMaterial pbr) {
        LOGGER.info("Converting PBRMaterial to CustomShaderMaterial: {}", pbr.getName());

        CustomShaderMaterial customShader = new CustomShaderMaterial();

        // 设置 Shader（使用 Photon 默认粒子 Shader）
        customShader.shader = new ResourceLocation("photon", "particle");

        // 获取 uniformTag
        CompoundTag uniformTag = customShader.serializeNBT(new CompoundTag()).getCompound("uniform");

        // 设置纹理（Sampler0）
        if (pbr.getBaseColorTexture() != null) {
            CompoundTag samplersTag = new CompoundTag();
            samplersTag.putString("0", pbr.getBaseColorTexture().toString());
            uniformTag.put("samplers", samplersTag);
            LOGGER.debug("Set sampler0: {}", pbr.getBaseColorTexture());
        }

        // 设置自发光颜色（EmissiveColor uniform）
        if (pbr.isEmissive()) {
            CompoundTag uniformsTag = new CompoundTag();
            ListTag emissiveList = new ListTag();
            emissiveList.add(FloatTag.valueOf(pbr.getEmissiveFactor().x));
            emissiveList.add(FloatTag.valueOf(pbr.getEmissiveFactor().y));
            emissiveList.add(FloatTag.valueOf(pbr.getEmissiveFactor().z));
            emissiveList.add(FloatTag.valueOf(1.0f));

            uniformsTag.put("EmissiveColor", emissiveList);
            uniformTag.put("uniforms", uniformsTag);

            LOGGER.debug("Set emissive color: ({}, {}, {})",
                    pbr.getEmissiveFactor().x,
                    pbr.getEmissiveFactor().y,
                    pbr.getEmissiveFactor().z);
        }

        // 反序列化回去
        CompoundTag tag = new CompoundTag();
        tag.put("uniform", uniformTag);
        customShader.deserializeNBT(tag);

        LOGGER.info("CustomShaderMaterial conversion complete");
        return customShader;
    }

    /**
     * 自动选择最佳材质类型
     */
    public static Object convertAuto(PBRMaterial pbr) {
        if (pbr.isEmissive()) {
            LOGGER.info("Material has emissive, using CustomShaderMaterial");
            return convertToCustomShader(pbr);
        } else {
            LOGGER.info("Material is basic, using TextureMaterial");
            return convertToTextureMaterial(pbr);
        }
    }
}
