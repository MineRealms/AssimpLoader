package cn.minerealms.rigworks.character.client;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterFishingLineTest {
    @Test void mainAndOffhandFollowPhysicalMainArm() {
        assertEquals(HumanoidArm.RIGHT, CharacterFishingLine.physicalArm(HumanoidArm.RIGHT, true));
        assertEquals(HumanoidArm.LEFT, CharacterFishingLine.physicalArm(HumanoidArm.RIGHT, false));
        assertEquals(HumanoidArm.LEFT, CharacterFishingLine.physicalArm(HumanoidArm.LEFT, true));
        assertEquals(HumanoidArm.RIGHT, CharacterFishingLine.physicalArm(HumanoidArm.LEFT, false));
    }

    @Test void rodTipInheritsDisplayRotationAndRenderedSocketScale() {
        Matrix4f world = new Matrix4f().translation(10, 20, 30).rotateY((float)Math.PI / 2).scale(2);
        Matrix4f display = new Matrix4f().translate(0, .25f, .15625f).rotateXYZ(0, (float)Math.PI / 2, .96f).scale(.85f);
        Vector3f expected = display.transformPosition(new Vector3f(.40625f, .40625f, 0));
        world.transformPosition(expected);
        assertTrue(expected.distance(CharacterFishingLine.transformTip(world, display)) < 1e-5f);
        assertEquals(new Matrix4f().translation(10,20,30).rotateY((float)Math.PI/2).scale(2), world);
    }

    @Test void originalStringCurveEndsExactlyAtRodTipWithoutDuplicateQuarterBlockOffset() {
        Vec3 endpoint = new Vec3(12.5, 72.2, -3.6);
        Vector3f delta = CharacterFishingLine.hookDelta(endpoint, 10, 70, -5);
        Vector3f curveEnd = new Vector3f(10, 70, -5).add(delta.x, delta.y + .25f, delta.z);
        assertEquals(endpoint.x, curveEnd.x, 1e-5);
        assertEquals(endpoint.y, curveEnd.y, 1e-5);
        assertEquals(endpoint.z, curveEnd.z, 1e-5);
    }
}
