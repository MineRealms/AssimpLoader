package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.mixin.character.PlayerRendererPoseAccess;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit client-world probe. Its unregistered player never sends input, ticks, joins a level or touches a save. */
public final class VanillaRuntimeProbe {
    private VanillaRuntimeProbe() {}

    public static Map<String, Object> verify(LocalPlayer real, CharacterProfile profile, AssimpScene scene) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || real != minecraft.player || minecraft.level == null)
            throw new IllegalStateException("Vanilla runtime probe requires the active player on the client thread");
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(real);
        if (!(renderer instanceof PlayerRenderer vanilla) || !(vanilla instanceof PlayerRendererPoseAccess access))
            throw new IllegalStateException("PlayerRendererPoseAccess mixin did not load");
        if (!profile.humanoid().keySet().containsAll(List.of("hips", "head", "left_upper_arm", "right_upper_arm", "left_thigh", "right_thigh")))
            throw new IllegalArgumentException("Vanilla probe requires the complete humanoid fixture mapping");
        var before = real.position(); float beforePitch = real.getXRot(); float beforeYaw = real.getYRot();
        var beforePose = real.getPose(); var beforeItem = real.getMainHandItem().copy();
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("mixin", vanilla.getClass().getName());
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var nativeBasisInverse = new Matrix4f().rotateY((float)Math.toRadians(-profile.modelYawDegrees()))
                .rotateX((float)Math.PI).invert();
        for (String limb : List.of("upper_arm", "forearm", "hand", "thigh", "shin", "foot")) {
            Vector3f left = bind.position(profile.bone("left_" + limb).node());
            Vector3f right = bind.position(profile.bone("right_" + limb).node());
            float separation = nativeBasisInverse.transformDirection(left.sub(right)).x;
            require(separation > 0, "Imported anatomical sides disagree with native left/right: " + limb + " separation=" + separation);
        }
        checks.put("importedHandedness", "All six paired arm and leg joints place anatomical left on native +X");
        Vector3f faceLocal = bind.global(profile.bone("head").node()).invert()
                .transformDirection(new Vector3f(0, 0, 1)).normalize();
        ProbePlayer player = new ProbePlayer(minecraft, real);
        String originalSkin = player.getModelName();
        try (var animation = new AnimationState("vanilla-runtime-probe", scene);
             var driver = new VanillaPlayerPoseDriver(profile, animation)) {
            PoseSnapshot neutral = sample(player, driver, animation);
            List<Object> headSamples = new ArrayList<>();
            for (float pitch : new float[]{-60, 60}) {
                player.reset(); player.setXRot(pitch);
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f face = pose.getNodeTransform(profile.bone("head").node()).transformDirection(new Vector3f(faceLocal)).normalize();
                float expectedY = -(float)Math.sin(Math.toRadians(pitch));
                require(Math.abs(face.y - expectedY) < .002f, "Visible head pitch is reversed or missing: " + face);
                headSamples.add(Map.of("pitch", pitch, "face", vector(face)));
            }
            checks.put("headPitch", headSamples);

            player.reset(); player.crouching = true;
            PoseSnapshot crouch = sample(player, driver, animation);
            float headDrop = position(neutral, profile, "head").y - position(crouch, profile, "head").y;
            require(headDrop > .1f, "Original crouch head pivot was not retargeted: " + headDrop);
            checks.put("crouch", Map.of("headDrop", headDrop, "hips", vector(position(crouch, profile, "hips"))));

            player.reset(); player.setWalk(2, 1);
            PoseSnapshot walkA = sample(player, driver, animation);
            player.setWalk(7, 1);
            PoseSnapshot walkB = sample(player, driver, animation);
            float walkChange = limbAxis(walkA, profile, "left_thigh", "left_shin")
                    .distance(limbAxis(walkB, profile, "left_thigh", "left_shin"));
            require(walkChange > .1f, "Actual walk animation did not reach the leg skeleton");
            checks.put("walk", Map.of("legDirectionChange", walkChange));

            List<Vector3f> armStroke = new ArrayList<>(), legKick = new ArrayList<>();
            List<Object> swimSamples = new ArrayList<>();
            float maximumSwimGeometryError = 0;
            for (String skin : List.of("default", "slim")) for (int phase = 0; phase <= 26; phase++) {
                player.modelName = skin;
                player.reset(); player.swimming = player.inWater = true; player.setWalk(phase, 1);
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f arm = limbAxis(pose, profile, "right_upper_arm", "right_forearm");
                Vector3f leg = limbAxis(pose, profile, "left_thigh", "left_shin");
                Vector3f otherLeg = limbAxis(pose, profile, "right_thigh", "right_shin");
                require(Math.abs(bodyAxis(pose, profile).y) < .002f, "Swimming root stayed upright");
                float geometryError = verifyNativeGeometry(player, profile, bind, pose);
                maximumSwimGeometryError = Math.max(maximumSwimGeometryError, geometryError);
                armStroke.add(arm); legKick.add(leg);
                Vector3f leftHand = position(pose, profile, "left_hand");
                Vector3f rightHand = position(pose, profile, "right_hand");
                swimSamples.add(Map.of("skin", skin, "phase", phase, "arm", vector(arm), "leftLeg", vector(leg), "rightLeg", vector(otherLeg),
                        "leftHand", vector(leftHand), "rightHand", vector(rightHand), "handSeparation", leftHand.distance(rightHand),
                        "maximumNativeGeometryError", geometryError));
            }
            player.modelName = originalSkin;
            require(spread(armStroke) > .3f, "Original swimming arm stroke did not reach the rig");
            require(spread(legKick) > .1f, "Original swimming leg kick did not reach the rig");
            checks.put("swim", Map.of("samples", swimSamples, "armDirectionChange", spread(armStroke), "legDirectionChange", spread(legKick),
                    "maximumNativeGeometryError", maximumSwimGeometryError,
                    "criterion", "Both arm and leg endpoints match native independent ModelPart transforms through the complete 26-tick stroke; native crossing is allowed"));

            List<Object> flightSamples = new ArrayList<>();
            for (float pitch : new float[]{-60, 0, 60}) {
                player.reset(); player.fallFlying = true; player.setXRot(pitch); player.setDeltaMovement(0, -.3, 1);
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f body = bodyAxis(pose, profile);
                Vector3f expected = new Vector3f(0, -(float)Math.sin(Math.toRadians(pitch)), (float)Math.cos(Math.toRadians(pitch)));
                require(body.distance(expected) < .002f, "Elytra root did not follow the actual dive pitch: " + body);
                flightSamples.add(Map.of("pitch", pitch, "body", vector(body)));
            }
            checks.put("elytra", flightSamples);

            player.reset(); player.vehicle = new Boat(EntityType.BOAT, minecraft.level);
            PoseSnapshot ride = sample(player, driver, animation);
            Vector3f rideLeg = limbAxis(ride, profile, "right_thigh", "right_shin");
            require(rideLeg.z > .75f, "Original mounted leg pose was not selected");
            checks.put("ride", Map.of("rightLeg", vector(rideLeg)));

            player.reset(); player.setPose(Pose.SLEEPING);
            PoseSnapshot sleep = sample(player, driver, animation);
            Vector3f sleepBody = bodyAxis(sleep, profile);
            require(Math.abs(sleepBody.y) < .002f, "Original sleeping renderer rotation was not applied");
            checks.put("sleep", Map.of("body", vector(sleepBody), "bedDirection", Direction.EAST.getName()));

            player.reset(); player.main = new ItemStack(Items.DIAMOND_SWORD);
            PoseSnapshot held = sample(player, driver, animation);
            Vector3f idleArm = limbAxis(neutral, profile, "right_upper_arm", "right_forearm");
            Vector3f heldArm = limbAxis(held, profile, "right_upper_arm", "right_forearm");
            require(idleArm.distance(heldArm) > .15f, "Original ITEM arm pose was not selected");
            checks.put("heldItem", Map.of("rightArm", vector(heldArm), "armPose", vanilla.getModel().rightArmPose.name()));

            List<Object> uses = new ArrayList<>();
            for (var entry : Map.of(Items.BOW, "BOW_AND_ARROW", Items.SHIELD, "BLOCK", Items.CROSSBOW, "CROSSBOW_CHARGE").entrySet()) {
                player.reset(); player.main = new ItemStack(entry.getKey()); player.using = true;
                PoseSnapshot pose = sample(player, driver, animation);
                String actual = vanilla.getModel().rightArmPose.name();
                require(actual.equals(entry.getValue()), "Original item action dispatch failed: " + actual + " expected " + entry.getValue());
                Vector3f arm = limbAxis(pose, profile, "right_upper_arm", "right_forearm");
                require(arm.isFinite() && arm.z > .1f, "Item-use arm is not forward or finite: " + arm);
                uses.add(Map.of("armPose", actual, "rightArm", vector(arm)));
            }
            checks.put("itemUse", uses);

            List<Object> twoHanded = new ArrayList<>();
            for (String skin : List.of("default", "slim")) for (HumanoidArm primary : HumanoidArm.values()) for (InteractionHand hand : InteractionHand.values()) {
                player.modelName = skin;
                for (String action : List.of("bow", "crossbow_charge", "crossbow_hold")) {
                    for (int useTicks : new int[]{0, 5, 15, 25}) for (float yaw : new float[]{-35, 35}) {
                        player.reset(); player.primary = primary; player.useHand = hand; player.useTicks = useTicks;
                        ItemStack weapon = new ItemStack(action.equals("bow") ? Items.BOW : Items.CROSSBOW);
                        if (action.equals("crossbow_hold")) CrossbowItem.setCharged(weapon, true);
                        if (hand == InteractionHand.MAIN_HAND) player.main = weapon; else player.off = weapon;
                        player.using = !action.equals("crossbow_hold");
                        player.yHeadRot = player.yHeadRotO = yaw; player.setXRot(yaw > 0 ? 25 : -25);
                        PoseSnapshot pose = sample(player, driver, animation);
                        float geometryError = verifyNativeGeometry(player, profile, bind, pose);
                        HumanoidArm active = hand == InteractionHand.MAIN_HAND ? primary : primary.getOpposite();
                        var source = ((PlayerRenderer)minecraft.getEntityRenderDispatcher().getRenderer(player)).getModel();
                        String armPose = (active == HumanoidArm.LEFT ? source.leftArmPose : source.rightArmPose).name();
                        String expected = switch (action) {
                            case "bow" -> "BOW_AND_ARROW";
                            case "crossbow_charge" -> "CROSSBOW_CHARGE";
                            default -> "CROSSBOW_HOLD";
                        };
                        require(armPose.equals(expected), "Two-handed action lost native arm dispatch: " + action + "/" + primary + "/" + hand + " -> " + armPose);
                        Vector3f left = position(pose, profile, "left_hand"), right = position(pose, profile, "right_hand");
                        twoHanded.add(Map.of("skin", skin, "action", action, "primaryArm", primary.name(), "itemHand", hand.name(),
                                "useTicks", useTicks, "headYaw", yaw, "leftHand", vector(left), "rightHand", vector(right),
                                "handSeparation", left.distance(right), "maximumNativeGeometryError", geometryError));
                    }
                }
            }
            player.modelName = originalSkin;
            checks.put("twoHandedNativeGeometry", Map.of("samples", twoHanded, "sampleCount", twoHanded.size(),
                    "criterion", "Both wrists and elbows match the original native geometry; item-specific grip contact is not inferred"));

            List<Vector3f> swings = new ArrayList<>();
            List<Object> attackSamples = new ArrayList<>();
            for (float phase : new float[]{.05f, .35f, .7f, .95f}) {
                player.reset(); player.main = new ItemStack(Items.DIAMOND_SWORD); player.attack = phase;
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f arm = limbAxis(pose, profile, "right_upper_arm", "right_forearm");
                swings.add(arm); attackSamples.add(Map.of("phase", phase, "rightArm", vector(arm)));
            }
            require(spread(swings) > .5f, "Accepted vanilla attack phases did not change the final arm pose");
            checks.put("attack", Map.of("samples", attackSamples, "directionChange", spread(swings)));
            checks.put("itemSupportContacts",verifyContacts(player,profile,scene,driver,animation));
        } finally {
            access.assimploader$setModelProperties(real);
        }
        require(real.position().equals(before) && real.getXRot() == beforePitch && real.getYRot() == beforeYaw
                        && real.getPose() == beforePose && ItemStack.matches(real.getMainHandItem(), beforeItem),
                "Runtime probe changed the real player's state");
        return Map.of("passed", true, "checks", checks, "realPlayerUnchanged", true,
                "input", "Unregistered LocalPlayer subclass; original PlayerRenderer and PlayerModel; independent FBX AnimationState",
                "notCovered", List.of("Live key input and accepted swing detection", "Server authority", "Render pixels", "Armor/cape layers", "Physics and terrain solver",
                        "Scripted pose overlays", "Arbitrary resource-pack grip geometry and fishing-line endpoint", "Custom reference-pose calibration and native skin mesh deformation"));
    }

    private static Map<String,Object> verifyContacts(ProbePlayer player,CharacterProfile profile,AssimpScene scene,
            VanillaPlayerPoseDriver driver,AnimationState animation) {
        CharacterEquipmentDefinition equipment;
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(new net.minecraft.resources.ResourceLocation(profile.equipmentDefinition()))) {
            equipment=CharacterEquipmentDefinition.read(reader);
        }catch(java.io.IOException error){throw new IllegalStateException("Cannot read contact fixture",error);}
        var contacts=new CharacterHandContacts(profile,equipment,scene);
        var samples=new ArrayList<Map<String,Object>>();float maximumError=0,maximumPrimaryError=0,maximumReachableError=0,maximumConstraintError=0;
        int clampedSamples=0;
        animation.addModifier("contact-runtime-probe",contacts);
        try {
            for(String skin:List.of("default","slim")) for(var primary:HumanoidArm.values()) for(var hand:InteractionHand.values())
                for(String action:List.of("bow","crossbow_charge","crossbow_hold")) for(float pitch:new float[]{-60,0,60})
                    for(float yaw:new float[]{-35,0,35}) for(int useTicks:new int[]{0,5,15,25}) {
                    player.modelName=skin;player.reset();player.primary=primary;player.useHand=hand;player.setXRot(pitch);
                    player.yHeadRot=player.yHeadRotO=yaw;player.useTicks=useTicks;
                    var weapon=new ItemStack(action.equals("bow")?Items.BOW:Items.CROSSBOW);
                    if(action.equals("crossbow_hold"))CrossbowItem.setCharged(weapon,true);
                    if(hand==InteractionHand.MAIN_HAND)player.main=weapon;else player.off=weapon;
                    player.using=!action.equals("crossbow_hold");
                    contacts.disable();var original=sample(player,driver,animation);
                    var physical=hand==InteractionHand.MAIN_HAND?primary:primary.getOpposite();
                    String mainNode=profile.bone(physical==HumanoidArm.LEFT?"left_hand":"right_hand").node();
                    var mainBefore=original.getNodeTransform(mainNode);
                    contacts.prepare(player,true);animation.invalidateCache();
                    var result=animation.getPoseSnapshot();var stats=contacts.statistics();
                    float primaryError=mainBefore.getTranslation(new Vector3f()).distance(result.getNodeTransform(mainNode).getTranslation(new Vector3f()));
                    require(!stats.rule().isEmpty(),"Automatic contact selector missed "+action+"/"+primary+"/"+hand);
                    require(primaryError<.00002f&&mainBefore.equals(result.getNodeTransform(mainNode),.00002f),"Support IK moved the primary item frame");
                    String side=physical==HumanoidArm.LEFT?"right":"left";
                    for(String[] segment:new String[][]{{side+"_upper_arm",side+"_forearm"},{side+"_forearm",side+"_hand"}}) {
                        float before=position(original,profile,segment[0]).distance(position(original,profile,segment[1]));
                        float after=position(result,profile,segment[0]).distance(position(result,profile,segment[1]));
                        require(Math.abs(before-after)<.00002f,"Support IK changed an FBX segment length");
                    }
                    maximumError=Math.max(maximumError,stats.error());maximumPrimaryError=Math.max(maximumPrimaryError,primaryError);
                    var displayContext=physical==HumanoidArm.LEFT?net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND:net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                    var itemModel=Minecraft.getInstance().getItemRenderer().getModel(weapon,player.level(),player,player.getId()+displayContext.ordinal());
                    var itemStack=new PoseStack();net.minecraftforge.client.ForgeHooksClient.handleCameraTransforms(itemStack,itemModel,displayContext,physical==HumanoidArm.LEFT);
                    var mount=physical==HumanoidArm.LEFT?equipment.leftHand():equipment.rightHand();
                    var item=mount.transform(original).mul(itemStack.last().pose());
                    var definition=equipment.grips().stream().filter(rule->rule.id().equals(stats.rule())).findFirst().orElseThrow();
                    require(definition.weight()==1&&definition.handOffsetVector().lengthSquared()==0,
                            "Runtime contact oracle expects full-weight wrist targets");
                    var shoulder=position(original,profile,side+"_upper_arm");
                    float upper=shoulder.distance(position(original,profile,side+"_forearm"));
                    float lower=position(original,profile,side+"_forearm").distance(position(original,profile,side+"_hand"));
                    var target=item.transformPosition(definition.socketVector());
                    var requested=new Vector3f(target).sub(shoulder);float requestedDistance=requested.length();
                    double near=Math.sqrt(upper*upper+lower*lower+2*upper*lower*Math.cos(Math.toRadians(definition.maximumBend())));
                    double far=Math.sqrt(upper*upper+lower*lower+2*upper*lower*Math.cos(Math.toRadians(definition.minimumBend())));
                    float legalDistance=(float)Math.max(near,Math.min(far,requestedDistance));
                    var nearestLegal=new Vector3f(shoulder).fma(legalDistance,requested.normalize());
                    float constraintError=nearestLegal.distance(position(result,profile,side+"_hand"));
                    require(Float.isFinite(stats.error())&&constraintError<.00002f,
                            "Contact IK did not reach the nearest bend-limited target: "+constraintError);
                    require(Math.abs(stats.error()-Math.abs(legalDistance-requestedDistance))<.00002f,
                            "Contact diagnostic error disagrees with independent reach calculation");
                    boolean expectedClamped=Math.abs(legalDistance-requestedDistance)>1e-6f;
                    require(stats.clamped()==expectedClamped,"Contact clamp diagnostic disagrees with reach limits");
                    if(stats.clamped())clampedSamples++;else maximumReachableError=Math.max(maximumReachableError,stats.error());
                    maximumConstraintError=Math.max(maximumConstraintError,constraintError);
                    var sample=new LinkedHashMap<String,Object>();
                    sample.putAll(Map.of("skin",skin,"action",action,"mainArm",primary.name(),"itemHand",hand.name(),"pitch",pitch,
                            "rule",stats.rule(),"support",stats.support(),"error",stats.error(),"clamped",stats.clamped()));
                    sample.put("nativeSupportInItem",vector(new Matrix4f(item).invert().transformPosition(position(original,profile,side+"_hand"))));
                    sample.put("shoulderInItem",vector(new Matrix4f(item).invert().transformPosition(position(original,profile,side+"_upper_arm"))));
                    sample.put("yaw",yaw);sample.put("useTicks",useTicks);
                    sample.put("requestedDistance",requestedDistance);sample.put("legalDistance",legalDistance);
                    sample.put("constraintError",constraintError);
                    samples.add(sample);
                }
        }finally{animation.removeModifier("contact-runtime-probe");}
        var report=Map.<String,Object>of("samples",samples,"sampleCount",samples.size(),"maximumContactError",maximumError,
                "maximumReachableContactError",maximumReachableError,"maximumConstraintError",maximumConstraintError,
                "clampedSamples",clampedSamples,"maximumPrimaryDisplacement",maximumPrimaryError,
                "criterion","Reachable targets meet the wrist; unreachable targets meet the independently computed nearest bend-limited point",
                "displayTransform","Actual ItemRenderer model overrides and Forge third-person camera transform");
        try{java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("hand-contact-probe.json"),new com.google.gson.Gson().toJson(report));}
        catch(java.io.IOException error){throw new IllegalStateException("Cannot write hand contact report",error);}
        require(maximumReachableError<.00002f,"Reachable item support contact exceeds tolerance: "+maximumReachableError);
        return report;
    }

    /** Use the original ModelPart matrix implementation as the geometric oracle, independent of the retargeter's Euler conversion. */
    private static float verifyNativeGeometry(ProbePlayer player, CharacterProfile profile, PoseGraph bind, PoseSnapshot pose) {
        var selected = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(selected instanceof PlayerRenderer renderer) || !(renderer instanceof PlayerRendererPoseAccess access))
            throw new IllegalStateException("Native geometry oracle requires the player's matching renderer");
        var nativeModel = renderer.getModel();
        nativeModel.attackTime = player.getAttackAnim(1);
        nativeModel.riding = false;
        nativeModel.young = false;
        float phase = player.walkAnimation.position(1), speed = Math.min(1, player.walkAnimation.speed(1));
        nativeModel.prepareMobModel(player, phase, speed, 1);
        nativeModel.setupAnim(player, phase, speed, player.tickCount + 1, player.yHeadRot - player.yBodyRot, player.getXRot());
        var stack = new PoseStack();
        access.assimploader$setupRotations(player, stack, player.tickCount + 1, player.yBodyRot, 1);
        var root = new Matrix4f().rotateY((float)Math.toRadians(player.yBodyRot - profile.modelYawDegrees()))
                .mul(stack.last().pose()).rotateZ((float)Math.PI).translate(0, -.001f, 0)
                .rotateX((float)Math.PI).rotateY((float)Math.toRadians(profile.modelYawDegrees()));
        var basis = new Matrix4f().rotateY((float)Math.toRadians(-profile.modelYawDegrees())).rotateX((float)Math.PI);
        float vertical = bind.position(profile.bone("head").node()).distance(bind.position(profile.bone("hips").node())) / 12;
        float horizontal = bind.position(profile.bone("left_upper_arm").node()).distance(bind.position(profile.bone("right_upper_arm").node())) / 10;
        float maximum = 0;
        for (String side : List.of("left", "right")) {
            boolean left = side.equals("left");
            for (String limb : List.of("arm", "leg")) {
                String upper = side + (limb.equals("arm") ? "_upper_arm" : "_thigh");
                String middle = side + (limb.equals("arm") ? "_forearm" : "_shin");
                String end = side + (limb.equals("arm") ? "_hand" : "_foot");
                ModelPart part = limb.equals("arm") ? (left ? nativeModel.leftArm : nativeModel.rightArm)
                        : (left ? nativeModel.leftLeg : nativeModel.rightLeg);
                Vector3f origin = bind.position(profile.bone(upper).node());
                var original = part.getInitialPose();
                Vector3f pivot = basis.transformDirection(new Vector3f((part.x - original.x) * horizontal,
                        (part.y - original.y) * vertical, (part.z - original.z) * vertical));
                var nativeStack = new PoseStack(); part.translateAndRotate(nativeStack);
                for (String endpoint : List.of(upper, middle, end)) {
                    Vector3f offset = bind.position(profile.bone(endpoint).node()).sub(origin);
                    basis.invert(new Matrix4f()).transformDirection(offset);
                    nativeStack.last().pose().transformDirection(offset);
                    basis.transformDirection(offset);
                    Vector3f expected = root.transformPosition(new Vector3f(origin).add(pivot).add(offset));
                    float error = position(pose, profile, endpoint).distance(expected);
                    require(Float.isFinite(error) && error < .0002f, "Native endpoint geometry mismatch: " + endpoint + " error=" + error);
                    maximum = Math.max(maximum, error);
                }
                for (String[] segment : new String[][]{{upper, middle}, {middle, end}}) {
                    float expectedLength = bind.position(profile.bone(segment[0]).node()).distance(bind.position(profile.bone(segment[1]).node()));
                    float actualLength = position(pose, profile, segment[0]).distance(position(pose, profile, segment[1]));
                    require(Math.abs(expectedLength - actualLength) < .0002f, "Native pose changed limb length: " + segment[0]);
                }
            }
        }
        return maximum;
    }

    private static PoseSnapshot sample(ProbePlayer player, VanillaPlayerPoseDriver driver, AnimationState animation) {
        driver.update(player, true); animation.update(.05f); return animation.getPoseSnapshot();
    }
    private static Vector3f position(PoseSnapshot pose, CharacterProfile profile, String role) {
        return pose.getNodeTransform(profile.bone(role).node()).getTranslation(new Vector3f());
    }
    private static Vector3f limbAxis(PoseSnapshot pose, CharacterProfile profile, String upper, String lower) {
        return position(pose, profile, lower).sub(position(pose, profile, upper)).normalize();
    }
    private static Vector3f bodyAxis(PoseSnapshot pose, CharacterProfile profile) { return limbAxis(pose, profile, "hips", "head"); }
    private static List<Float> vector(Vector3f value) { return List.of(value.x, value.y, value.z); }
    private static float spread(List<Vector3f> values) {
        float max = 0;
        for (Vector3f a : values) for (Vector3f b : values) max = Math.max(max, a.distance(b));
        return max;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class ProbePlayer extends LocalPlayer {
        boolean crouching, swimming, inWater, fallFlying, using;
        float attack;
        HumanoidArm primary;
        InteractionHand useHand;
        int useTicks;
        ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY;
        Entity vehicle;
        String modelName;
        ProbePlayer(Minecraft minecraft, LocalPlayer real) {
            super(minecraft, minecraft.level, real.connection, new StatsCounter(), new ClientRecipeBook(), false, false);
            modelName = real.getModelName();
            reset();
        }
        void reset() {
            crouching = swimming = inWater = fallFlying = using = false; attack = 0;
            primary = HumanoidArm.RIGHT; useHand = InteractionHand.MAIN_HAND; useTicks = 15;
            main = off = ItemStack.EMPTY; vehicle = null; setPose(Pose.STANDING);
            setXRot(0); setYRot(0); yBodyRot = yBodyRotO = yHeadRot = yHeadRotO = 0;
            tickCount = 100; swingingArm = InteractionHand.MAIN_HAND;
            setDeltaMovement(Vec3.ZERO); setWalk(0, 0);
        }
        void setWalk(float phase, float speed) {
            walkAnimation.update(phase - walkAnimation.position(1), 1); walkAnimation.setSpeed(speed);
        }
        @Override public String getModelName() { return modelName == null ? "default" : modelName; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCrouching() { return crouching; }
        @Override public boolean isSwimming() { return swimming; }
        @Override public boolean isVisuallySwimming() { return swimming; }
        @Override public boolean isInWater() { return inWater; }
        @Override public float getSwimAmount(float partialTick) { return swimming ? 1 : 0; }
        @Override public boolean isFallFlying() { return fallFlying; }
        @Override public int getFallFlyingTicks() { return fallFlying ? 20 : 0; }
        @Override public boolean isPassenger() { return vehicle != null; }
        @Override public Entity getVehicle() { return vehicle; }
        @Override public Direction getBedOrientation() { return Direction.EAST; }
        @Override public HumanoidArm getMainArm() { return primary == null ? HumanoidArm.RIGHT : primary; }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return hand == InteractionHand.MAIN_HAND ? main : off; }
        @Override public ItemStack getMainHandItem() { return main; }
        @Override public ItemStack getOffhandItem() { return off; }
        @Override public boolean isUsingItem() { return using; }
        @Override public InteractionHand getUsedItemHand() { return useHand; }
        @Override public ItemStack getUseItem() { return using ? getItemInHand(useHand) : ItemStack.EMPTY; }
        @Override public int getTicksUsingItem() { return using ? useTicks : 0; }
        @Override public int getUseItemRemainingTicks() { return using ? getUseItem().getUseDuration() - useTicks : 0; }
        @Override public float getAttackAnim(float partialTick) { return attack; }
    }
}
