package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.material.PBRMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.minerealms.assimploader.util.ResourceLocations;
/**
 * PBRMaterial → Photon Material 转换器
 * <p>
 * Photon 2.2.11 replaced the public material fields with setters and moved the custom-shader
 * uniforms into a {@code LDShaderHolder}, so the 1.21 node only guarantees the shader location;
 * the sampler/emissive defaults are applied where the new API exposes them.
 */
public class AssimpToPhotonMaterialConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("AssimpToPhotonMaterialConverter");

    /**
     * 转换为 Photon TextureMaterial（基础材质）
     */
    public static TextureMaterial convertToTextureMaterial(PBRMaterial pbr) {
        LOGGER.info("Converting PBRMaterial to TextureMaterial: {}", pbr.getName());

        TextureMaterial textureMaterial = new TextureMaterial();

        if (pbr.getBaseColorTexture() != null) {
            //? if forge {
            textureMaterial.texture = pbr.getBaseColorTexture();
            //?}
            //? if neoforge {
            /*
            textureMaterial.setTexture(pbr.getBaseColorTexture());
            *///?}
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
        //? if forge {
        customShader.shader = ResourceLocations.of("photon", "particle");
        //?}
        //? if neoforge {
        /*
        customShader.setShader(ResourceLocations.of("photon", "particle"));
        *///?}

        //? if forge {
        // 获取 uniformTag
        net.minecraft.nbt.CompoundTag uniformTag = customShader.serializeNBT(new net.minecraft.nbt.CompoundTag()).getCompound("uniform");

        // 设置纹理（Sampler0）
        if (pbr.getBaseColorTexture() != null) {
            net.minecraft.nbt.CompoundTag samplersTag = new net.minecraft.nbt.CompoundTag();
            samplersTag.putString("0", pbr.getBaseColorTexture().toString());
            uniformTag.put("samplers", samplersTag);
            LOGGER.debug("Set sampler0: {}", pbr.getBaseColorTexture());
        }

        // 设置自发光颜色（EmissiveColor uniform）
        if (pbr.isEmissive()) {
            net.minecraft.nbt.CompoundTag uniformsTag = new net.minecraft.nbt.CompoundTag();
            net.minecraft.nbt.ListTag emissiveList = new net.minecraft.nbt.ListTag();
            emissiveList.add(net.minecraft.nbt.FloatTag.valueOf(pbr.getEmissiveFactor().x));
            emissiveList.add(net.minecraft.nbt.FloatTag.valueOf(pbr.getEmissiveFactor().y));
            emissiveList.add(net.minecraft.nbt.FloatTag.valueOf(pbr.getEmissiveFactor().z));
            emissiveList.add(net.minecraft.nbt.FloatTag.valueOf(1.0f));

            uniformsTag.put("EmissiveColor", emissiveList);
            uniformTag.put("uniforms", uniformsTag);

            LOGGER.debug("Set emissive color: ({}, {}, {})",
                    pbr.getEmissiveFactor().x,
                    pbr.getEmissiveFactor().y,
                    pbr.getEmissiveFactor().z);
        }

        // 反序列化回去
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("uniform", uniformTag);
        customShader.deserializeNBT(tag);
        //?} else {
        /*
        // 2.2.11 keeps the uniforms inside a private LDShaderHolder that is only reachable through
        // the shader's own serialisation; the sampler/emissive defaults are therefore not applied
        // on this version. The shader itself still renders the base material.
        if (pbr.getBaseColorTexture() != null) {
            LOGGER.debug("Sampler defaults are not applied on this Photon version: {}", pbr.getBaseColorTexture());
        }
        *///?}

        LOGGER.info("CustomShaderMaterial conversion complete");
        return customShader;
    }

    /**
     * 自动选择最佳材质类型
     */
    public static IMaterial convertAuto(PBRMaterial pbr) {
        if (pbr.isEmissive()) {
            LOGGER.info("Material has emissive, using CustomShaderMaterial");
            //? if neoforge {
            /*
            try {
                return convertToCustomShader(pbr);
            } catch (RuntimeException e) {
                // A missing `photon:particle` shader must not lose the imported material.
                LOGGER.warn("Custom shader material unavailable, falling back to TextureMaterial", e);
                return convertToTextureMaterial(pbr);
            }
            *///?}
            //? if forge {
            return convertToCustomShader(pbr);
            //?}
        } else {
            LOGGER.info("Material is basic, using TextureMaterial");
            return convertToTextureMaterial(pbr);
        }
    }
}
