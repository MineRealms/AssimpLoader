package cn.minerealms.assimploader.animation.physics;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SpringBoneModifierTest {
    private AssimpScene scene() {
        String json = """
                {"asset":{"version":"2.0"},"nodes":[{"name":"Parent","children":[1]},
                 {"name":"Joint"}],"scenes":[{"nodes":[0]}],"scene":0}
                """;
        AssimpScene result = new AssimpModelLoader().loadModelFromMemory("spring.gltf", json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        assertNotNull(result);
        return result;
    }
    private SpringBoneModifier.Settings settings() {
        return new SpringBoneModifier.Settings("Joint", new Vector3f(1,0,0), 12, 3, new Vector3f(0,-9.81f,0), .02f, .01, 8, 5);
    }

    @Test void fixedStepIsIndependentOfFramePartitionAndKeepsLength() {
        AssimpScene scene = scene();
        SpringBoneModifier a = new SpringBoneModifier(settings()), b = new SpringBoneModifier(settings());
        Map<String,Matrix4f> first = new LinkedHashMap<>(), second = new LinkedHashMap<>();
        a.apply(scene, first, 0); b.apply(scene, second, 0);
        for (int i = 0; i < 20; i++) { first.clear(); a.apply(scene, first, .05); }
        for (int i = 0; i < 200; i++) { second.clear(); b.apply(scene, second, .005); }
        assertTrue(a.getTailPosition().distance(b.getTailPosition()) < 1e-5);
        assertEquals(1, a.getTailPosition().length(), 1e-5);
        assertTrue(a.getTailPosition().y < -.1f);
        Vector3f rendered = new PoseGraph(scene.getRootNode(), first).global("Joint").transformPosition(new Vector3f(1,0,0));
        assertTrue(rendered.distance(a.getTailPosition()) < 1e-5);
        assertEquals(new Matrix4f(), scene.getRootNode().find("Joint").getTransformation());
    }

    @Test void hitchResetAndTeleportHaveBoundedWorkAndNoStaleVelocity() {
        AssimpScene scene = scene();
        SpringBoneModifier spring = new SpringBoneModifier(settings());
        Map<String,Matrix4f> locals = new LinkedHashMap<>();
        spring.apply(scene, locals, 1000);
        assertEquals(8, spring.getLastSubsteps());
        locals.clear(); spring.apply(scene, locals, 0);
        assertEquals(0, spring.getLastSubsteps());
        spring.reset(); locals.clear(); spring.apply(scene, locals, 0);
        assertEquals(new Vector3f(1,0,0), spring.getTailPosition());
        locals.clear(); locals.put("Parent", new Matrix4f().translation(100,0,0));
        spring.apply(scene, locals, 0);
        assertEquals(new Vector3f(101,0,0), spring.getTailPosition());
    }

    @Test void rotatedParentUsesModelSpaceGravityAndRestoresLocalRotation() {
        AssimpScene scene = scene();
        SpringBoneModifier spring = new SpringBoneModifier(settings());
        Map<String,Matrix4f> locals = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            locals.clear(); locals.put("Parent", new Matrix4f().rotateY(1));
            spring.apply(scene, locals, .01);
        }
        Vector3f rendered = new PoseGraph(scene.getRootNode(), locals).global("Joint").transformPosition(new Vector3f(1,0,0));
        assertTrue(rendered.distance(spring.getTailPosition()) < 1e-5);
        assertTrue(rendered.y < -.1f);
    }

    @Test void nearOppositeTailPreservesScaleAndMatchesPublishedPose() {
        AssimpScene scene = scene();
        Vector3f axis = new Vector3f(.1501f, -.9393f, .3086f).normalize();
        Vector3f tail = new Vector3f(axis).mul(.002065443f);
        Vector3f turnAxis = new Vector3f(axis).cross(new Vector3f(0, 0, 1)).normalize();
        for (float angle : new float[]{(float) Math.PI - .000321f, (float) Math.PI}) {
            var settings = new SpringBoneModifier.Settings("Joint", tail, 0, 0, new Vector3f(), 0, .01, 8, 5);
            var spring = new SpringBoneModifier(settings);
            Map<String, Matrix4f> locals = new LinkedHashMap<>();
            locals.put("Parent", new Matrix4f().translation(.2f, 1, -.15f).scale(100));
            spring.apply(scene, locals, 0);
            Vector3f particle = spring.getTailPosition();
            locals.put("Joint", new Matrix4f().rotate(angle, turnAxis));
            spring.apply(scene, locals, 0);

            var pose = cn.minerealms.assimploader.animation.PoseSnapshot.capture(1, scene, locals);
            Vector3f rendered = pose.getNodeTransform("Joint").transformPosition(new Vector3f(tail));
            assertTrue(rendered.distance(particle) < 2e-6f, "Published tip differs from the stored particle: " + angle);
            assertTrue(locals.get("Joint").getScale(new Vector3f()).distance(new Vector3f(1)) < 2e-5f,
                    "Spring rotation must preserve the animated scale: " + angle);
            assertEquals(particle, spring.getTailPosition());
        }
    }

    @Test void modelSpaceSphereDeflectsTailAndSettingsAreDefensive() {
        AssimpScene scene = scene();
        SpringBoneModifier.Settings settings = settings();
        settings.tail().zero(); settings.gravity().zero();
        SpringBoneModifier spring = new SpringBoneModifier(settings,
                List.of(new SpringBoneModifier.Sphere(null, new Vector3f(0,-1,0), .35f)));
        Map<String,Matrix4f> locals = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) { locals.clear(); spring.apply(scene, locals, .01); }
        assertTrue(spring.getTailPosition().distance(new Vector3f(0,-1,0)) >= .369f);
        assertEquals(1, spring.getTailPosition().length(), 1e-5);
        assertThrows(IllegalArgumentException.class, () -> spring.apply(scene, locals, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new SpringBoneModifier.Settings("Joint", new Vector3f(), 1, 1, new Vector3f(), 0, .01, 8, 5));
    }

    @Test void zeroTimeRepublicationDoesNotIntegrateOrAccumulateRotation() {
        AssimpScene scene = scene();
        SpringBoneModifier spring = new SpringBoneModifier(settings());
        Map<String,Matrix4f> locals = new LinkedHashMap<>();
        spring.apply(scene, locals, .05);
        Vector3f before = spring.getTailPosition();
        Matrix4f rotation = new Matrix4f(locals.get("Joint"));
        for (int i = 0; i < 100; i++) {
            locals.clear(); spring.apply(scene, locals, 0);
            assertEquals(0, spring.getLastSubsteps());
            assertEquals(before, spring.getTailPosition());
            assertTrue(rotation.equals(locals.get("Joint"), 1e-6f));
        }
    }

    @Test void snapshotBoneProxyUsesNodeTransformAndCopiesArguments() {
        AssimpScene scene = scene();
        cn.minerealms.assimploader.animation.PoseSnapshot snapshot = cn.minerealms.assimploader.animation.PoseSnapshot.capture(1, scene,
                Map.of("Joint", new Matrix4f().translation(3,0,0).scale(2,1,1)));
        Vector3f tail = new Vector3f(0,1,0);
        cn.minerealms.assimploader.collision.BoneCollisionProxy proxy = new cn.minerealms.assimploader.collision.BoneCollisionProxy("Joint", new Vector3f(), tail, .25f);
        tail.zero();
        cn.minerealms.assimploader.collision.RayHit hit = proxy.at(snapshot).raycast(
                new cn.minerealms.assimploader.collision.Ray(new Vector3f(0,.5f,0), new Vector3f(1,0,0)), 10).orElseThrow();
        assertEquals(2.5f, hit.distance(), 1e-6);
        assertEquals(3, snapshot.getNodeTransform("Joint").m30(), 1e-6);
    }
}
