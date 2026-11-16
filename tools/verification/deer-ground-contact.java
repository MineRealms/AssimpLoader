// Read only: execute the pose and collision queries on the client thread.
var client = net.minecraft.client.Minecraft.getInstance();
var latch = new java.util.concurrent.CountDownLatch(1);
var output = new java.util.concurrent.atomic.AtomicReference<String>();
client.execute(() -> {
    try {
        var deer = client.level.getEntitiesOfClass(cn.minerealms.assimploader.deer.DeerShowcaseEntity.class,
                client.player.getBoundingBox().inflate(128)).stream()
                .min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player))).orElseThrow();
        var scene = cn.minerealms.assimploader.core.AssimpModelLoader.getCachedModel(
                cn.minerealms.assimploader.deer.client.DeerShowcaseRenderer.MODEL_ID,
                cn.minerealms.assimploader.core.ModelImportMode.DYNAMIC);
        var state = cn.minerealms.assimploader.animation.ClientAnimationManager.getInstance()
                .getOrCreateAnimationState(deer, cn.minerealms.assimploader.deer.client.DeerShowcaseRenderer.MODEL_ID, scene);
        var pose = state.getPoseSnapshot();
        var mesh = scene.getMeshes().get(0);
        var vertices = cn.minerealms.assimploader.rendering.CpuSkinning.geometry(pose, mesh).positions();
        var skin = mesh.getSkinnedData();
        var indices = skin.getBoneIndices();
        var weights = skin.getBoneWeights();
        var boneNames = mesh.getBoneNames();
        int stride = weights.length / mesh.getVertexCount();
        var world = new org.joml.Matrix4f().translation((float) deer.getX(), (float) deer.getY(), (float) deer.getZ())
                .rotateY((float) Math.toRadians(-deer.getYRot()));
        var sampler = new cn.minerealms.assimploader.client.MinecraftGroundSampler(client.level);
        var report = new java.util.ArrayList<String>();
        report.add("state=" + state.getStateMachine().getCurrentStateName() + ", groundIK=" + deer.groundIkEnabled()
                + ", noGravity=" + deer.isNoGravity() + ", feet=" + state.getModelInstance().feet().keySet()
                + ", anchor=" + deer.position());
        for (String leg : java.util.List.of("FL", "FR", "BL", "BR")) {
            float lowest = Float.POSITIVE_INFINITY;
            var point = new org.joml.Vector3f();
            for (int vertex = 0; vertex < mesh.getVertexCount(); vertex++) {
                int slot = vertex * stride;
                if (weights[slot] < .98f || !boneNames.get(indices[slot]).equals("leg" + leg + "2")) continue;
                var candidate = world.transformPosition(new org.joml.Vector3f(vertices[vertex * 3], vertices[vertex * 3 + 1], vertices[vertex * 3 + 2]));
                if (candidate.y < lowest) { lowest = candidate.y; point.set(candidate); }
            }
            var ground = sampler.ground(new org.joml.Vector3f(point).add(0, 1, 0), 2).orElseThrow();
            report.add(leg + " sole=" + point + " ground=" + ground.y + " clearance=" + (point.y - ground.y));
        }
        output.set(String.join("; ", report));
    } catch (Throwable failure) {
        output.set(failure.toString());
    } finally {
        latch.countDown();
    }
});
if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Client pose query timed out");
return output.get();
