package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.ik.controllers.FootIKController;
import cn.minerealms.assimploader.animation.ik.controllers.HandIKController;
import cn.minerealms.assimploader.animation.ik.controllers.HeadLookAtController;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * IKPresets - Pre-configured IK setups for common skeleton types.
 *
 * Provides quick setup for:
 * - Humanoid (bipedal with arms)
 * - Quadruped (4-legged animals)
 * - Spider (8-legged creatures)
 *
 * Phase 5: IK Presets
 */
public class IKPresets {

    /**
     * Humanoid preset configuration
     */
    public static class HumanoidConfig {
        public String neck = "Neck";
        public String head = "Head";
        public String leftShoulder = "LeftShoulder";
        public String leftElbow = "LeftElbow";
        public String leftHand = "LeftHand";
        public String rightShoulder = "RightShoulder";
        public String rightElbow = "RightElbow";
        public String rightHand = "RightHand";
        public String leftHip = "LeftHip";
        public String leftKnee = "LeftKnee";
        public String leftAnkle = "LeftAnkle";
        public String rightHip = "RightHip";
        public String rightKnee = "RightKnee";
        public String rightAnkle = "RightAnkle";
    }

    /**
     * Humanoid IK setup result
     */
    public static class HumanoidIK {
        public final HeadLookAtController head;
        public final HandIKController hands;
        public final FootIKController feet;

        public HumanoidIK(HeadLookAtController head, HandIKController hands, FootIKController feet) {
            this.head = head;
            this.hands = hands;
            this.feet = feet;
        }
    }

    /**
     * Create a humanoid IK setup with default bone names
     */
    public static HumanoidIK createHumanoid(IKManager ikManager) {
        return createHumanoid(ikManager, new HumanoidConfig());
    }

    /**
     * Create a humanoid IK setup with custom bone names
     */
    public static HumanoidIK createHumanoid(IKManager ikManager, HumanoidConfig config) {
        HeadLookAtController head = new HeadLookAtController(
            ikManager, config.neck, config.head
        );

        HandIKController hands = new HandIKController(
            ikManager,
            config.leftShoulder, config.leftElbow, config.leftHand,
            config.rightShoulder, config.rightElbow, config.rightHand
        );

        FootIKController feet = new FootIKController(
            ikManager,
            config.leftHip, config.leftKnee, config.leftAnkle,
            config.rightHip, config.rightKnee, config.rightAnkle
        );

        return new HumanoidIK(head, hands, feet);
    }

    /**
     * Quadruped preset configuration
     */
    public static class QuadrupedConfig {
        public String neck = "Neck";
        public String head = "Head";
        public String frontLeftShoulder = "FrontLeftShoulder";
        public String frontLeftElbow = "FrontLeftElbow";
        public String frontLeftPaw = "FrontLeftPaw";
        public String frontRightShoulder = "FrontRightShoulder";
        public String frontRightElbow = "FrontRightElbow";
        public String frontRightPaw = "FrontRightPaw";
        public String backLeftHip = "BackLeftHip";
        public String backLeftKnee = "BackLeftKnee";
        public String backLeftPaw = "BackLeftPaw";
        public String backRightHip = "BackRightHip";
        public String backRightKnee = "BackRightKnee";
        public String backRightPaw = "BackRightPaw";
        public String tail1 = null; // Optional
        public String tail2 = null;
        public String tail3 = null;
    }

    /**
     * Quadruped IK setup result
     */
    public static class QuadrupedIK {
        public final HeadLookAtController head;
        public final Map<String, IKChain> legs;
        @Nullable
        public final IKChain tail;

        public QuadrupedIK(HeadLookAtController head, Map<String, IKChain> legs, @Nullable IKChain tail) {
            this.head = head;
            this.legs = legs;
            this.tail = tail;
        }
    }

