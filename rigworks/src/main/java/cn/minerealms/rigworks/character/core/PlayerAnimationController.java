package cn.minerealms.rigworks.character.core;

import cn.minerealms.assimploader.animation.AnimationPlayer;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.animation.AnimationPoseLayer;
import cn.minerealms.assimploader.animation.ik.LookAtModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Translates client facts into the existing animation runtime. This controller never advances simulation time. */
public final class PlayerAnimationController implements AutoCloseable {
    public enum Intent {
        IDLE, WALK, RUN, WALK_BACKWARD, STRAFE_LEFT, STRAFE_RIGHT, CROUCH, CROUCH_WALK,
        JUMP, FALL, SWIM, FLY, ELYTRA, RIDE, SLEEP, DEAD, USE_ITEM, SWING,
        CRAWL, CRAWL_IDLE, LADDER_UP, LADDER_DOWN, LADDER_IDLE, WATER_IDLE, LAVA, RIPTIDE, FROZEN, HURT, LAND;
        private final String key = name().toLowerCase(Locale.ROOT);
        public String key() { return key; }
    }

    public record Selection(boolean accepted, Intent requested, Intent selected, String animation,
                            boolean stateBinding, boolean changed, boolean headLook) {}
    private record Resolved(Intent intent, String name, boolean stateBinding) {}
    private static final String HEAD_MODIFIER = "character:head-look";
    private static final String TRAVEL_MODIFIER = "character:travel-aim";
    private static final String ACTION_LAYER = "character:upper-action";
    private static final Map<Intent, Intent[]> FALLBACKS = Map.ofEntries(
            Map.entry(Intent.IDLE, new Intent[]{Intent.IDLE}),
            Map.entry(Intent.RUN, new Intent[]{Intent.RUN, Intent.WALK, Intent.IDLE}),
            Map.entry(Intent.WALK_BACKWARD, new Intent[]{Intent.WALK_BACKWARD, Intent.WALK, Intent.IDLE}),
            Map.entry(Intent.STRAFE_LEFT, new Intent[]{Intent.STRAFE_LEFT, Intent.WALK, Intent.IDLE}),
            Map.entry(Intent.STRAFE_RIGHT, new Intent[]{Intent.STRAFE_RIGHT, Intent.WALK, Intent.IDLE}),
            Map.entry(Intent.CROUCH_WALK, new Intent[]{Intent.CROUCH_WALK, Intent.CROUCH, Intent.WALK, Intent.IDLE}),
            Map.entry(Intent.JUMP, new Intent[]{Intent.JUMP, Intent.FALL, Intent.IDLE}),
            Map.entry(Intent.FALL, new Intent[]{Intent.FALL, Intent.JUMP, Intent.IDLE}),
            Map.entry(Intent.SWIM, new Intent[]{Intent.SWIM, Intent.FLY, Intent.WALK, Intent.IDLE}),
            Map.entry(Intent.ELYTRA, new Intent[]{Intent.ELYTRA, Intent.FLY, Intent.IDLE}));

    private final CharacterProfile profile;
    private final AnimationState animation;
    private final Set<String> clips;
    private final String headNode;
    private final Vector3f bindForward;
    private final HeadAim headAim;
    private final TravelAim travelAim;
    private long lastSequence = -1;
    private double lastTime = -1;
    private boolean previousJump, previousSwing, closed;
    private AnimationPlayer activeSwing;
    private AnimationPlayer synchronizedSwing, cadencePlayer;
    private float cadenceOriginalSpeed;
    private long swingGeneration = -1;
    private Selection selection;
    private AnimationPoseLayer upperAction;
    private String upperClip;
    private long upperSwing = -1;
    private Map<String, Float> upperMask;
    private VanillaPoseSelection compatibility = VanillaPoseSelection.ALL;

    public PlayerAnimationController(CharacterProfile profile, ModelInstance instance) {
        this(profile, Objects.requireNonNull(instance, "Character instance").animation());
    }

