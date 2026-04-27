package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationPlayer;
import cn.minerealms.assimploader.animation.Bone;
import cn.minerealms.assimploader.animation.ik.IKPresets;
import cn.minerealms.assimploader.animation.ik.controllers.FootIKController;
import cn.minerealms.assimploader.animation.ik.controllers.HandIKController;
import cn.minerealms.assimploader.animation.ik.controllers.HeadLookAtController;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpAnimation;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 完整的 Minecraft 实体渲染器示例，展示如何使用 FBX 动画和 IK 系统。
 *
 * 功能：
 * - 加载 FBX 模型和动画
 * - 播放关键帧动画（idle, walk, run）
 * - 运行时 IK（脚部地面对齐、头部追踪玩家、手部抓取物品）
 * - 动画状态机切换
 */
public class AnimatedEntityRenderer extends EntityRenderer<TestAnimatedEntity> {

    // 模型和动画数据
    private AssimpScene scene;
    private Map<String, AnimationPlayer> animations;
    private AnimationPlayer currentAnimation;

    // IK 控制器
    private HeadLookAtController headIK;
    private HandIKController handIK;
    private FootIKController footIK;

    // 根骨骼（用于 IK 初始化）
    private Bone rootBone;

    public AnimatedEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        loadModelAndAnimations();
    }

    /**
     * 步骤 1：加载 FBX 模型和动画
     */
    private void loadModelAndAnimations() {
        try {
            // 加载 FBX 文件（包含模型 + 多个动画）
            Path modelPath = Path.of("models/character.fbx");
            AssimpModelLoader loader = new AssimpModelLoader();

            // 使用 DYNAMIC 模式保留骨骼层级（动画需要）
            scene = loader.loadModel(modelPath, true); // auto-detect 会自动选择 DYNAMIC

            // 提取所有动画
            animations = new HashMap<>();
            for (AssimpAnimation anim : scene.getAnimations()) {
                String animName = anim.getName();
                AnimationPlayer player = new AnimationPlayer(anim);
                player.setLooping(true);
                animations.put(animName, player);
            }

            // 设置默认动画
            currentAnimation = animations.get("idle");
            if (currentAnimation != null) {
                currentAnimation.play();
            }

            // 初始化 IK 系统
            initializeIK();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 步骤 2：初始化 IK 系统
     */
    private void initializeIK() {
        if (currentAnimation == null) return;

        // 从场景中获取根骨骼
        rootBone = extractRootBone(scene);

        // 初始化 IK
        currentAnimation.initializeIK(rootBone);

        // 使用预设快速配置（假设是人形骨架）
        try {
            IKPresets.HumanoidIK humanoid = IKPresets.createHumanoid(
                currentAnimation.getIKManager()
            );

            headIK = humanoid.head;
            handIK = humanoid.hands;
            footIK = humanoid.feet;

            // 配置 IK 参数
            setupIKParameters();

        } catch (Exception e) {
            // 如果预设失败，手动创建控制器
            createIKControllersManually();
        }
    }

    /**
     * 步骤 3：配置 IK 参数
     */
    private void setupIKParameters() {
        // 头部 IK：追踪玩家
        headIK.setMaxAngle(70); // 最大转头角度 70 度
        headIK.setLookSpeed(3.0f); // 平滑追踪速度
        headIK.setWeight(0.5f); // 50% IK + 50% 关键帧

        // 脚部 IK：地面对齐
        footIK.setFootOffset(0.05f); // 5cm 脚跟高度
        footIK.setTransitionSpeed(5.0f); // 平滑过渡速度
        footIK.setLeftFootWeight(0.8f); // 80% IK
        footIK.setRightFootWeight(0.8f);

        // 设置地面检测回调
        footIK.setGroundRaycast(footPos -> {
            // 在这里实现射线检测
            // 返回脚下的地面位置
            return new Vector3f(footPos.x, getGroundY(footPos), footPos.z);
        });

        // 手部 IK：默认禁用，需要时启用
        handIK.disableLeftHand();
        handIK.disableRightHand();
    }

    /**
     * 步骤 4：渲染实体（每帧调用）
     */
    @Override
    public void render(TestAnimatedEntity entity, float entityYaw, float partialTick,
                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        if (scene == null || currentAnimation == null) return;

        // 更新动画状态
        updateAnimationState(entity, partialTick);

        // 更新 IK 目标
        updateIKTargets(entity, partialTick);

        // 更新动画播放器（关键帧 + IK）
        currentAnimation.update(partialTick / 20.0); // Minecraft tick to seconds

        // 渲染模型
        poseStack.pushPose();

        // 应用实体变换
        poseStack.translate(0, 0, 0);
        poseStack.scale(1.0f, 1.0f, 1.0f);

        // 渲染场景（使用更新后的骨骼变换）
        renderScene(scene, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    /**
     * 步骤 5：动画状态机（根据实体状态切换动画）
     */
    private void updateAnimationState(TestAnimatedEntity entity, float partialTick) {
        String desiredState = entity.getAnimationState(); // "idle", "walk", "run"

        AnimationPlayer desiredAnimation = animations.get(desiredState);
        if (desiredAnimation != null && desiredAnimation != currentAnimation) {
            // 切换动画
            if (currentAnimation != null) {
                currentAnimation.pause();
            }

            currentAnimation = desiredAnimation;
            currentAnimation.play();

            // 重新初始化 IK（新动画需要重新绑定）
            initializeIK();
        }
    }

    /**
     * 步骤 6：更新 IK 目标（每帧）
     */
    private void updateIKTargets(TestAnimatedEntity entity, float partialTick) {
        if (headIK == null || footIK == null) return;

        // 头部 IK：追踪最近的玩家
        Player nearestPlayer = entity.level().getNearestPlayer(entity, 10.0);
        if (nearestPlayer != null) {
            Vec3 playerPos = nearestPlayer.getEyePosition(partialTick);
            headIK.setTarget(new Vector3f(
                (float) playerPos.x,
                (float) playerPos.y,
                (float) playerPos.z
            ));
            headIK.update(partialTick / 20.0f);
        }

        // 脚部 IK：自动地面对齐
        if (entity.onGround()) {
            footIK.enableLeftFoot();
            footIK.enableRightFoot();
            footIK.update(partialTick / 20.0f);
        } else {
            // 空中时禁用脚部 IK
            footIK.disableLeftFoot();
            footIK.disableRightFoot();
        }

        // 手部 IK：抓取物品示例
        if (!entity.getMainHandItem().isEmpty()) {
            handIK.enableRightHand();

            // 计算物品位置
            Vector3f itemPos = calculateItemPosition(entity);
            handIK.setRightHandTarget(itemPos, null);

            // 平滑过渡到 IK
            handIK.smoothReachRight(partialTick / 20.0f, 1.0f);
        } else {
            // 松开物品，平滑回到关键帧
            handIK.smoothReachRight(partialTick / 20.0f, 0.0f);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 从场景中提取根骨骼
     */
    private Bone extractRootBone(AssimpScene scene) {
        // 这里需要根据你的骨骼层级实现
        // 通常根骨骼名为 "Root", "Armature", "Skeleton" 等

        // 示例：创建简单的骨骼层级
        // 实际应该从 scene.getRootNode() 递归构建
        return new Bone("Root", new org.joml.Matrix4f().identity());
    }

    /**
     * 地面高度检测
     */
    private float getGroundY(Vector3f footPos) {
        // 实现射线检测，返回地面 Y 坐标
        // 这里简化为返回实体脚下位置
        return 0.0f;
    }

    /**
     * 计算物品位置
     */
    private Vector3f calculateItemPosition(TestAnimatedEntity entity) {
        // 计算实体前方的物品位置
        return new Vector3f(0, 1.0f, 0.5f);
    }

    /**
     * 渲染场景（简化版）
     */
    private void renderScene(AssimpScene scene, PoseStack poseStack,
                            MultiBufferSource bufferSource, int packedLight) {
        // 这里应该遍历场景的 meshes 并渲染
        // 参考 AssimpBlockEntityRenderer 的实现
    }

    /**
     * 手动创建 IK 控制器（如果预设失败）
     */
    private void createIKControllersManually() {
        if (currentAnimation == null || currentAnimation.getIKManager() == null) return;

        try {
            // 手动创建头部控制器
            headIK = new HeadLookAtController(
                currentAnimation.getIKManager(),
                "Neck",
                "Head"
            );

            // 手动创建手部控制器
            handIK = new HandIKController(
                currentAnimation.getIKManager(),
                "LeftShoulder", "LeftElbow", "LeftHand",
                "RightShoulder", "RightElbow", "RightHand"
            );

            // 手动创建脚部控制器
            footIK = new FootIKController(
                currentAnimation.getIKManager(),
                "LeftHip", "LeftKnee", "LeftAnkle",
                "RightHip", "RightKnee", "RightAnkle"
            );

            setupIKParameters();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TestAnimatedEntity entity) {
        return new ResourceLocation("assimploader", "textures/entity/character.png");
    }
}
