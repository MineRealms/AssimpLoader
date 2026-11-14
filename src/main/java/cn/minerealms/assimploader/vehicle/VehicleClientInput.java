package cn.minerealms.assimploader.vehicle;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public final class VehicleClientInput {
    private static final KeyMapping HANDBRAKE = key("handbrake", GLFW.GLFW_KEY_SPACE);

    private static int sequence;
    private static int lastVehicleId = -1;
    private static float lastThrottle;
    private static float lastSteering;
    private static boolean lastHandbrake;
    private static int ticksSinceSend;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(HANDBRAKE);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetLocalState();
            return;
        }
        if (!(minecraft.player.getVehicle() instanceof ArmoredCarEntity car)
                || car.getControllingPassenger() != minecraft.player) {
            resetLocalState();
            return;
        }

        int vehicleId = car.getId();
        boolean acceptsInput = minecraft.screen == null && minecraft.isWindowActive();
        float throttle = acceptsInput
                ? (minecraft.options.keyUp.isDown() == minecraft.options.keyDown.isDown() ? 0.0f
                    : minecraft.options.keyUp.isDown() ? 1.0f : -1.0f)
                : 0.0f;
        float steering = acceptsInput
                ? (minecraft.options.keyRight.isDown() == minecraft.options.keyLeft.isDown() ? 0.0f
                    : minecraft.options.keyRight.isDown() ? 1.0f : -1.0f)
                : 0.0f;
        boolean handbrake = !acceptsInput || HANDBRAKE.isDown();
        var testControls = cn.minerealms.assimploader.client.VehicleSmokeTest.controls();
        if (testControls != null) {
            throttle = testControls.throttle();
            steering = testControls.steering();
            handbrake = testControls.brake();
        }
        ticksSinceSend++;
        if (vehicleId != lastVehicleId || throttle != lastThrottle || steering != lastSteering
                || handbrake != lastHandbrake || ticksSinceSend >= 5) {
            VehicleNetworking.sendInput(vehicleId, sequence++, throttle, steering, handbrake);
            lastVehicleId = vehicleId;
            lastThrottle = throttle;
            lastSteering = steering;
            lastHandbrake = handbrake;
            ticksSinceSend = 0;
        }
    }

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.assimploader.vehicle_" + name, InputConstants.Type.KEYSYM,
                key, "key.categories.assimploader");
    }

    private static void resetLocalState() {
        lastVehicleId = -1;
        lastThrottle = lastSteering = 0.0f;
        lastHandbrake = false;
        ticksSinceSend = 0;
    }

    private VehicleClientInput() {}
}