    public PlayerAnimationController(CharacterProfile profile, AnimationState animation) {
        this.profile = Objects.requireNonNull(profile, "Character profile");
        this.animation = Objects.requireNonNull(animation, "Character animation");
        animation.initialize();
        clips = Set.copyOf(Arrays.asList(animation.getAvailableAnimations()));
        var head = profile.bone("head");
        var scene = animation.getScene();
        if (profile.headLook().enabled() && head != null && scene != null && scene.getRootNode() != null
                && scene.getRootNode().find(head.node()) != null) {
            if (animation.getModifier(HEAD_MODIFIER) != null) throw new IllegalArgumentException("Character head controller already installed");
            headNode = head.node();
            Vector3f localForward = head.referenceRotation().quaternion().transform(profile.headLook().forward().vector());
            Matrix4f bind = new PoseGraph(scene.getRootNode(), Map.of()).global(headNode);
            bindForward = bind.transformDirection(new Vector3f(localForward)).normalize();
            if (!bindForward.isFinite() || Math.abs(bindForward.y) > .999f)
                throw new IllegalArgumentException("Head reference forward must have a horizontal component");
            Vector3f target = bind.getTranslation(new Vector3f()).add(new Vector3f(bindForward).mul(8));
            headAim = new HeadAim(headNode, new LookAtModifier(headNode, localForward, target, 1, (float)Math.PI));
            animation.addModifier(HEAD_MODIFIER, headAim);
        } else {
            headNode = null; bindForward = null; headAim = null;
        }
        var hips = profile.bone("hips");
        if (hips != null && head != null && scene != null && scene.getRootNode() != null
                && scene.findNodeByName(hips.node()) != null && scene.findNodeByName(head.node()) != null) {
            if (animation.getModifier(TRAVEL_MODIFIER) != null) throw new IllegalArgumentException("Character travel controller already installed");
            Vector3f localForward = head.referenceRotation().quaternion().transform(profile.headLook().forward().vector());
            Vector3f forward = new PoseGraph(scene.getRootNode(), Map.of()).global(head.node()).transformDirection(localForward);
            forward.y = 0;
            if (!forward.isFinite() || forward.lengthSquared() < 1e-10f)
                throw new IllegalArgumentException("Character reference forward must have a horizontal component");
            travelAim = new TravelAim(hips.node(), head.node(), forward.normalize());
            animation.addModifier(TRAVEL_MODIFIER, travelAim);
        } else travelAim = null;
    }

    public CharacterProfile profile() { return profile; }
    public Selection lastSelection() { return selection; }
    public boolean hasHeadLook() { return headAim != null; }

    /** Production policy: authored locomotion continues under an independently masked action layer. */
    public VanillaPoseSelection updateCompatible(PlayerVisualInput input) {
        var base = new PlayerVisualInput(input.sequence(), input.timeSeconds(), input.forwardVelocity(), input.strafeVelocity(),
                input.verticalVelocity(), input.bodyYawDegrees(), input.headYawDegrees(), input.headPitchDegrees(),
                input.onGround(), input.crouching(), input.sprinting(), input.jumping(), input.falling(), input.swimming(),
                input.flying(), input.elytra(), input.riding(), input.sleeping(), input.dead(), false, false,
                input.swingAction() == null ? null : new PlayerVisualInput.SwingAction(input.swingAction().generation(), false, 0), input.actionState());
        var selected = update(base);
        if (!selected.accepted()) return compatibility;
        boolean wholeBody = requiresNativeFallback(selected.requested(), selected.selected());
        boolean suppressAction = input.dead() || input.sleeping() || input.actionState().riptide();
        boolean swing = !suppressAction && input.swingAction() != null && input.swingAction().active();
        Intent action = suppressAction ? null : input.usingItem() ? Intent.USE_ITEM : swing ? Intent.SWING
                : input.actionState().hurtTicks() > 0 ? Intent.HURT : null;
        Resolved resolved = action == null ? null : resolveOne(action);
        if (resolved != null && !wholeBody && !upperBodyMask().isEmpty()) {
            String clip = resolved.stateBinding ? animation.getStateMachine().getBindings().get(resolved.name).clip() : resolved.name;
            boolean retrigger = action == Intent.SWING && input.swingAction().generation() != upperSwing;
            if (upperAction == null || !clip.equals(upperClip)) {
                upperAction = animation.setLayer(ACTION_LAYER, clip, 1, false, upperBodyMask());
                upperClip = clip;
            }
            var player = upperAction.getPlayer();
            player.setLooping(action == Intent.USE_ITEM);
            if (retrigger) { player.stop(); player.play(); }
            if (action == Intent.SWING) { player.synchronizePhase(input.swingAction().progress()); upperSwing = input.swingAction().generation(); }
            else player.releaseSynchronizedPhase();
            upperAction.setWeight(1);
        } else clearUpperAction();
        boolean nativeAction = action != null && upperAction == null;
        boolean nativeLeft = nativeAction || input.actionState().leftHeld();
        boolean nativeRight = nativeAction || input.actionState().rightHeld();
        // A custom action owns both arms. Root/legs remain entirely owned by the locomotion clip.
        if (upperAction != null) nativeLeft = nativeRight = false;
        compatibility = wholeBody ? VanillaPoseSelection.ALL : new VanillaPoseSelection(true,
                nativeAction && swing, true, input.actionState().hurtTicks() > 0 && upperAction == null ? 1 : 0,
                0, 1, nativeLeft ? 1 : 0, nativeRight ? 1 : 0, 0, 0);
        if (wholeBody && travelAim != null) travelAim.enabled = false;
        return compatibility;
    }

