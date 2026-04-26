package cn.minerealms.assimploader.mixin.photon;

import cn.minerealms.assimploader.compat.photon.AssimpToPhotonMaterialConverter;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.material.MaterialConverter;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 扩展 Photon 的 MaterialsResource，添加 FBX 材质导入功能
 *
 * 功能：
 * - 在 Photon 编辑器的 Material 资源面板添加 "Import FBX Materials" 按钮
 * - 打开文件选择对话框
 * - 调用 AssimpLoader 加载 FBX
 * - 提取材质并转换为 Photon Material
 * - 添加到资源管理器
 *
 * 注意：此 Mixin 仅在 Photon 存在时加载（通过 PhotonCompat 条件加载）
 */
@Mixin(targets = "com.lowdragmc.photon.gui.editor.MaterialsResource", remap = false)
public class MaterialsResourceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MaterialsResourceMixin");

    /**
     * 注入到 buildConfigurator 方法，添加 "Import FBX Materials" 按钮
     */
    @Inject(
        method = "buildConfigurator",
        at = @At("RETURN"),
        remap = false
    )
    private void addFBXMaterialImport(Object father, CallbackInfo ci) {
        try {
            LOGGER.info("Adding FBX material import button to Photon Material editor");

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
                    .newInstance("Import FBX Materials");

            Object guiTextureGroup = guiTextureGroupClass.getConstructor(Object[].class)
                    .newInstance((Object) new Object[]{rectTexture, textTexture});

            // 创建按钮点击处理器
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

            // 创建按钮
            Object button = buttonWidgetClass.getConstructor(
                    int.class, int.class, int.class, int.class,
                    Class.forName("com.lowdragmc.lowdraglib.gui.texture.IGuiTexture"),
                    java.util.function.Consumer.class
            ).newInstance(0, 0, 200, 20, guiTextureGroup, clickHandler);

            // 创建 WrapperConfigurator
            Object wrapper = wrapperConfiguratorClass.getConstructor(String.class, Object.class)
                    .newInstance("FBX Material Import", button);

            // 添加到 father (ConfiguratorGroup)
            Method addConfiguratorsMethod = father.getClass().getMethod("addConfigurators", Object[].class);
            addConfiguratorsMethod.invoke(father, (Object) new Object[]{wrapper});

            LOGGER.info("FBX material import button added successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to add FBX material import button", e);
        }
    }

    /**
     * 处理导入按钮点击事件
     */
    private void onImportButtonClick() {
        try {
            LOGGER.info("FBX material import button clicked");

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

            // 创建文件过滤器
            java.util.function.Predicate<File> fileFilter = file -> {
                if (file.isDirectory()) return true;
                String name = file.getName().toLowerCase();
                return name.endsWith(".fbx") || name.endsWith(".obj") ||
                       name.endsWith(".gltf") || name.endsWith(".glb");
            };

            // 创建文件选择回调
            java.util.function.Consumer<File> callback = file -> {
                if (file != null && file.isFile()) {
                    importFBXMaterials(file);
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
     * 导入 FBX 文件的所有材质
     */
    private void importFBXMaterials(File fbxFile) {
        LOGGER.info("Importing FBX materials from: {}", fbxFile.getAbsolutePath());

        try {
            // 1. 使用 AssimpLoader 加载 FBX
            AssimpScene scene = AssimpModelLoader.loadModel(fbxFile.toPath());
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

            // 3. 转换并添加材质
            Class<?> materialsResourceClass = Class.forName("com.lowdragmc.photon.gui.editor.MaterialsResource");
            Method addStaticResourceMethod = materialsResourceClass.getMethod("addStaticResource", String.class, Object.class);

            int imported = 0;
            for (int matIndex : usedMaterialIndices) {
                AssimpMaterial assimpMat = scene.getMaterials().get(matIndex);

                // 转换为 PBRMaterial
                PBRMaterial pbrMat = MaterialConverter.convertToPBR(assimpMat, scene);
                LOGGER.info("Converted material: {} (emissive: {})",
                        pbrMat.getName(), pbrMat.isEmissive());

                // 转换为 Photon Material（自动选择类型）
                Object photonMat = AssimpToPhotonMaterialConverter.convertAuto(pbrMat);

                // 添加到资源管理器
                String matName = fbxFile.getName().replace(".fbx", "")
                        .replace(".obj", "")
                        .replace(".gltf", "")
                        .replace(".glb", "")
                        + "_" + pbrMat.getName();

                addStaticResourceMethod.invoke(this, matName, photonMat);
                imported++;
                LOGGER.info("Added material to Photon: {}", matName);
            }

            // 4. 释放 Assimp 资源
            scene.release();

            // 5. 显示成功消息
            showNotification("FBX Material Import Success",
                    "Imported " + imported + " material(s) from " + fbxFile.getName());

            LOGGER.info("FBX material import completed successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to import FBX materials", e);
            showNotification("FBX Material Import Failed", "Error: " + e.getMessage());
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
