package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.client.ui.LookDevNavigation;
import cn.minerealms.assimploader.debug.ClientDiagnostics;
import cn.minerealms.rigworks.RigWorks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

/** Supplies optional UI and diagnostics through engine extension points. */
@Mod.EventBusSubscriber(modid = RigWorks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RigWorksClientSetup {
    private RigWorksClientSetup() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            LookDevNavigation.registerPage(LookDevNavigation.Page.PLAYER, () -> new CharacterSelectionScreen(null));
            ClientDiagnostics.register(RigWorks.MODID, LocalPlayerCharacter::currentPhysics, () -> {
                var physics = LocalPlayerCharacter.currentPhysics();
                String details = physics == null ? "RigPhysics OFF" : "Physics bodies/joints "
                        + physics.statistics().bodies() + "/" + physics.statistics().joints()
                        + "  substeps " + physics.statistics().lastSubsteps();
                return List.of(details, "RigWorks " + LocalPlayerCharacter.animationMode());
            });
        });
    }
}