    public VanillaPoseSelection compatibilitySelection() { return compatibility; }

    /** Missing locomotion can degrade within its family; missing special poses must use native sampling. */
    public static boolean requiresNativeFallback(Intent requested, Intent selected) {
        if (selected == requested) return false;
        return switch (requested) {
            case RUN, WALK_BACKWARD, STRAFE_LEFT, STRAFE_RIGHT -> selected != Intent.WALK;
            case IDLE, WALK -> selected == null;
            default -> true;
        };
    }

    private Map<String, Float> upperBodyMask() {
        if (upperMask != null) return upperMask;
        var mask = new java.util.LinkedHashMap<String, Float>();
        for (String role : new String[]{"spine", "chest", "upper_chest", "left_upper_arm", "right_upper_arm"}) {
            var bone = profile.bone(role);
            if (bone == null) continue;
            var node = animation.getScene().findNodeByName(bone.node());
            if (node != null) addMask(node, mask);
        }
        for (String role : new String[]{"left_thigh", "right_thigh"}) {
            var bone = profile.bone(role);
            if (bone == null) continue;
            var node = animation.getScene().findNodeByName(bone.node());
            if (node != null) removeMask(node, mask);
        }
        var hips = profile.bone("hips");
        if (hips != null) mask.remove(hips.node());
        return upperMask = Map.copyOf(mask);
    }

    private static void addMask(cn.minerealms.assimploader.model.AssimpNode node, Map<String, Float> mask) {
        mask.put(node.getName(), 1f);
        for (var child : node.getChildren()) addMask(child, mask);
    }

    private static void removeMask(cn.minerealms.assimploader.model.AssimpNode node, Map<String, Float> mask) {
        mask.remove(node.getName());
        for (var child : node.getChildren()) removeMask(child, mask);
    }

    private void clearUpperAction() {
        if (upperAction != null && !animation.isClosed()) animation.removeLayer(ACTION_LAYER);
        upperAction = null; upperClip = null; upperSwing = -1;
    }

