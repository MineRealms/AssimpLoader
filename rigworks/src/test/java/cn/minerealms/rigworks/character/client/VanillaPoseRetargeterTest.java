package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.AirborneLegPose;
import cn.minerealms.rigworks.character.core.VanillaPoseSelection;
import cn.minerealms.assimploader.animation.AnimationBlender;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VanillaPoseRetargeterTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/rigworks/models/character/block_player");
    private static final VanillaPoseRetargeter.Part ZERO = part(0, 0, 0);
    private static VanillaPoseRetargeter.Part part(float x, float y, float z) {
        return new VanillaPoseRetargeter.Part(x, y, z, 0, 0, 0);
    }
    private static AssimpScene scene() throws Exception {
        return new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"), ModelImportMode.DYNAMIC);
    }
    private static CharacterProfile profile() throws Exception {
        try (var reader = Files.newBufferedReader(ROOT.resolve("profile.json"))) { return CharacterProfile.read(reader); }
    }
    private static Map<String, VanillaPoseRetargeter.Part> parts() {
        var parts = new LinkedHashMap<String, VanillaPoseRetargeter.Part>();
        for (String role : new String[]{"hips", "head", "left_upper_arm", "right_upper_arm", "left_thigh", "right_thigh"}) parts.put(role, ZERO);
        return parts;
    }
    private static Vector3f axis(PoseGraph graph, String from, String to) {
        return graph.position(to).sub(graph.position(from)).normalize();
    }

    @Test void importedPhysicalHandSidesMatchTheNativeModelBasis() throws Exception {
        var scene = scene();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        for (String limb : new String[]{"UpperArm", "Forearm", "Hand", "Thigh", "Shin", "Foot"}) {
            assertTrue(bind.position(limb + ".L").x > 0, "Native left must occupy +X: " + limb);
            assertTrue(bind.position(limb + ".R").x < 0, "Native right must occupy -X: " + limb);
        }
    }

    @Test void nativePoseSamplerPreservesStandardAndSlimShoulderReferenceOffsets() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        float pixelHeight = bind.position("Head").distance(bind.position("Hips")) / 12;
        for (boolean slim : new boolean[]{false, true}) {
            var source = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            var sampler = VanillaPlayerPoseDriver.createPoseModel(source, slim);
            assertNotSame(source.leftArm, sampler.leftArm, "Sampling may not mutate the renderer's arm");
            assertEquals(source.leftArm.getInitialPose().y, sampler.leftArm.getInitialPose().y, 0);
            assertEquals(slim ? 2.5f : 2f, sampler.leftArm.getInitialPose().y, 0);
            sampler.leftArm.y = sampler.rightArm.y = 2;
            var values = parts();
            values.put("left_upper_arm", VanillaPlayerPoseDriver.part(sampler.leftArm));
            values.put("right_upper_arm", VanillaPlayerPoseDriver.part(sampler.rightArm));
            float expectedPixels = slim ? -.5f : 0;
            assertEquals(expectedPixels, values.get("left_upper_arm").y(), 0);
            var locals = new LinkedHashMap<String, Matrix4f>();
            var retargeter = new VanillaPoseRetargeter(profile, scene);
            retargeter.update(new Matrix4f(), values); retargeter.apply(scene, locals, 0);
            var graph = new PoseGraph(scene.getRootNode(), locals);
            for (String side : List.of(".L", ".R")) {
                assertEquals(-expectedPixels * pixelHeight,
                        graph.position("UpperArm" + side).y - bind.position("UpperArm" + side).y, 1e-5);
            }
            assertEquals(slim ? 2.5f : 2f, source.leftArm.y, 0, "The original renderer pose is untouched");
        }
    }

    @Test void realFbxUsesVanillaDownwardPitchWithoutImportedBoneRollInversion() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var localFace = bind.global("Head").invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
        for (float pitch : new float[]{-1.2f, 0, 1.2f}) for (float yaw : new float[]{-.8f, 0, .8f}) {
            var retarget = new VanillaPoseRetargeter(profile, scene);
            var values = parts(); values.put("head", part(pitch, yaw, 0));
            var locals = new LinkedHashMap<String, Matrix4f>();
            retarget.update(new Matrix4f(), values); retarget.apply(scene, locals, 0);
            var face = new PoseGraph(scene.getRootNode(), locals).global("Head").transformDirection(new Vector3f(localFace)).normalize();
            var expected = new Quaternionf().rotationYXZ(-yaw, pitch, 0).transform(new Vector3f(0, 0, 1));
            assertEquals(0, face.distance(expected), 1e-5, "Visible face should follow Minecraft pitch/yaw");
        }
    }

    @Test void nativeIndependentLimbRotationsDoNotDoubleTheTorsoTwistOrShortenLimbs() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var values = parts(); values.put("hips", part(.5f, .7f, 0));
        values.put("right_upper_arm", part(-(float)Math.PI / 2, .35f, -.25f));
        values.put("left_thigh", part(.25f, 0, 0));
        var locals = new LinkedHashMap<String, Matrix4f>();
        var retarget = new VanillaPoseRetargeter(profile, scene);
        retarget.update(new Matrix4f(), values); retarget.apply(scene, locals, 0);
        var graph = new PoseGraph(scene.getRootNode(), locals);
        var arm = axis(graph, "UpperArm.R", "Forearm.R");
        var expected = new Quaternionf().rotationZYX(.25f, -.35f, -(float)Math.PI / 2)
                .transform(axis(bind, "UpperArm.R", "Forearm.R"));
        assertEquals(0, arm.distance(expected), 1e-5, "Arm angles are independent vanilla model parts, not relative to body");
        assertTrue(arm.z > .8f, "Native attack arm should reach in front of the visible +Z face: " + arm + " bind=" + axis(bind, "UpperArm.R", "Forearm.R"));
        for (String[] chain : new String[][]{{"UpperArm.R", "Forearm.R"}, {"Forearm.R", "Hand.R"},
                {"Thigh.L", "Shin.L"}, {"Shin.L", "Foot.L"}}) {
            assertEquals(bind.position(chain[0]).distance(bind.position(chain[1])),
                    graph.position(chain[0]).distance(graph.position(chain[1])), 1e-5, "Retargeting must preserve rig bone length");
        }
        var legExpected = new Quaternionf().rotationX(.25f).transform(axis(bind, "Thigh.L", "Shin.L"));
        assertEquals(0, axis(graph, "Thigh.L", "Shin.L").distance(legExpected), 1e-5);
    }

    @Test void articulatedAirborneKneesBendRelativeToThighsWithoutStretching() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var values = parts();
        for (String side : List.of("left", "right")) {
            values.put(side + "_thigh", part(-.25f, 0, 0));
            values.put(side + "_shin", part(.5f, 0, 0));
        }
        var locals = new LinkedHashMap<String, Matrix4f>();
        var retarget = new VanillaPoseRetargeter(profile, scene);
        retarget.update(new Matrix4f(), values); retarget.apply(scene, locals, 0);
        var graph = new PoseGraph(scene.getRootNode(), locals);
        for (String side : List.of(".L", ".R")) {
            var calf = axis(graph, "Shin" + side, "Foot" + side);
            var expected = new Quaternionf().rotationX(.25f).transform(axis(bind, "Shin" + side, "Foot" + side));
            assertEquals(0, calf.distance(expected), 1e-5, "Knee rotation must accumulate with the thigh's rotation");
            for (String[] chain : new String[][]{{"Thigh", "Shin"}, {"Shin", "Foot"}})
                assertEquals(bind.position(chain[0] + side).distance(bind.position(chain[1] + side)),
                        graph.position(chain[0] + side).distance(graph.position(chain[1] + side)), 1e-5);
        }
    }

    @Test void bothImportedArmChainsMatchNativeModelPartMatricesAcrossFullEulerTurns() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var basis = new Matrix4f().rotateX((float)Math.PI);
        float vertical = bind.position("Head").distance(bind.position("Hips")) / 12;
        float horizontal = bind.position("UpperArm.L").distance(bind.position("UpperArm.R")) / 10;
        var retarget = new VanillaPoseRetargeter(profile, scene);
        for (int phase = 0; phase <= 26; phase++) {
            var values = parts();
            values.put("hips", part(.4f, -.65f, .15f));
            var nativeParts = new LinkedHashMap<String, ModelPart>();
            for (String side : List.of("left", "right")) {
                float mirror = side.equals("left") ? 1 : -1;
                float angle = (float)(2 * Math.PI * phase / 26);
                var value = new VanillaPoseRetargeter.Part(angle - (float)Math.PI, mirror * .85f * (float)Math.sin(angle),
                        mirror * angle, mirror * .35f, 1.2f, -.8f);
                values.put(side + "_upper_arm", value);
                ModelPart nativePart = new ModelPart(List.of(), Map.of());
                nativePart.xRot = value.pitch(); nativePart.yRot = value.yaw(); nativePart.zRot = value.roll();
                nativeParts.put(side, nativePart);
            }
            var root = new Matrix4f().translate(.2f, -.1f, .3f).rotateX(.7f).rotateY(-.3f);
            var locals = new LinkedHashMap<String, Matrix4f>();
            retarget.update(root, values); retarget.apply(scene, locals, 0);
            var graph = new PoseGraph(scene.getRootNode(), locals);
            for (String side : List.of("left", "right")) {
                String suffix = side.equals("left") ? ".L" : ".R";
                String upper = "UpperArm" + suffix;
                Vector3f origin = bind.position(upper);
                var value = values.get(side + "_upper_arm");
                Vector3f pivot = basis.transformDirection(new Vector3f(value.x() * horizontal, value.y() * vertical, value.z() * vertical));
                var nativeStack = new PoseStack(); nativeParts.get(side).translateAndRotate(nativeStack);
                for (String endpoint : List.of(upper, "Forearm" + suffix, "Hand" + suffix)) {
                    Vector3f offset = basis.invert(new Matrix4f()).transformDirection(bind.position(endpoint).sub(origin));
                    nativeStack.last().pose().transformDirection(offset);
                    basis.transformDirection(offset);
                    Vector3f expected = root.transformPosition(new Vector3f(origin).add(pivot).add(offset));
                    assertEquals(0, graph.position(endpoint).distance(expected), 2e-5,
                            "Imported endpoint must match the original independent ModelPart matrix: " + endpoint + " phase=" + phase);
                }
                for (String[] segment : new String[][]{{upper, "Forearm" + suffix}, {"Forearm" + suffix, "Hand" + suffix}}) {
                    assertEquals(bind.position(segment[0]).distance(bind.position(segment[1])),
                            graph.position(segment[0]).distance(graph.position(segment[1])), 2e-5,
                            "Part rotations cannot stretch or shorten either arm segment");
                }
            }
        }
    }

    @Test void rendererRootPitchAffectsBodyEquipmentAndAllLimbsInOnePose() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        for (float pitch : new float[]{-90, -60, 0, 60, 90}) {
            // Vanilla's -90-pitch renderer turn becomes +90+pitch in imported model axes.
            var root = new Matrix4f().rotateX((float)Math.toRadians(90 + pitch));
            var locals = new LinkedHashMap<String, Matrix4f>();
            var retarget = new VanillaPoseRetargeter(profile, scene);
            retarget.update(root, parts()); retarget.apply(scene, locals, 0);
            var graph = new PoseGraph(scene.getRootNode(), locals);
            assertEquals(0, axis(graph, "Hips", "Head").distance(root.transformDirection(new Vector3f(0, 1, 0))), 1e-5);
            var hand = root.transformPosition(bind.position("Hand.R"));
            assertEquals(0, graph.position("Hand.R").distance(hand), 1e-5, "Held-item mount shares the same root pitch");
        }
    }

    @Test void disablingDoesNotOverwriteTheClipPose() throws Exception {
        var scene = scene(); var retarget = new VanillaPoseRetargeter(profile(), scene);
        var locals = new LinkedHashMap<String, Matrix4f>();
        locals.put("Hips", new Matrix4f().translate(1, 2, 3));
        retarget.update(new Matrix4f(), parts()); retarget.disable(); retarget.apply(scene, locals, 0);
        assertEquals(Map.of("Hips", new Matrix4f().translate(1, 2, 3)), locals);
    }

    @Test void calibratedNativeNeutralChangesOnlyTheCandidateAndRootStillRotatesTheWholeRig() throws Exception {
        var scene = scene(); var base = profile();
        var mappings = new LinkedHashMap<>(base.humanoid());
        var arm = mappings.get("right_upper_arm");
        mappings.put("right_upper_arm", new CharacterProfile.HumanoidBone(arm.node(), arm.referenceRotation(),
                new CharacterProfile.Rotation(0, 0, (float)Math.sin(.3), (float)Math.cos(.3))));
        var calibrated = new CharacterProfile(base.model(), base.scale(), mappings, base.animations(), base.physicsDefinition(),
                base.blendSeconds(), base.movementThreshold(), base.headLook(), base.solverDefinition(),
                base.modelYawDegrees(), base.equipmentDefinition(), base.timing());
        var imported = SkinBindPose.locals(scene);
        Matrix4f original = new Matrix4f(imported.get(arm.node()));
        var locals = new LinkedHashMap<>(imported);
        var retarget = new VanillaPoseRetargeter(calibrated, scene);
        retarget.update(new Matrix4f(), parts()); retarget.apply(scene, locals, 0);
        assertTrue(new Matrix4f(original).rotateZ(.6f).equals(locals.get(arm.node()), 2e-5f));
        assertEquals(original, SkinBindPose.locals(scene).get(arm.node()), "Calibration cannot alter imported bind data");
        var before = new PoseGraph(scene.getRootNode(), locals).position("Hand.R");
        var root = new Matrix4f().rotateX((float)Math.PI / 2);
        retarget.update(root, parts()); retarget.apply(scene, locals, 0);
        assertEquals(0, new PoseGraph(scene.getRootNode(), locals).position("Hand.R")
                .distance(root.transformPosition(before)), 2e-5, "Elytra root turn must include the calibrated hand");
        retarget.disable();
        locals.put(arm.node(), new Matrix4f(original));
        retarget.apply(scene, locals, 0);
        assertEquals(original, locals.get(arm.node()), "Authored clips retain their own pose when native channels are off");
    }

    @Test void nativeCalibrationPreservesTranslationAndNonUniformReflectedScale() {
        var orientation = new Quaternionf().rotationXYZ(.3f, -.4f, .5f);
        var correction = new CharacterProfile.Rotation(0, 0, (float)Math.sin(.3), (float)Math.cos(.3));
        var local = new Matrix4f().translationRotateScale(new Vector3f(2, 3, 4), orientation, new Vector3f(-2, 3, .5f));
        var actual = VanillaPoseRetargeter.calibratedLocal(local, correction);
        var expected = new Matrix4f().translationRotateScale(new Vector3f(2, 3, 4),
                new Quaternionf(orientation).mul(correction.quaternion()), new Vector3f(-2, 3, .5f));
        assertTrue(expected.equals(actual, 2e-6f));
        assertTrue(actual.determinant3x3() < 0);
        assertEquals(new Vector3f(2, 3, 4), actual.getTranslation(new Vector3f()));
        assertEquals(local, VanillaPoseRetargeter.calibratedLocal(local, CharacterProfile.Rotation.IDENTITY));
    }

    @Test void selectedArmPreservesEveryUnselectedLocalAndResetsItsOwnForearmOnly() throws Exception {
        var scene = scene(); var profile = profile();
        var locals = new LinkedHashMap<>(SkinBindPose.locals(scene));
        locals.get("Head").rotateX(.7f);
        locals.get("Forearm.R").rotateZ(.8f);
        locals.get("Forearm.L").rotateZ(-.6f);
        var before = new LinkedHashMap<String, Matrix4f>();
        locals.forEach((name, matrix) -> before.put(name, new Matrix4f(matrix)));
        var options = VanillaPoseSelection.fromMap(Map.of("root",0,"body",0,"head",0,"leftArm",0,"rightArm",1,"leftLeg",0,"rightLeg",0));
        var values = parts(); values.put("right_upper_arm", part(-1.2f,.3f,.2f));
        var retargeter = new VanillaPoseRetargeter(profile, scene);
        retargeter.update(new Matrix4f().rotateX(.9f), values, options);
        retargeter.apply(scene,locals,0);
        for (var entry : before.entrySet()) {
            if (!List.of("UpperArm.R","Forearm.R","Hand.R").contains(entry.getKey()))
                assertEquals(entry.getValue(),locals.get(entry.getKey()),"Unselected local: "+entry.getKey());
        }
        assertEquals(SkinBindPose.locals(scene).get("Forearm.R"),locals.get("Forearm.R"));
        assertFalse(before.get("UpperArm.R").equals(locals.get("UpperArm.R")));
    }

    @Test void partialArmKeepsAuthoredShoulderPivotAndFollowsTheAnimatedTorsoFrame() throws Exception {
        var scene = scene(); var base = profile();
        var mappings = new LinkedHashMap<>(base.humanoid());
        mappings.remove("chest"); // This remains an animated helper parent, like an imported clavicle.
        var profile = new CharacterProfile(base.model(), base.scale(), mappings, base.animations(), base.physicsDefinition(),
                base.blendSeconds(), base.movementThreshold(), base.headLook(), base.solverDefinition(),
                base.modelYawDegrees(), base.equipmentDefinition(), base.timing());
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var locals = new LinkedHashMap<>(SkinBindPose.locals(scene));
        locals.put("Chest", new Matrix4f(locals.get("Chest")).rotateY(.6f).rotateZ(.2f));
        locals.put("UpperArm.R", new Matrix4f(locals.get("UpperArm.R")).translate(.03f, .01f, -.02f));
        var authored = new PoseGraph(scene.getRootNode(), locals);
        var shoulder = authored.position("UpperArm.R");
        Quaternionf torsoDelta = authored.global("Chest").getUnnormalizedRotation(new Quaternionf()).normalize()
                .mul(bind.global("Chest").getUnnormalizedRotation(new Quaternionf()).normalize().invert());
        var values = parts();
        var options = VanillaPoseSelection.fromMap(Map.of("root",0,"body",0,"head",0,"leftArm",0,
                "rightArm",1,"leftLeg",0,"rightLeg",0));
        var retargeter = new VanillaPoseRetargeter(profile, scene);
        retargeter.update(new Matrix4f(), values, options); retargeter.apply(scene, locals, 0);
        var composed = new PoseGraph(scene.getRootNode(), locals);
        assertEquals(0, shoulder.distance(composed.position("UpperArm.R")), 2e-5,
                "An item-arm overlay cannot relocate an authored shoulder joint");
        assertEquals(0, torsoDelta.transform(axis(bind,"UpperArm.R","Forearm.R"))
                .distance(axis(composed,"UpperArm.R","Forearm.R")), 2e-5);
    }

    @Test void rootOnlyRotatesTheIncomingAnimatedSkeletonWithoutResettingItsLocals() throws Exception {
        var scene = scene(); var profile = profile();
        var locals = new LinkedHashMap<>(SkinBindPose.locals(scene));
        locals.get("Forearm.R").rotateX(.9f);
        locals.get("Hips").translate(.1f,.2f,-.3f);
        var before = new PoseGraph(scene.getRootNode(),locals).position("Hand.R");
        var forearm = new Matrix4f(locals.get("Forearm.R"));
        var root = new Matrix4f().rotateX(.7f);
        var options = VanillaPoseSelection.fromMap(Map.of("body",0,"head",0,"leftArm",0,"rightArm",0,"leftLeg",0,"rightLeg",0));
        var retargeter = new VanillaPoseRetargeter(profile,scene);
        retargeter.update(root,parts(),options);retargeter.apply(scene,locals,0);
        assertEquals(forearm,locals.get("Forearm.R"));
        assertEquals(0,new PoseGraph(scene.getRootNode(),locals).position("Hand.R").distance(root.transformPosition(before)),2e-5);
    }

    @Test void channelHalfWeightBlendsLocalTrsAndZeroWeightIsAnExactNoOp() throws Exception {
        var scene = scene();var profile = profile();
        var values = parts(); values.put("head",part(.9f,.4f,0));
        var full = new LinkedHashMap<>(SkinBindPose.locals(scene));
        var half = new LinkedHashMap<>(SkinBindPose.locals(scene));
        var zero = new LinkedHashMap<>(SkinBindPose.locals(scene));
        var initial = new Matrix4f(zero.get("Head")).rotateZ(.3f);
        full.put("Head",new Matrix4f(initial));half.put("Head",new Matrix4f(initial));zero.put("Head",new Matrix4f(initial));
        var retargeter = new VanillaPoseRetargeter(profile,scene);
        retargeter.update(new Matrix4f(),values,VanillaPoseSelection.fromMap(Map.of("head",1,"root",0)));retargeter.apply(scene,full,0);
        retargeter.update(new Matrix4f(),values,VanillaPoseSelection.fromMap(Map.of("head",.5,"root",0)));retargeter.apply(scene,half,0);
        retargeter.update(new Matrix4f(),values,VanillaPoseSelection.fromMap(Map.of("head",0,"root",0)));retargeter.apply(scene,zero,0);
        assertTrue(AnimationBlender.blendTransforms(initial,full.get("Head"),.5f).equals(half.get("Head"),1e-6f));
        assertEquals(initial,zero.get("Head"));
    }

    @Test void originalCrouchAndAttackPivotOffsetsAreScaledFromTheRigsOwnProportions() throws Exception {
        var scene = scene(); var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var values = parts();
        values.put("hips", new VanillaPoseRetargeter.Part(.5f, .3f, 0, 0, 3.2f, 0));
        values.put("head", new VanillaPoseRetargeter.Part(0, 0, 0, 0, 4.2f, 0));
        values.put("right_upper_arm", new VanillaPoseRetargeter.Part(.4f, 0, 0, .4f, 3.2f, 2));
        values.put("left_thigh", new VanillaPoseRetargeter.Part(0, 0, 0, 0, .2f, 4));
        var retarget = new VanillaPoseRetargeter(profile(), scene);
        var locals = new LinkedHashMap<String, Matrix4f>();
        retarget.update(new Matrix4f(), values); retarget.apply(scene, locals, 0);
        var graph = new PoseGraph(scene.getRootNode(), locals);
        float vertical = bind.position("Head").distance(bind.position("Hips")) / 12;
        float horizontal = bind.position("UpperArm.L").distance(bind.position("UpperArm.R")) / 10;
        assertEquals(0, graph.position("Head").distance(bind.position("Head").add(0, -4.2f * vertical, 0)), 1e-5);
        assertEquals(0, graph.position("UpperArm.R").distance(bind.position("UpperArm.R").add(.4f * horizontal, -3.2f * vertical, -2 * vertical)), 1e-5);
        assertEquals(0, graph.position("Thigh.L").distance(bind.position("Thigh.L").add(0, -.2f * vertical, -4 * vertical)), 1e-5);
        assertEquals(bind.position("UpperArm.R").distance(bind.position("Forearm.R")),
                graph.position("UpperArm.R").distance(graph.position("Forearm.R")), 1e-5);
        assertTrue(graph.position("Hips").z < -.1, "Crouch torso pivots around the native body origin near the neck");
    }

    @Test void articulatedKneeKeepsItsPivotAndItsBendPlaneFollowsTheThigh() throws Exception {
        var scene = scene(); var profile = profile();
        var values = parts(); values.put("left_shin", part(.5f, 0, 0));
        var retargeter = new VanillaPoseRetargeter(profile, scene);
        var straightThigh = new LinkedHashMap<>(SkinBindPose.locals(scene));
        retargeter.update(new Matrix4f(), values); retargeter.apply(scene, straightThigh, 0);
        var base = new PoseGraph(scene.getRootNode(), straightThigh);
        values.put("left_thigh", part(-.25f, .8f, -.3f));
        var posedThigh = new LinkedHashMap<>(SkinBindPose.locals(scene));
        retargeter.update(new Matrix4f(), values); retargeter.apply(scene, posedThigh, 0);
        var posed = new PoseGraph(scene.getRootNode(), posedThigh);
        var thighDelta = posed.global("Thigh.L").mul(base.global("Thigh.L").invert());
        for (String endpoint : List.of("Shin.L", "Foot.L")) {
            assertEquals(0, posed.position(endpoint).distance(thighDelta.transformPosition(base.position(endpoint))), 2e-5,
                    "The bent knee and ankle must inherit thigh yaw/roll: " + endpoint);
        }
        assertTrue(straightThigh.get("Shin.L").equals(posedThigh.get("Shin.L"), 2e-5f));
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        assertEquals(bind.position("Shin.L").distance(bind.position("Foot.L")),
                posed.position("Shin.L").distance(posed.position("Foot.L")), 2e-5);
        values.put("left_shin", ZERO);
        var unbent = new LinkedHashMap<>(SkinBindPose.locals(scene));
        retargeter.update(new Matrix4f(), values); retargeter.apply(scene, unbent, 0);
        assertEquals(0, new PoseGraph(scene.getRootNode(), unbent).position("Shin.L").distance(posed.position("Shin.L")), 2e-5,
                "Bending cannot move the knee joint itself");
    }

    @Test void profilesWithoutBothMappedShinsKeepTheirExistingNativeLegBehavior() throws Exception {
        var scene = scene(); var base = profile();
        assertTrue(VanillaPlayerPoseDriver.supportsArticulatedLegs(base));
        for (String missing : List.of("left_shin", "right_shin", "left_thigh", "right_thigh")) {
            var mappings = new LinkedHashMap<>(base.humanoid()); mappings.remove(missing);
            var profile = new CharacterProfile(base.model(), base.scale(), mappings, base.animations(), base.physicsDefinition(),
                    base.blendSeconds(), base.movementThreshold(), base.headLook(), base.solverDefinition(),
                    base.modelYawDegrees(), base.equipmentDefinition(), base.timing());
            assertFalse(VanillaPlayerPoseDriver.supportsArticulatedLegs(profile), missing);
        }
        var values = parts(); values.put("left_thigh", part(-.3f, .2f, 0));
        var retargeter = new VanillaPoseRetargeter(base, scene);
        var expected = new LinkedHashMap<>(SkinBindPose.locals(scene));
        retargeter.update(new Matrix4f(), values); retargeter.apply(scene, expected, 0);
        values.put("left_shin", ZERO); values.put("right_shin", ZERO);
        var actual = new LinkedHashMap<>(SkinBindPose.locals(scene));
        retargeter.update(new Matrix4f(), values); retargeter.apply(scene, actual, 0);
        for (var entry : expected.entrySet()) assertTrue(entry.getValue().equals(actual.get(entry.getKey()), 2e-6f), entry.getKey());
    }

    @Test void scriptedFrameDisablePreservesContactHistoryButCloseResetsIt() throws Exception {
        var scene = scene();
        try (var animation = new AnimationState("scripted-air-contact", scene);
             var driver = new VanillaPlayerPoseDriver(profile(), animation)) {
            var field = VanillaPlayerPoseDriver.class.getDeclaredField("airborne");
            field.setAccessible(true);
            var contact = (AirborneLegPose) field.get(driver);
            for (int tick = 0; tick < 8; tick++) {
                driver.disable();
                contact.update(tick, false, true);
            }
            assertEquals(1, contact.update(8, false, true), "Per-tick script dispatch cannot restart the airborne blend");
            driver.close();
            assertEquals(0, contact.update(9, true, true), "A closed player binding releases its contact state");
        }
    }
}
