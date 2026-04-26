package cn.minerealms.assimploader.mixin.photon;

import cn.minerealms.assimploader.compat.photon.AssimpToPhotonMeshConverter;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import com.lowdragmc.photon.gui.editor.MeshesResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * 扩展 Photon 的 MeshesResource，添加 FBX 网格导入功能
 */
@Mixin(value = MeshesResource.class, remap = false)
public class MeshesResourceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MeshesResourceMixin");

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

            LOGGER.info("Adding FBX mesh import menu to MeshesResource");

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
                    ((TreeBuilder.Menu) menu).leaf("Import FBX Mesh", () -> openImportDialog(container));
                } catch (Exception e) {
                    LOGGER.error("Error adding menu item", e);
                }
            };

            // 设置新的 onMenu
            onMenuField.set(container, newOnMenu);

            LOGGER.info("FBX mesh import menu added successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to add FBX mesh import menu", e);
        }
    }

    /**
     * 打开文件选择对话框
     */
    private static void openImportDialog(ResourceContainer<?, ?> container) {
        try {
            LOGGER.info("Opening FBX mesh import dialog");

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
                        importFBXMesh(container, file);
                    }
                }
            );

        } catch (Exception e) {
            LOGGER.error("Failed to open import dialog", e);
        }
    }

    /**
     * 导入 FBX 网格
     */
    private static void importFBXMesh(ResourceContainer<?, ?> container, File fbxFile) {
        try {
            LOGGER.info("Importing FBX mesh from: {}", fbxFile.getAbsolutePath());

            // 1. 加载 FBX 文件
            AssimpModelLoader loader = new AssimpModelLoader();
            AssimpScene scene = loader.loadModel(fbxFile.toPath());

            LOGGER.info("FBX loaded: {} meshes", scene.getMeshes().size());

            // 2. 转换所有 Mesh
            Map<String, MeshData> meshes = AssimpToPhotonMeshConverter.convertAllMeshes(scene);
            LOGGER.info("Converted {} meshes", meshes.size());

            // 3. 获取 MeshesResource 实例
            MeshesResource resource = (MeshesResource) container.getResource();

            // 4. 添加到 Photon 资源管理器
            int imported = 0;
            for (Map.Entry<String, MeshData> entry : meshes.entrySet()) {
                String meshName = fbxFile.getName().replace(".fbx", "")
                        .replace(".obj", "")
                        .replace(".gltf", "")
                        .replace(".glb", "")
                        + "_" + entry.getKey();

                File meshFile = resource.getStaticResourceFile(meshName);
                resource.addStaticResource(meshFile, entry.getValue());
                imported++;
                LOGGER.info("Added mesh to Photon: {} -> {}", meshName, meshFile.getAbsolutePath());
            }

            // 5. 刷新容器显示
            container.reBuild();

            // 6. 释放 Assimp 资源
            scene.release();

            LOGGER.info("FBX mesh import completed successfully: {} mesh(es) imported", imported);

        } catch (Exception e) {
            LOGGER.error("Failed to import FBX mesh from {}", fbxFile.getName(), e);
        }
    }
}