    /** Call once per new client tick before the animation manager evaluates its next pose. */
    public Selection update(PlayerVisualInput input) {
        requireOpen(); Objects.requireNonNull(input, "Player visual input");
        boolean observedSwing = input.swingAction() != null;
        boolean nativeSwing = profile.timing().swingSync() && observedSwing;
        if (input.sequence() <= lastSequence || input.timeSeconds() < lastTime
                || observedSwing && input.swingAction().generation() < swingGeneration) {
            return selection == null ? new Selection(false, null, null, null, false, false, false)
                    : new Selection(false, selection.requested, selection.selected, selection.animation, selection.stateBinding, false, selection.headLook);
        }
        boolean jumpEdge = input.jumping() && !previousJump;
        boolean swingEdge = observedSwing
                ? input.swingAction().active() && input.swingAction().generation() != swingGeneration
                : input.swinging() && !previousSwing;
        if (observedSwing) swingGeneration = input.swingAction().generation();
        previousJump = input.jumping(); previousSwing = input.swinging();
        lastSequence = input.sequence(); lastTime = input.timeSeconds();
        Intent desired = select(input, jumpEdge, swingEdge);
        Resolved resolved = resolve(desired);
        boolean syncAction = nativeSwing && desired == Intent.SWING && resolved != null && resolved.intent == Intent.SWING;
        boolean leavingSync = synchronizedSwing != null && !syncAction;
        if (leavingSync) releaseSwingClock();
        boolean headEnabled = updateHead(input, desired);
        if (travelAim != null) travelAim.update(input, desired);
        boolean changed = false;
        if (resolved != null) {
            String expectedClip = resolved.stateBinding
                    ? animation.getStateMachine().getBindings().get(resolved.name).clip() : resolved.name;
            AnimationPlayer current = animation.getCurrentAnimation();
            boolean restart = (desired == Intent.SWING && swingEdge && resolved.intent == Intent.SWING)
                    || (desired == Intent.JUMP && jumpEdge && resolved.intent == Intent.JUMP);
            changed = current == null || !current.getAnimation().getName().equals(expectedClip)
                    || resolved.stateBinding && !resolved.name.equals(animation.getStateMachine().getCurrentStateName());
            if (syncAction && (changed || restart || synchronizedSwing != current)) {
                if (resolved.stateBinding) animation.getStateMachine().transitionTo(resolved.name, 0);
                // Immediate publication also clears an interrupted blend when this is already the current clip.
                animation.playAnimation(expectedClip);
                changed = true;
            } else if (resolved.stateBinding) {
                if (leavingSync) animation.getStateMachine().transitionTo(resolved.name, 0);
                else animation.getStateMachine().request(resolved.name);
            } else animation.transitionTo(resolved.name, leavingSync ? 0 : profile.blendSeconds());
            current = animation.getCurrentAnimation();
            if (current != null) {
                if (!syncAction && restart && !changed) { current.stop(); current.play(); changed = true; }
                if (!resolved.stateBinding || resolved.intent == Intent.SWING || resolved.intent == Intent.JUMP)
                    current.setLooping(resolved.intent != Intent.SWING && resolved.intent != Intent.JUMP && resolved.intent != Intent.DEAD);
                if (resolved.intent == Intent.SWING && desired == Intent.SWING) activeSwing = current;
                else activeSwing = null;
                updateCadence(current, resolved, desired, input);
                if (syncAction) {
                    synchronizedSwing = current;
                    current.synchronizePhase(input.swingAction().progress());
                }
            }
        } else { activeSwing = null; releaseCadence(); }
        selection = new Selection(true, desired, resolved == null ? null : resolved.intent,
                resolved == null ? null : resolved.name, resolved != null && resolved.stateBinding, changed, headEnabled);
        return selection;
    }

