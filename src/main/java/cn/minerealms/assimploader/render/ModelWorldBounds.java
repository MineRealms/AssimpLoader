package cn.minerealms.assimploader.render;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;

/** Affine visual bounds keep the world anchor in double precision. */
public final class ModelWorldBounds {
    private ModelWorldBounds() {}

    public static AABB transform(AABB bounds, Matrix4fc local, double x, double y, double z) {
        if (!local.isFinite() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite visual transform");
        double lowX=Double.POSITIVE_INFINITY, lowY=lowX, lowZ=lowX;
        double highX=Double.NEGATIVE_INFINITY, highY=highX, highZ=highX;
        for (int corner=0;corner<8;corner++) {
            double px=(corner&1)==0?bounds.minX:bounds.maxX;
            double py=(corner&2)==0?bounds.minY:bounds.maxY;
            double pz=(corner&4)==0?bounds.minZ:bounds.maxZ;
            double wx=x+local.m00()*px+local.m10()*py+local.m20()*pz+local.m30();
            double wy=y+local.m01()*px+local.m11()*py+local.m21()*pz+local.m31();
            double wz=z+local.m02()*px+local.m12()*py+local.m22()*pz+local.m32();
            lowX=Math.min(lowX,wx);lowY=Math.min(lowY,wy);lowZ=Math.min(lowZ,wz);
            highX=Math.max(highX,wx);highY=Math.max(highY,wy);highZ=Math.max(highZ,wz);
        }
        return new AABB(lowX,lowY,lowZ,highX,highY,highZ);
    }
}
