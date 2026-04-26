package cn.minerealms.assimploader.mixin.photon;

import cn.minerealms.assimploader.compat.photon.AssimpToPhotonMaterialConverter;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.gui.editor.MaterialsResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 扩展 Photon 的 MaterialsResource，添加 FBX 材质导入功能
 */
@Mixin(value = MaterialsResource.class, remap = false)
public class MaterialsResourceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MaterialsResourceMixin");

    /**
     * 注入到 createContainer 方法，添加右键菜单项
     */
    @Inject(
        method = "createContainer",
        at = @At("RETURN"),
        remap = false
    )
    private void addFBXImportMenu(ResourcePanel panel, CallbackInfoReturnable<ResourceContainer<?, ?>> cir) {
        try {
            ResourceContainer<?, ?> container = cir.getReturnValue();
            if (container == null) return;

            LOGGER.info("Adding FBX material import menu to MaterialsResource");

            // 使用反射获取 onMenu 字段
            java.lang.reflect.Field onMenuField = ResourceContainer.class.getDeclaredField("onMenu");
            onMenuField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.function.BiConsumer<Object, Object> existingOnMenu =
                (java.util.function.BiConsumer<Object, Object>) onMenuField.get(container);

            // 创建新的 onMenu，包装原有的 onMenu
            java.util.function.BiConsumer<Object, Object> newOnMenu = (selected, menu) -> {
                // 调用原有的 onMenu
                if (existingOnMenu != null) {
                    try {
                        existingOnMenu.accept(selected, menu);
                    } catch (Exception e) {
                        LOGGER.error("Error calling existing onMenu", e);
                    }
                }

                // 添加我们的菜单项
                try {
                    ((TreeBuilder.Menu) menu).leaf("Import FBX Materials", () -> openImportDialog(container));
                } catch (Exception e) {
                    LOGGER.error("Error adding menu item", e);
                }
            };

            // 设置新的 onMenu
            onMenuField.set(container, newOnMenu);

            LOGGER.info("FBX material import menu added successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to add FBX material import menu", e);
        }
    }

    /**
     * 打开文件选择对话框
     */
    private static void openImportDialog(ResourceContainer<?, ?> container) {
        try {
            LOGGER.info("Opening FBX material import dialog");

            // 获取 Editor 实例
            Editor editorInstance = Editor.INSTANCE;
            if (editorInstance == null) {
                LOGGER.warn("Editor instance is null");
                return;
            }

            // 获取工作空间
            File workSpace = editorInstance.getWorkSpace();
            if (workSpace == null) {
                LOGGER.warn("Workspace is null");
                return;
            }

            // 显示文件选择对话框
            DialogWidget.showFileDialog(
                editorInstance,
                "Select FBX/OBJ/GLTF file",
                workSpace,
                true,
                treeNode -> {
                    File file = treeNode.getKey();
                    if (file == null) return false;
                    String name = file.getName().toLowerCase();
                    return name.endsWith(".fbx") || name.endsWith(".obj") ||
                           name.endsWith(".gltf") || name.endsWith(".glb");
                },
                file -> {
                    if (file != null) {
                        importFBXMaterials(container, file);
                    }
                }
            );

        } catch (Exception e) {
            LOGGER.error("Failed to open import dialog", e);
        }
    }

    /**
     * 导入 FBX 材质
     */
    private static void importFBXMaterials(ResourceContainer<?, ?> container, File fbxFile) {
        try {
            LOGGER.info("Importing FBX materials from: {}", fbxFile.getAbsolutePath());

            // 1. 加载 FBX 文件
            AssimpModelLoader loader = new AssimpModelLoader();
            AssimpScene scene = loader.loadModel(fbxFile.toPath());

            LOGGER.info("FBX loaded: {} meshes, {} materials",
                    scene.getMeshes().size(), scene.getMaterials().size());

            // 2. 提取所有使用的材质（去重）
            Set<Integer> usedMaterialIndices = new HashSet<>();
            for (AssimpMesh mesh : scene.getMeshes()) {
                int matIndex = mesh.getMaterialIndex();
                if (matIndex >= 0 && matIndex < scene.getMaterials().size()) {
                    usedMaterialIndices.add(matIndex);
                }
            }

            LOGGER.info("Found {} unique materials in use", usedMaterialIndices.size());

            // 3. 获取 MaterialsResource 实例
            MaterialsResource resource = (MaterialsResource) container.getResource();

            // 4. 转换并添加材质
            int imported = 0;
            for (int matIndex : usedMaterialIndices) {
                AssimpMaterial assimpMat = scene.getMaterials().get(matIndex);

                // 手动创建 PBRMaterial
                String materialName = fbxFile.getName().replace(".fbx", "")
                        .replace(".obj", "")
                        .replace(".gltf", "")
                        .replace(".glb", "")
                        + "_Material_" + matIndex;
                PBRMaterial pbrMat = new PBRMaterial(materialName);

                // 设置基础颜色
                float[] diffuse = assimpMat.getDiffuseColor();
                pbrMat.setBaseColor(new org.joml.Vector4f(
                    diffuse[0],
                    diffuse[1],
                    diffuse[2],
                    diffuse[3]
                ));

                // 设置自发光（使用环境光作为自发光的近似）
                float[] ambient = assimpMat.getAmbientColor();
                if (ambient != null && (ambient[0] > 0.01f || ambient[1] > 0.01f || ambient[2] > 0.01f)) {
                    pbrMat.setEmissiveFactor(new org.joml.Vector3f(
                        ambient[0],
                        ambient[1],
                        ambient[2]
                    ));
                }

                LOGGER.info("Converted material: {} (emissive: {})",
                        pbrMat.getName(), pbrMat.isEmissive());

                // 转换为 Photon Material（自动选择类型）
                IMaterial photonMat = (IMaterial) AssimpToPhotonMaterialConverter.convertAuto(pbrMat);

                // 创建文件路径并添加到 Photon 资源管理器
                File materialFile = resource.getStaticResourceFile(materialName);
                resource.addStaticResource(materialFile, photonMat);
                imported++;
                LOGGER.info("Added material to Photon: {} -> {}", materialName, materialFile.getAbsolutePath());
            }

            // 5. 刷新容器显示
            container.reBuild();

            // 6. 释放 Assimp 资源
            scene.release();

            LOGGER.info("FBX material import completed successfully: {} material(s) imported", imported);

        } catch (Exception e) {
            LOGGER.error("Failed to import FBX materials from {}", fbxFile.getName(), e);
        }
    }
}