    private Intent select(PlayerVisualInput input, boolean jumpEdge, boolean swingEdge) {
        if (input.dead()) return Intent.DEAD;
        if (input.sleeping()) return Intent.SLEEP;
        if (input.actionState().riptide()) return Intent.RIPTIDE;
        if (input.riding()) return Intent.RIDE;
        if (input.elytra()) return Intent.ELYTRA;
        if (input.swimming()) return Intent.SWIM;
        if (input.crawling()) return input.horizontalSpeed() > profile.movementThreshold() ? Intent.CRAWL : Intent.CRAWL_IDLE;
        if (input.actionState().climbing()) return input.verticalVelocity() > .05 ? Intent.LADDER_UP
                : input.verticalVelocity() < -.05 ? Intent.LADDER_DOWN : Intent.LADDER_IDLE;
        if (input.actionState().inLava()) return Intent.LAVA;
        if (input.actionState().inWater() && !input.onGround()) return Intent.WATER_IDLE;
        if (input.flying()) return Intent.FLY;
        if (input.actionState().frozen()) return Intent.FROZEN;
        if (input.usingItem()) return Intent.USE_ITEM;
        if (profile.timing().swingSync() && input.swingAction() != null) {
            if (input.swingAction().active()) return Intent.SWING;
        } else if (swingEdge || activeSwing != null && activeSwing == animation.getCurrentAnimation() && activeSwing.isPlaying()) return Intent.SWING;
        if (input.falling() || !input.onGround() && input.verticalVelocity() < -.01) return Intent.FALL;
        if (jumpEdge && (input.onGround() || input.verticalVelocity() > .01)
                || !input.onGround() && input.verticalVelocity() > .01) return Intent.JUMP;
        if (!input.onGround()) return Intent.FALL;
        if (input.actionState().airPhase() == PlayerActionState.AirPhase.LANDING && resolveOne(Intent.LAND) != null) return Intent.LAND;
        boolean moving = input.horizontalSpeed() > profile.movementThreshold();
        if (input.crouching()) return moving ? Intent.CROUCH_WALK : Intent.CROUCH;
        if (!moving) return Intent.IDLE;
        if (input.sprinting()) return Intent.RUN;
        if (Math.abs(input.strafeVelocity()) > Math.abs(input.forwardVelocity()))
            return input.strafeVelocity() > 0 ? Intent.STRAFE_RIGHT : Intent.STRAFE_LEFT;
        return input.forwardVelocity() < 0 ? Intent.WALK_BACKWARD : Intent.WALK;
    }

    private Resolved resolve(Intent desired) {
        Intent[] candidates = FALLBACKS.get(desired);
        if (candidates == null) {
            Resolved result = resolveOne(desired);
            return result == null ? resolveOne(Intent.IDLE) : result;
        }
        for (Intent candidate : candidates) {
            Resolved result = resolveOne(candidate);
            if (result != null) return result;
        }
        return null;
    }

    private Resolved resolveOne(Intent intent) {
        String name = profile.animation(intent.key());
        if (animation.getStateMachine().getBindings().containsKey(name)) return new Resolved(intent, name, true);
        return clips.contains(name) ? new Resolved(intent, name, false) : null;
    }

    private void updateCadence(AnimationPlayer player, Resolved resolved, Intent desired, PlayerVisualInput input) {
        boolean locomotion = switch (resolved.intent) {
            case WALK, RUN, WALK_BACKWARD, STRAFE_LEFT, STRAFE_RIGHT, CROUCH_WALK -> true;
            default -> false;
        };
        if (cadencePlayer != null && (cadencePlayer != player || !locomotion || !profile.timing().movementSync())) releaseCadence();
        if (!locomotion || !profile.timing().movementSync()) {
            player.setSpeed(resolved.stateBinding ? animation.getStateMachine().getBindings().get(resolved.name).speed() : 1);
            return;
        }
        if (cadencePlayer == null) { cadencePlayer = player; cadenceOriginalSpeed = player.getSpeed(); }
        double stride = switch (desired) {
            case RUN -> profile.timing().runStride();
            case CROUCH_WALK -> profile.timing().crouchStride();
            default -> profile.timing().walkStride();
        };
        double duration = player.getAnimation().getDuration() / player.getAnimation().getTicksPerSecond();
        if (Double.isFinite(duration) && duration > 0) {
            double rate = duration * input.horizontalSpeed() / stride;
            player.setSpeed((float)Math.max(profile.timing().minRate(), Math.min(profile.timing().maxRate(), rate)));
        }
    }

    private void releaseCadence() {
        if (cadencePlayer != null) cadencePlayer.setSpeed(cadenceOriginalSpeed);
        cadencePlayer = null;
    }

    private void releaseSwingClock() {
        if (synchronizedSwing != null) synchronizedSwing.releaseSynchronizedPhase();
        synchronizedSwing = null;
    }