    /**
     * Create a quadruped IK setup
     */
    public static QuadrupedIK createQuadruped(IKManager ikManager, QuadrupedConfig config) {
        HeadLookAtController head = new HeadLookAtController(
            ikManager, config.neck, config.head
        );

        Map<String, IKChain> legs = new HashMap<>();

        // Front left leg
        legs.put("frontLeft", ikManager.createChainFromRange(
            "frontLeftLeg",
            config.frontLeftShoulder,
            config.frontLeftPaw,
            IKSolver.Type.TWO_BONE
        ));

        // Front right leg
        legs.put("frontRight", ikManager.createChainFromRange(
            "frontRightLeg",
            config.frontRightShoulder,
            config.frontRightPaw,
            IKSolver.Type.TWO_BONE
        ));

        // Back left leg
        legs.put("backLeft", ikManager.createChainFromRange(
            "backLeftLeg",
            config.backLeftHip,
            config.backLeftPaw,
            IKSolver.Type.TWO_BONE
        ));

        // Back right leg
        legs.put("backRight", ikManager.createChainFromRange(
            "backRightLeg",
            config.backRightHip,
            config.backRightPaw,
            IKSolver.Type.TWO_BONE
        ));

        // Optional tail
        IKChain tail = null;
        if (config.tail1 != null && config.tail2 != null && config.tail3 != null) {
            tail = ikManager.createChainFromRange(
                "tail",
                config.tail1,
                config.tail3,
                IKSolver.Type.FABRIK
            );
        }

        return new QuadrupedIK(head, legs, tail);
    }

    /**
     * Spider preset configuration (8 legs)
     */
    public static class SpiderConfig {
        public String[] legRoots = new String[8];
        public String[] legMids = new String[8];
        public String[] legTips = new String[8];

        public SpiderConfig() {
            // Default names
            for (int i = 0; i < 8; i++) {
                legRoots[i] = "Leg" + i + "Root";
                legMids[i] = "Leg" + i + "Mid";
                legTips[i] = "Leg" + i + "Tip";
            }
        }
    }

    /**
     * Spider IK setup result
     */
    public static class SpiderIK {
        public final Map<String, IKChain> legs;

        public SpiderIK(Map<String, IKChain> legs) {
            this.legs = legs;
        }

        /**
         * Enable all legs
         */
        public void enableAll() {
            for (IKChain leg : legs.values()) {
                leg.setEnabled(true);
            }
        }

        /**
         * Disable all legs
         */
        public void disableAll() {
            for (IKChain leg : legs.values()) {
                leg.setEnabled(false);
            }
        }

        /**
         * Set weight for all legs
         */
        public void setAllWeights(float weight) {
            for (IKChain leg : legs.values()) {
                leg.setWeight(weight);
            }
        }
    }

    /**
     * Create a spider IK setup (8 legs)
     */
    public static SpiderIK createSpider(IKManager ikManager, SpiderConfig config) {
        Map<String, IKChain> legs = new HashMap<>();

        for (int i = 0; i < 8; i++) {
            String legName = "leg" + i;
            IKChain leg = ikManager.createChainFromRange(
                legName,
                config.legRoots[i],
                config.legTips[i],
                IKSolver.Type.TWO_BONE
            );
            legs.put(legName, leg);
        }

        return new SpiderIK(legs);
    }

    /**
     * Auto-detect skeleton type and create appropriate preset.
     * Returns null if skeleton type cannot be determined.
     */
    @Nullable
    public static Object autoDetect(IKManager ikManager) {
        Skeleton skeleton = ikManager.getSkeleton();

        // Try humanoid detection
        if (skeleton.getBone("LeftHand") != null && skeleton.getBone("RightHand") != null &&
            skeleton.getBone("LeftAnkle") != null && skeleton.getBone("RightAnkle") != null) {
            return createHumanoid(ikManager);
        }

        // Try quadruped detection
        if (skeleton.getBone("FrontLeftPaw") != null && skeleton.getBone("BackLeftPaw") != null) {
            return createQuadruped(ikManager, new QuadrupedConfig());
        }

        // Try spider detection
        if (skeleton.getBone("Leg0Root") != null && skeleton.getBone("Leg7Root") != null) {
            return createSpider(ikManager, new SpiderConfig());
        }

        return null;
    }
}
