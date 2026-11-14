package cn.minerealms.assimploader.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** Rideable heavy vehicle; model convention is +Z forward, +X right, +Y up. */
public final class ArmoredCarEntity extends Entity {
    public static final String MODEL_NAME = "armored_car";
    public static final float MODEL_SCALE = 2.0f;
    public static final float MODEL_LENGTH = 4.5f * MODEL_SCALE;
    public static final float MODEL_WIDTH = 2.0f * MODEL_SCALE;
    public static final float MODEL_HEIGHT = 2.5f * MODEL_SCALE;
    public static final double SEAT_HEIGHT = 1.32 * MODEL_SCALE;
    public static final double CAMERA_DISTANCE = 7.5 * MODEL_SCALE;

    private static final EntityDataAccessor<Float> WHEEL_ROLL =
            SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEERING_ANGLE =
            SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.FLOAT);
    private static final float GRAVITY = 0.08f;
    private static final float TERMINAL_FALL_SPEED = 0.55f;
    private static final int INPUT_TIMEOUT_TICKS = 8;

    private float throttleInput;
    private float steeringInput;
    private boolean handbrakeInput;
    private int lastInputTick;
    private int lastInputSequence;
    private boolean hasInputSequence;
    private java.util.UUID inputDriver;
    private float previousWheelRoll;
    private float currentWheelRoll;
    private float previousSteeringAngle;
    private float currentSteeringAngle;
    private boolean wheelPhaseInitialized;
    private int interpolationSteps;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;

    public ArmoredCarEntity(EntityType<? extends ArmoredCarEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.lastInputTick = this.tickCount;
        setMaxUpStep(0.6f);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(WHEEL_ROLL, 0.0f);
        this.entityData.define(STEERING_ANGLE, 0.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == WHEEL_ROLL) {
            if (!level().isClientSide || !wheelPhaseInitialized) {
                currentWheelRoll = entityData.get(WHEEL_ROLL);
                previousWheelRoll = currentWheelRoll;
                wheelPhaseInitialized = true;
            }
        } else if (accessor == STEERING_ANGLE) {
            if (!level().isClientSide) currentSteeringAngle = entityData.get(STEERING_ANGLE);
        }
    }

    @Override
    public void tick() {
        super.tick();
        previousWheelRoll = currentWheelRoll;
        previousSteeringAngle = currentSteeringAngle;
        if (level().isClientSide) {
            tickInterpolation();
            return;
        }

        if (getPassengers().isEmpty() || tickCount - lastInputTick > INPUT_TIMEOUT_TICKS) {
            throttleInput = 0.0f;
            steeringInput = 0.0f;
            handbrakeInput = true;
        }

        Vec3 forward = forwardVector(getYRot());
        Vec3 side = new Vec3(forward.z, 0.0, -forward.x);
        Vec3 velocity = getDeltaMovement();
        float forwardSpeed = (float) (velocity.x * forward.x + velocity.z * forward.z);
        float lateralSpeed = (float) (velocity.x * side.x + velocity.z * side.z);
        ArmoredCarPhysics.Motion motion = ArmoredCarPhysics.step(forwardSpeed, lateralSpeed,
                entityData.get(STEERING_ANGLE), throttleInput, steeringInput, handbrakeInput, onGround());

        float oldYaw = getYRot();
        setYRot(oldYaw + (float) Math.toDegrees(motion.yawDeltaRadians()));
        setBoundingBox(makeBoundingBox());
        if (!level().noCollision(this, getBoundingBox().deflate(1.0e-5))) {
            setYRot(oldYaw);
            setBoundingBox(makeBoundingBox());
        }
        Vec3 newForward = forwardVector(getYRot());
        Vec3 newSide = new Vec3(newForward.z, 0.0, -newForward.x);

        double verticalSpeed = velocity.y;
        if (!isNoGravity()) verticalSpeed = Math.max(verticalSpeed - GRAVITY, -TERMINAL_FALL_SPEED);
        Vec3 movement = newForward.scale(motion.forwardSpeed()).add(newSide.scale(motion.lateralSpeed()))
                .add(0.0, verticalSpeed, 0.0);

        double startX = getX();
        double startZ = getZ();
        setDeltaMovement(movement);
        move(MoverType.SELF, movement);
        if (onGround() && getDeltaMovement().y < 0.0) {
            setDeltaMovement(getDeltaMovement().x, 0.0, getDeltaMovement().z);
        }

        Vec3 moved = new Vec3(getX() - startX, 0.0, getZ() - startZ);
        double signedDistance = moved.dot(newForward);
        // A blocked car has no displacement and therefore no rolling wheel animation.
        float nextRoll = ArmoredCarPhysics.wheelRollForDistance(currentWheelRoll, signedDistance);
        entityData.set(WHEEL_ROLL, nextRoll);
        entityData.set(STEERING_ANGLE, motion.steeringAngle());
        setBoundingBox(makeBoundingBox());
        for (Entity passenger : getPassengers()) positionRider(passenger);
    }

    /** +1 steering turns toward local +X; the driver inputs are server-validated. */
    public void applyInput(ServerPlayer sender, int sequence, float throttle, float steering, boolean handbrake) {
        if (sender.level() != level() || sender.getVehicle() != this || getControllingPassenger() != sender) return;
        if (!sender.getUUID().equals(inputDriver)) {
            inputDriver = sender.getUUID();
            hasInputSequence = false;
        }
        if (hasInputSequence && sequence - lastInputSequence <= 0) return;
        hasInputSequence = true;
        lastInputSequence = sequence;
        throttleInput = Mth.clamp(Float.isFinite(throttle) ? throttle : 0.0f, -1.0f, 1.0f);
        steeringInput = Mth.clamp(Float.isFinite(steering) ? steering : 0.0f, -1.0f, 1.0f);
        handbrakeInput = handbrake;
        lastInputTick = tickCount;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isCrouching()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        inputDriver = null;
        hasInputSequence = false;
        throttleInput = steeringInput = 0.0f;
        handbrakeInput = true;
    }

    @Nullable
    @Override
    public Player getControllingPassenger() {
        return getPassengers().isEmpty() || !(getPassengers().get(0) instanceof Player player) ? null : player;
    }

    @Override
    public void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (!hasPassenger(passenger)) return;
        double yaw = Math.toRadians(-getYRot());
        double localX = -0.34 * MODEL_SCALE;
        double localZ = 0.18 * MODEL_SCALE;
        double worldX = localX * Math.cos(yaw) + localZ * Math.sin(yaw);
        double worldZ = -localX * Math.sin(yaw) + localZ * Math.cos(yaw);
        moveFunction.accept(passenger, getX() + worldX, getY() + SEAT_HEIGHT, getZ() + worldZ);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 forward = forwardVector(getYRot());
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        Vec3 center = position();
        double sideExit = MODEL_WIDTH * 0.5 + 0.7;
        double endExit = MODEL_LENGTH * 0.5 + 0.65;
        Vec3[] candidates = {center.add(right.scale(-sideExit)), center.add(right.scale(sideExit)),
                center.add(forward.scale(-endExit)), center.add(forward.scale(endExit))};
        for (Vec3 candidate : candidates) {
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos pos = BlockPos.containing(candidate.x, getY() + dy, candidate.z);
                Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
                if (safe != null && !passenger.getDimensions(Pose.STANDING).makeBoundingBox(safe).intersects(getBoundingBox()))
                    return safe;
            }
        }
        Vec3 roof = new Vec3(getX(), getBoundingBox().maxY + 0.15, getZ());
        if (level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(roof))) return roof;
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(MODEL_WIDTH, MODEL_HEIGHT);
    }

    @Override
    protected AABB makeBoundingBox() {
        double yaw = Math.toRadians(-getYRot());
        double sin = Math.abs(Math.sin(yaw));
        double cos = Math.abs(Math.cos(yaw));
        double halfX = (MODEL_WIDTH * cos + MODEL_LENGTH * sin) * 0.5;
        double halfZ = (MODEL_WIDTH * sin + MODEL_LENGTH * cos) * 0.5;
        return new AABB(getX() - halfX, getY(), getZ() - halfZ,
                getX() + halfX, getY() + MODEL_HEIGHT, getZ() + halfZ);
    }

    public float getWheelAngle(float partialTick) {
        return Mth.lerp(partialTick, previousWheelRoll, currentWheelRoll);
    }

    /** Interpolated radians; positive rotates the front wheels toward local +X (right). */
    public float getSteeringAngle(float partialTick) {
        return Mth.lerp(partialTick, previousSteeringAngle, currentSteeringAngle);
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }
    @Override public boolean isPushable() { return true; }
    @Override public boolean canCollideWith(Entity other) {
        return !isPassengerOfSameVehicle(other) && (other.canBeCollidedWith() || other.isPushable());
    }

    @Override
    public void push(double x, double y, double z) {
        super.push(x * 0.08, y * 0.08, z * 0.08);
    }

    /** Clients send controls and interpolate server motion; vanilla vehicle-position packets are not used. */
    @Override public boolean isControlledByLocalInstance() { return !level().isClientSide; }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        targetX = x; targetY = y; targetZ = z;
        targetYaw = yaw; targetPitch = pitch;
        interpolationSteps = teleport ? 1 : Math.max(2, steps);
    }

    private void tickInterpolation() {
        double startX = getX(), startZ = getZ();
        if (interpolationSteps > 0) {
            double factor = 1.0 / interpolationSteps;
            setPos(Mth.lerp(factor, getX(), targetX), Mth.lerp(factor, getY(), targetY), Mth.lerp(factor, getZ(), targetZ));
            setYRot(getYRot() + Mth.wrapDegrees(targetYaw - getYRot()) / interpolationSteps);
            setXRot(getXRot() + (targetPitch - getXRot()) / interpolationSteps);
            setBoundingBox(makeBoundingBox());
            interpolationSteps--;
        }
        Vec3 moved = new Vec3(getX() - startX, 0.0, getZ() - startZ);
        if (moved.lengthSqr() < 4.0)
            currentWheelRoll = ArmoredCarPhysics.wheelRollForDistance(currentWheelRoll, moved.dot(forwardVector(getYRot())));
        else currentWheelRoll = previousWheelRoll = entityData.get(WHEEL_ROLL);
        currentSteeringAngle = Mth.lerp(0.5f, currentSteeringAngle, entityData.get(STEERING_ANGLE));
        for (Entity passenger : getPassengers()) positionRider(passenger);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("WheelRoll", currentWheelRoll);
        tag.putFloat("SteeringAngle", currentSteeringAngle);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        currentWheelRoll = tag.getFloat("WheelRoll");
        currentSteeringAngle = tag.getFloat("SteeringAngle");
        if (!Float.isFinite(currentWheelRoll)) currentWheelRoll = 0.0f;
        if (!Float.isFinite(currentSteeringAngle)) currentSteeringAngle = 0.0f;
        currentSteeringAngle = Mth.clamp(currentSteeringAngle, -0.48f, 0.48f);
        previousWheelRoll = currentWheelRoll;
        previousSteeringAngle = currentSteeringAngle;
        entityData.set(WHEEL_ROLL, currentWheelRoll);
        entityData.set(STEERING_ANGLE, currentSteeringAngle);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private static Vec3 forwardVector(float minecraftYawDegrees) {
        double yaw = Math.toRadians(minecraftYawDegrees);
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }
}