    private boolean updateHead(PlayerVisualInput input, Intent intent) {
        if (headAim == null) return false;
        headAim.enabled = intent != Intent.DEAD && intent != Intent.SLEEP;
        if (!headAim.enabled) return false;
        float yaw = wrap((double)input.headYawDegrees() - input.bodyYawDegrees());
        yaw = Math.max(-profile.headLook().maxYaw(), Math.min(profile.headLook().maxYaw(), yaw));
        float pitch = Math.max(-profile.headLook().maxPitch(), Math.min(profile.headLook().maxPitch(), input.headPitchDegrees()));
        Vector3f direction = new Vector3f(bindForward).rotateY(-(float)Math.toRadians(yaw));
        Vector3f right = new Vector3f(0, 1, 0).cross(direction).normalize();
        direction.rotateAxis((float)Math.toRadians(pitch), right.x, right.y, right.z);
        headAim.direction.set(direction);
        return true;
    }

    private static float wrap(double degrees) {
        float result = (float)(degrees % 360);
        if (result >= 180) result -= 360;
        if (result < -180) result += 360;
        return result;
    }

    /** The owning client resets its input sequence when changing players/worlds; existing animation time is preserved. */
    public void reset() {
        requireOpen(); lastSequence = -1; lastTime = -1; previousJump = previousSwing = false;
        swingGeneration = -1; releaseSwingClock(); releaseCadence();
        activeSwing = null; selection = null; if (headAim != null) headAim.enabled = false;
        clearUpperAction(); compatibility = VanillaPoseSelection.ALL;
        if (travelAim != null) travelAim.enabled = false;
    }

    private void requireOpen() {
        if (closed || animation.isClosed()) throw new IllegalStateException("Character controller is closed");
    }
    @Override public void close() {
        if (closed) return;
        closed = true; activeSwing = null; releaseSwingClock(); releaseCadence();
        clearUpperAction();
        if (!animation.isClosed() && headAim != null && animation.getModifier(HEAD_MODIFIER) == headAim)
            animation.removeModifier(HEAD_MODIFIER);
        if (!animation.isClosed() && travelAim != null && animation.getModifier(TRAVEL_MODIFIER) == travelAim)
            animation.removeModifier(TRAVEL_MODIFIER);
    }

    private static final class HeadAim implements PoseModifier {
        private final String node;
        private final LookAtModifier look;
        private final Vector3f direction = new Vector3f();
        private boolean enabled;
        private HeadAim(String node, LookAtModifier look) { this.node = node; this.look = look; }
        @Override public Category category() { return Category.IK; }
        @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
            if (enabled) {
                var origin = new PoseGraph(scene.getRootNode(), locals).position(node);
                look.setTarget(origin.add(new Vector3f(direction).mul(8)));
                look.apply(scene, locals, deltaSeconds);
            }
        }
    }

    /** Correct the authored horizontal clip in model space so mesh, equipment and physics share the same dive. */
    private static final class TravelAim implements PoseModifier {
        private final String hips, head;
        private final Vector3f forward, direction = new Vector3f();
        private boolean enabled;
        private TravelAim(String hips, String head, Vector3f forward) { this.hips = hips; this.head = head; this.forward = forward; }
        private void update(PlayerVisualInput input, Intent intent) {
            enabled = intent == Intent.ELYTRA || intent == Intent.SWIM;
            if (!enabled) return;
            float yaw = wrap((double) input.headYawDegrees() - input.bodyYawDegrees());
            direction.set(forward).rotateY(-(float) Math.toRadians(yaw));
            Vector3f right = new Vector3f(0, 1, 0).cross(direction).normalize();
            float pitch = Math.max(-90, Math.min(90, input.headPitchDegrees()));
            direction.rotateAxis((float) Math.toRadians(pitch), right.x, right.y, right.z);
        }
        @Override public Category category() { return Category.GENERAL; }
        @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
            if (!enabled) return;
            var graph = new PoseGraph(scene.getRootNode(), locals);
            Vector3f axis = graph.position(head).sub(graph.position(hips));
            if (axis.lengthSquared() < 1e-10f) return;
            graph.rotateModel(hips, new Quaternionf().rotationTo(axis.normalize(), direction));
        }
    }
}
