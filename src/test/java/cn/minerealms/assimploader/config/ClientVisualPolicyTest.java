package cn.minerealms.assimploader.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientVisualPolicyTest {
    @Test void hostModePreservesSuppliedFeatureAndAnimationInputs() {
        var policy=new ClientVisualPolicy(ClientVisualPolicy.Mode.HOST_CONTROLLED,true,false);
        assertFalse(policy.ground(false));assertTrue(policy.ground(true));
        assertFalse(policy.effects(false));assertTrue(policy.effects(true));
        assertEquals("gallop",policy.animation("gallop","auto"));
    }
    @Test void localModeUsesOnlyConfiguredVisualSwitchesAndLocalAnimationInput() {
        var policy=new ClientVisualPolicy(ClientVisualPolicy.Mode.CLIENT_LOCAL,true,false);
        assertTrue(policy.ground(false));assertTrue(policy.ground(true));
        assertFalse(policy.effects(false));assertFalse(policy.effects(true));
        assertEquals("auto",policy.animation("gallop","auto"));
        var disabledGround=new ClientVisualPolicy(ClientVisualPolicy.Mode.CLIENT_LOCAL,false,true);
        assertFalse(disabledGround.ground(true));assertTrue(disabledGround.effects(false));
        assertThrows(NullPointerException.class,()->new ClientVisualPolicy(null,true,false));
        assertSame(ClientVisualPolicy.current(),ClientVisualPolicy.current());
    }
}
