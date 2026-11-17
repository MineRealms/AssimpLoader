package cn.minerealms.assimploader.debug;

import cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView;
import cn.minerealms.assimploader.material.LabPbrPacker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialDebugViewsTest {
    @Test void packedLabPbrChannelsDecodeToIndependentOpaqueAlbedoPlanes() {
        var albedo=new LabPbrPacker.Image(1,1,new int[]{0xff112233});
        var maps=new LabPbrPacker.PackedTextures(
                new LabPbrPacker.Image(1,1,new int[]{0xff408080}),
                new LabPbrPacker.Image(1,1,new int[]{0x2000ff40}));
        assertEquals(0xff112233,MaterialDebugViews.image(MaterialView.ALBEDO,albedo,maps).pixel(0,0));
        assertEquals(0xff404040,MaterialDebugViews.image(MaterialView.AO,albedo,maps).pixel(0,0));
        assertEquals(0xffbfbfbf,MaterialDebugViews.image(MaterialView.ROUGHNESS,albedo,maps).pixel(0,0));
        assertEquals(0xffffffff,MaterialDebugViews.image(MaterialView.METALLIC,albedo,maps).pixel(0,0));
        assertEquals(0xff202020,MaterialDebugViews.image(MaterialView.EMISSION,albedo,maps).pixel(0,0));
        int normal=MaterialDebugViews.image(MaterialView.NORMAL,albedo,maps).pixel(0,0);
        assertTrue((normal>>>16&255)>=254);
        assertEquals(127,normal>>>8&255);
    }
    @Test void missingChannelsUseNeutralValuesAndLargeTexturesAreBounded() {
        assertEquals(0xffffffff,MaterialDebugViews.image(MaterialView.AO,null,null).pixel(0,0));
        assertEquals(0xff000000,MaterialDebugViews.image(MaterialView.METALLIC,null,null).pixel(0,0));
        assertEquals(0xff000000,MaterialDebugViews.image(MaterialView.EMISSION,null,null).pixel(0,0));
        var large=new LabPbrPacker.Image(2048,2,new int[4096]);
        var result=MaterialDebugViews.image(MaterialView.ALBEDO,large,null);
        assertEquals(1024,result.width());assertEquals(2,result.height());
    }
}
