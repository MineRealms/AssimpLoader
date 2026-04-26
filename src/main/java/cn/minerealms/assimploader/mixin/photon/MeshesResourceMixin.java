package cn.minerealms.assimploader.mixin.photon;

import cn.minerealms.assimploader.compat.photon.AssimpToPhotonMeshConverter;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 扩展 Photon 的 MeshesResource，添加 FBX 导入功能
 *
 * 功能：
 * - 在 Photon 编辑器的 Mesh 资源面板添加 "Import FBX Mesh" 按钮
 * - 打开文件选择对话框
 * - 调用 AssimpLoader 加载 FBX
 * - 转换为 Photon MeshData
 * - 添加到资源管理器
 *
 * 注意：此 Mixin 仅在 Photon 存在时加载（通过 PhotonCompat 条件加载）
 */
@Mixin(targets = "com.lowdragmc.photon.gui.editor.MeshesResource", remap = false)
public class MeshesResourceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MeshesResourceMixin");

    /**
     * 注入到 buildConfigurator 方法，添加 "Import FBX" 按钮
     *
     * 在 Photon 编辑器的 Mesh 配置面板底部添加导入按钮
     */
    @Inject(
        method = "buildConfigurator",
        at = @At("RETURN"),
        remap = false
    )
    private void addFBXImportButton(Object father, CallbackInfo ci) {
        try {
            LOGGER.info("Adding FBX import button to Photon Mesh editor");

            // 使用反射创建 ButtonWidget
            Class<?> buttonWidgetClass = Class.forName("com.lowdragmc.lowdraglib.gui.widget.ButtonWidget");
            Class<?> guiTextureGroupClass = Class.forName("com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup");
            Class<?> colorPatternClass = Class.forName("com.lowdragmc.lowdraglib.gui.editor.ColorPattern");
            Class<?> textTextureClass = Class.forName("com.lowdragmc.lowdraglib.gui.texture.TextTexture");
            Class<?> wrapperConfiguratorClass = Class.forName("com.lowdragmc.lowdraglib.gui.editor.configurator.WrapperConfigurator");

            // 创建按钮纹理
            Object grayPattern = colorPatternClass.getField("T_GRAY").get(null);
            Method rectTextureMethod = colorPatternClass.getMethod("rectTexture");
            Object rectTexture = rectTextureMethod.invoke(grayPattern);
            Method setRadiusMethod = rectTexture.getClass().getMethod("setRadius", float.class);
            setRadiusMethod.invoke(rectTexture, 5.0f);

            Object textTexture = textTextureClass.getConstructor(String.class)
                    .newInstance("Import FBX Mesh");

            Object guiTextureGroup = guiTextureGroupClass.getConstructor(Object[].class)
                    .newInstance((Object) new Object[]{rectTexture, textTexture});

            // 创建按钮点击处理器
            Class<?> clickDataClass = Class.forName("com.lowdragmc.lowdraglib.gui.widget.Widget$ClickData");
            Object clickHandler = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{java.util.function.Consumer.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("accept")) {
                            onImportButtonClick();
                        }
                        return null;
                    }
            );

            // 创建按钮 (x, y, width, height, texture, clickHandler)
            Object button = buttonWidgetClass.getConstructor(
                    int.class, int.class, int.class, int.class,
                    Class.forName("com.lowdragmc.lowdraglib.gui.texture.IGuiTexture"),
                    java.util.function.Consumer.class
            ).newInstance(0, 0, 200, 20, guiTextureGroup, clickHandler);

            // 创建 WrapperConfigurator
            Object wrapper = wrapperConfiguratorClass.getConstructor(String.class, Object.class)
                    .newInstance("FBX Import", button);

            // 添加到 father (ConfiguratorGroup)
            Method addConfiguratorsMethod = father.getClass().getMethod("addConfigurators", Object[].class);
            addConfiguratorsMethod.invoke(father, (Object) new Object[]{wrapper});

            LOGGER.info("FBX import button added successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to add FBX import button", e);
        }
    }

    /**
     * 处理导入按钮点击事件
     */
    private void onImportButtonClick() {
        try {
            LOGGER.info("FBX import button clicked");

            // 获取 Editor 实例
            Class<?> editorClass = Class.forName("com.lowdragmc.lowdraglib.gui.editor.ui.Editor");
            Object editorInstance = editorClass.getField("INSTANCE").get(null);

            if (editorInstance == null) {
                LOGGER.warn("Editor instance is null");
                return;
            }

            // 获取工作空间目录
            Method getWorkSpaceMethod = editorClass.getMethod("getWorkSpace");
            File workSpace = (File) getWorkSpaceMethod.invoke(editorInstance);

            // 打开文件选择对话框
            Class<?> dialogWidgetClass = Class.forName("com.lowdragmc.lowdraglib.gui.widget.DialogWidget");
            Method showFileDialogMethod = dialogWidgetClass.getMethod(
                    "showFileDialog",
                    Object.class, String.class, File.class, boolean.class,
                    java.util.function.Predicate.class, java.util.function.Consumer.class
            );

            // 创建文件过滤器（.fbx, .obj, .gltf, .glb）
            java.util.function.Predicate<File> fileFilter = file -> {
                if (file.isDirectory()) return true;
                String name = file.getName().toLowerCase();
                return name.endsWith(".fbx") || name.endsWith(".obj") ||
                       name.endsWith(".gltf") || name.endsWith(".glb");
            };

            // 创建文件选择回调
            java.util.function.Consumer<File> callback = file -> {
                if (file != null && file.isFile()) {
                    importFBXMesh(file);
                }
            };

            // 显示对话框
            showFileDialogMethod.invoke(null, editorInstance, "Select FBX/OBJ/GLTF file",
                    workSpace, true, fileFilter, callback);

        } catch (Exception e) {
            LOGGER.error("Failed to open file dialog", e);
        }
    }

    /**
     * 导入 FBX 文件的所有 Mesh
     */
    private void importFBXMesh(File fbxFile) {
        LOGGER.info("Importing FBX mesh from: {}", fbxFile.getAbsolutePath());

        try {
            // 1. 使用 AssimpLoader 加载 FBX
            AssimpScene scene = AssimpModelLoader.loadModel(fbxFile.toPath());
            LOGGER.info("FBX loaded: {} meshes", scene.getMeshes().size());

            // 2. 转换所有 Mesh
            Map<String, Object> meshes = AssimpToPhotonMeshConverter.convertScene(scene);
            LOGGER.info("Converted {} meshes", meshes.size());

            // 3. 添加到 Photon 资源管理器
            Class<?> meshesResourceClass = Class.forName("com.lowdragmc.photon.gui.editor.MeshesResource");
            Method addStaticResourceMethod = meshesResourceClass.getMethod("addStaticResource", String.class, Object.class);

            int imported = 0;
            for (Map.Entry<String, Object> entry : meshes.entrySet()) {
                String meshName = fbxFile.getName().replace(".fbx", "")
                        .replace(".obj", "")
                        .replace(".gltf", "")
                        .replace(".glb", "")
                        + "_" + entry.getKey();

                addStaticResourceMethod.invoke(this, meshName, entry.getValue());
                imported++;
                LOGGER.info("Added mesh to Photon: {}", meshName);
            }

            // 4. 释放 Assimp 资源
            scene.release();

            // 5. 显示成功消息
            showNotification("FBX Import Success",
                    "Imported " + imported + " mesh(es) from " + fbxFile.getName());

            LOGGER.info("FBX import completed successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to import FBX mesh", e);
            showNotification("FBX Import Failed", "Error: " + e.getMessage());
        }
    }

    /**
     * 显示通知消息
     */
    private void showNotification(String title, String message) {
        try {
            Class<?> editorClass = Class.forName("com.lowdragmc.lowdraglib.gui.editor.ui.Editor");
            Object editorInstance = editorClass.getField("INSTANCE").get(null);

            if (editorInstance != null) {
                Method getNotificationPanelMethod = editorClass.getMethod("getNotificationPanel");
                Object notificationPanel = getNotificationPanelMethod.invoke(editorInstance);

                Method showMethod = notificationPanel.getClass().getMethod("show", String.class, String.class);
                showMethod.invoke(notificationPanel, title, message);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to show notification", e);
        }
    }
}
