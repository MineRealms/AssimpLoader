package cn.minerealms.rigworks;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Optional client character application built on the AssimpLoader engine. */
@Mod(RigWorks.MODID)
public final class RigWorks {
    public static final String MODID = "rigworks";

    public RigWorks() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RigWorksConfig.register();
        }
    }
}
