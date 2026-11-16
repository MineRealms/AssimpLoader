package cn.minerealms.assimploader.render;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelWorldBoundsTest {
    @Test void rotatedBoundsPreserveSubBlockOffsetsAtWorldBorder() {
        var box=new AABB(-.7,-.1,-1.3,.4,2.4,.8);
        var matrix=new Matrix4f().translate(.125f,.25f,-.25f).rotateY((float)(Math.PI/2)).scale(2,.5f,-1.5f);
        double anchor=29_999_999.125;
        var world=ModelWorldBounds.transform(box,matrix,anchor,120,-anchor);
        assertEquals(anchor-1.075,world.minX,2e-6);
        assertEquals(anchor+2.075,world.maxX,2e-6);
        assertEquals(120.2,world.minY,2e-6);
        assertEquals(121.45,world.maxY,2e-6);
        assertEquals(-anchor-1.05,world.minZ,2e-6);
        assertEquals(-anchor+1.15,world.maxZ,2e-6);
        var shifted=ModelWorldBounds.transform(box,matrix,anchor+.125,120,-anchor);
        assertEquals(.125,shifted.minX-world.minX,1e-8);
        assertEquals(.125,shifted.maxX-world.maxX,1e-8);
    }
}
