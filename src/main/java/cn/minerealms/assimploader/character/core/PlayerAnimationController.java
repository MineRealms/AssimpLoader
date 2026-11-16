package cn.minerealms.assimploader.character.core;

import cn.minerealms.assimploader.animation.AnimationPlayer;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.animation.ik.LookAtModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
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
        JUMP, FALL, SWIM, FLY, ELYTRA, RIDE, SLEEP, DEAD, USE_ITEM, SWING;
        private final String key = name().toLowerCase(Locale.ROOT);
        public String key() { return key; }
    }

    public record Selection(boolean accepted, Intent requested, Intent selected, String animation,
                            boolean stateBinding, boolean changed, boolean headLook) {}
    private record Resolved(Intent intent, String name, boolean stateBinding) {}
    private static final String HEAD_MODIFIER = "character:head-look";
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
    private long lastSequence = -1;
    private double lastTime = -1;
    private boolean previousJump, previousSwing, closed;
    private AnimationPlayer activeSwing;
    private Selection selection;

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
            headAim = new HeadAim(new LookAtModifier(headNode, localForward, target, 1, (float)Math.PI));
            animation.addModifier(HEAD_MODIFIER, headAim);
        } else {
            headNode = null; bindForward = null; headAim = null;
        }
    }

    public CharacterProfile profile() { return profile; }
    public Selection lastSelection() { return selection; }
    public boolean hasHeadLook() { return headAim != null; }

    /** Call once per new client tick before the animation manager evaluates its next pose. */
    public Selection update(PlayerVisualInput input) {
        requireOpen(); Objects.requireNonNull(input, "Player visual input");
        if (input.sequence() <= lastSequence || input.timeSeconds() < lastTime) {
            return selection == null ? new Selection(false, null, null, null, false, false, false)
                    : new Selection(false, selection.requested, selection.selected, selection.animation, selection.stateBinding, false, selection.headLook);
        }
        boolean jumpEdge = input.jumping() && !previousJump;
        boolean swingEdge = input.swinging() && !previousSwing;
        previousJump = input.jumping(); previousSwing = input.swinging();
        lastSequence = input.sequence(); lastTime = input.timeSeconds();
        Intent desired = select(input, jumpEdge, swingEdge);
        Resolved resolved = resolve(desired);
        boolean headEnabled = updateHead(input, desired);
        boolean changed = false;
        if (resolved != null) {
            String expectedClip = resolved.stateBinding
                    ? animation.getStateMachine().getBindings().get(resolved.name).clip() : resolved.name;
            AnimationPlayer current = animation.getCurrentAnimation();
            boolean restart = (desired == Intent.SWING && swingEdge && resolved.intent == Intent.SWING)
                    || (desired == Intent.JUMP && jumpEdge && resolved.intent == Intent.JUMP);
            changed = current == null || !current.getAnimation().getName().equals(expectedClip)
                    || resolved.stateBinding && !resolved.name.equals(animation.getStateMachine().getCurrentStateName());
            if (resolved.stateBinding) animation.getStateMachine().request(resolved.name);
            else animation.transitionTo(resolved.name, profile.blendSeconds());
            current = animation.getCurrentAnimation();
            if (current != null) {
                if (restart && !changed) { current.stop(); current.play(); changed = true; }
                if (!resolved.stateBinding || resolved.intent == Intent.SWING || resolved.intent == Intent.JUMP)
                    current.setLooping(resolved.intent != Intent.SWING && resolved.intent != Intent.JUMP && resolved.intent != Intent.DEAD);
                if (resolved.intent == Intent.SWING && desired == Intent.SWING) activeSwing = current;
                else activeSwing = null;
            }
        } else activeSwing = null;
        selection = new Selection(true, desired, resolved == null ? null : resolved.intent,
                resolved == null ? null : resolved.name, resolved != null && resolved.stateBinding, changed, headEnabled);
        return selection;
    }

    private Intent select(PlayerVisualInput input, boolean jumpEdge, boolean swingEdge) {
        if (input.dead()) return Intent.DEAD;
        if (input.sleeping()) return Intent.SLEEP;
        if (input.riding()) return Intent.RIDE;
        if (input.elytra()) return Intent.ELYTRA;
        if (input.swimming()) return Intent.SWIM;
        if (input.flying()) return Intent.FLY;
        if (input.usingItem()) return Intent.USE_ITEM;
        if (swingEdge || activeSwing != null && activeSwing == animation.getCurrentAnimation() && activeSwing.isPlaying()) return Intent.SWING;
        if (input.falling() || !input.onGround() && input.verticalVelocity() < -.01) return Intent.FALL;
        if (jumpEdge && (input.onGround() || input.verticalVelocity() > .01)
                || !input.onGround() && input.verticalVelocity() > .01) return Intent.JUMP;
        if (!input.onGround()) return Intent.FALL;
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

    private boolean updateHead(PlayerVisualInput input, Intent intent) {
        if (headAim == null) return false;
        headAim.enabled = intent != Intent.DEAD && intent != Intent.SLEEP && intent != Intent.ELYTRA;
        if (!headAim.enabled) return false;
        float yaw = wrap((double)input.headYawDegrees() - input.bodyYawDegrees());
        yaw = Math.max(-profile.headLook().maxYaw(), Math.min(profile.headLook().maxYaw(), yaw));
        float pitch = Math.max(-profile.headLook().maxPitch(), Math.min(profile.headLook().maxPitch(), input.headPitchDegrees()));
        Vector3f direction = new Vector3f(bindForward).rotateY(-(float)Math.toRadians(yaw));
        Vector3f right = new Vector3f(0, 1, 0).cross(direction).normalize();
        direction.rotateAxis((float)Math.toRadians(pitch), right.x, right.y, right.z);
        Matrix4f head = animation.getPoseSnapshot().getNodeTransform(headNode);
        if (head == null) { headAim.enabled = false; return false; }
        headAim.look.setTarget(head.getTranslation(new Vector3f()).add(direction.mul(8)));
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
        activeSwing = null; selection = null; if (headAim != null) headAim.enabled = false;
    }

    private void requireOpen() {
        if (closed || animation.isClosed()) throw new IllegalStateException("Character controller is closed");
    }
    @Override public void close() {
        if (closed) return;
        closed = true; activeSwing = null;
        if (!animation.isClosed() && headAim != null && animation.getModifier(HEAD_MODIFIER) == headAim)
            animation.removeModifier(HEAD_MODIFIER);
    }

    private static final class HeadAim implements PoseModifier {
        private final LookAtModifier look;
        private boolean enabled;
        private HeadAim(LookAtModifier look) { this.look = look; }
        @Override public Category category() { return Category.IK; }
        @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
            if (enabled) look.apply(scene, locals, deltaSeconds);
        }
    }
}
