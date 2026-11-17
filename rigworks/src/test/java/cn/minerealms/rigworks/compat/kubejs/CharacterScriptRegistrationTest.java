package cn.minerealms.rigworks.compat.kubejs;

import cn.minerealms.rigworks.character.client.CharacterScriptRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CharacterScriptRegistrationTest {
    @AfterEach void clear() { CharacterScriptRuntime.clear(); }

    @Test void topLevelScriptCanRegisterOnTheModLoadingThreadWithoutTouchingMinecraft() throws Exception {
        var error=new AtomicReference<Throwable>();
        var thread=new Thread(()->{
            try {CharacterScriptApi.INSTANCE.register("pack:load",frame->fail("Registration must not execute the callback"));}
            catch(Throwable failure) {error.set(failure);}
        },"modloading-worker-test");
        thread.start();thread.join();
        assertNull(error.get());assertEquals("pack:load",CharacterScriptRuntime.active());
        assertTrue(CharacterScriptRuntime.controllers().contains("pack:load"));
    }

    @Test void registrationReplacementSelectionAndLimitsAreExplicit() {
        var api=CharacterScriptApi.INSTANCE;
        api.register("pack:a",ignored->{}).register("pack:b",ignored->{});
        assertEquals("pack:b",api.active());api.use("pack:a");assertEquals("pack:a",api.active());
        api.register("pack:a",ignored->{});assertEquals(2,api.controllers().size());
        api.unregister("pack:a");assertNull(api.active());
        assertThrows(IllegalArgumentException.class,()->api.use("missing"));
        assertThrows(IllegalArgumentException.class,()->api.register("bad name",ignored->{}));
        api.clear();for(int n=0;n<32;n++)api.register("pack:"+n,ignored->{});
        assertThrows(IllegalArgumentException.class,()->api.register("pack:overflow",ignored->{}));
        api.useDefault();assertNull(api.active());assertEquals(32,api.controllers().size());
    }
}
