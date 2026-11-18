package cn.minerealms.rigworks.character.script;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AvatarScriptRuntimeTest {
    private static AvatarScriptBundle bundle(String code) {
        return new AvatarScriptBundle("test","test:models/player/profile.json","test:controller",Map.of("controller.js",code));
    }
    private static String controller(String body){return "RigWorks.register('test:controller', function(frame) { "+body+" });";}

    @Test void pureJsControllerReturnsTypedCommandsAndSupportsArrowRegistration() {
        try(var session=AvatarScriptRuntime.open(bundle("RigWorks.register('test:controller', frame => { frame.defaultAnimation(); frame.play('walk',.2); });"))) {
            assertTrue(session.available(),session.failure());var result=session.execute(new JsonObject());
            assertTrue(result.handled(),result.failure());assertEquals(2,result.commands().size());
            assertEquals("defaultAnimation",result.commands().get(0).type());assertEquals("walk",result.commands().get(1).args().get("clip").getAsString());
        }
    }

    @Test void ownerStatesAreIndependentAndResetOnlyTouchesOneSession() {
        var source=bundle(controller("var state=frame.state(); state.count=(state.count||0)+1; frame.play('step'+state.count,0);"));
        try(var first=AvatarScriptRuntime.open(source);var second=AvatarScriptRuntime.open(source)) {
            assertEquals("step1",clip(first));assertEquals("step2",clip(first));assertEquals("step1",clip(second));
            first.reset();assertEquals("step1",clip(first));assertEquals("step2",clip(second));
        }
    }

    @Test void frameInputIsFrozenAndDoesNotExposeJavaObjectsOrClasses() {
        String script=controller("""
                var blocked=0;
                try { Java.type('java.lang.System'); } catch(e) { blocked++; }
                try { Packages.java.lang.System.exit(0); } catch(e) { blocked++; }
                try { (function(){}).constructor('return java.lang.System')(); } catch(e) { blocked++; }
                try { frame.input().speed=999; } catch(e) { blocked++; }
                if(typeof frame.model().getClass!=='undefined'||typeof frame.getClass!=='undefined') throw new Error('Java leak');
                if(frame.input().speed!==2||blocked<3) throw new Error('Sandbox was mutable');
                frame.play('safe',0);
                """);
        try(var session=AvatarScriptRuntime.open(bundle(script))) {
            var input=new JsonObject();input.addProperty("speed",2);var result=session.execute(input);
            assertTrue(result.handled(),result.failure());assertEquals("safe",result.commands().get(0).args().get("clip").getAsString());
            assertEquals(2,input.get("speed").getAsInt());
        }
    }

    @Test void infiniteLoopAndCatchRetryLoopExhaustInstructionBudgetAndDisableSession() {
        for(String body:new String[]{"while(true){}","while(true){try{while(true){}}catch(error){}}"}) {
            assertTimeoutPreemptively(Duration.ofSeconds(3),()-> {
                try(var session=AvatarScriptRuntime.open(bundle(controller(body)))) {
                    assertTrue(session.available(),session.failure());var result=session.execute(new JsonObject());
                    assertFalse(result.handled());assertTrue(result.failure().contains("budget"),result.failure());assertFalse(session.available());
                    assertFalse(session.execute(new JsonObject()).handled());
                }
            });
        }
    }

    @Test void startupLoopsSyntaxFailuresAndMissingControllerFallBack() {
        for(String script:new String[]{"while(true){}","RigWorks.register('test:controller', ( => {});","RigWorks.register('test:other', function(){});"}) {
            assertTimeoutPreemptively(Duration.ofSeconds(3),()-> {
                try(var session=AvatarScriptRuntime.open(bundle(script))) {
                    assertFalse(session.available());assertFalse(session.execute(new JsonObject()).handled());assertFalse(session.failure().isEmpty());
                }
            });
        }
    }

    @Test void malformedNonfiniteOrExcessiveCommandOutputIsRejectedAsAWhole() {
        for(String body:new String[]{"frame.ikTarget('leg',NaN,0,0);","frame.morph('face',Infinity);","for(var n=0;n<129;n++)frame.defaultAnimation();"}) {
            try(var session=AvatarScriptRuntime.open(bundle(controller(body)))) {
                var result=session.execute(new JsonObject());assertFalse(result.handled());assertTrue(result.commands().isEmpty());assertFalse(session.failure().isEmpty());
            }
        }
    }

    @Test void nativeMathAndMappingsRemainUsableWithoutBuiltinFreezeReflectionBugs() {
        var source=new AvatarScriptBundle("test","test:models/player/profile.json","test:controller",Map.of(
                "manifest.json","{\"version\":1,\"mappings\":\"mappings.json\"}",
                "mappings.json","{\"bones\":{\"right_hand\":{\"node\":\"HandR\"}},\"animations\":{\"walk\":\"Walk\"}}",
                "controller.js",controller("if(Math.abs(Math.sin(Math.PI/2)-1)>.0001||frame.bone('right_hand')!=='HandR')throw new Error('Math or mapping');frame.play(frame.clip('walk'),0);")));
        try(var session=AvatarScriptRuntime.open(source)) {
            var result=session.execute(new JsonObject());assertTrue(result.handled(),result.failure());assertEquals("Walk",result.commands().get(0).args().get("clip").getAsString());
        }
    }

    @Test void sealedNativeFunctionsKeepCallAndApplyWithoutEnablingDynamicConstruction() {
        try(var session=AvatarScriptRuntime.open(bundle(controller("""
                var add=function(a,b){return a+b;};
                if(add.call(null,2,3)!==5||add.apply(null,[4,5])!==9)throw new Error('Function prototype lost');
                var values=new Array(2);
                values[0]=1;values[1]=2;
                values.push(3);
                values.unshift(0);
                values.splice(1,0,7);
                if(values.concat([4]).length!==6||'x'.repeat(2)!=='xx')throw new Error('Native function call lost');
                var blocked=0;
                try { Function('return 1')(); } catch(error) { blocked++; }
                try { add.constructor('return 1')(); } catch(error) { blocked++; }
                if(blocked!==2)throw new Error('Dynamic construction enabled');
                frame.defaultAnimation();
                """)))) {
            assertTrue(session.available(),session.failure());var result=session.execute(new JsonObject());
            assertTrue(result.handled(),result.failure());assertEquals("defaultAnimation",result.commands().get(0).type());
        }
    }

    @Test void regexBuiltinAndStringBombsAreUnavailable() {
        for(String body:new String[]{"var x=/(a+)+$/.test('aaa');","var x='x'.repeat(1000000000);"}) {
            try(var session=AvatarScriptRuntime.open(bundle(controller(body)))) {
                assertFalse(session.execute(new JsonObject()).handled());assertFalse(session.failure().isEmpty());
            }
        }
    }

    @Test void returnedCommandsCannotBeMutatedAndClosedSessionsStayDisabled() {
        var session=AvatarScriptRuntime.open(bundle(controller("frame.play('idle',0);")));
        var result=session.execute(new JsonObject());assertTrue(result.handled(),result.failure());
        result.commands().get(0).args().addProperty("clip","other");
        assertEquals("idle",result.commands().get(0).args().get("clip").getAsString());
        assertThrows(UnsupportedOperationException.class,()->result.commands().clear());
        session.close();assertFalse(session.available());assertFalse(session.execute(new JsonObject()).handled());
    }

    private static String clip(AvatarScriptRuntime.Session session) {
        var result=session.execute(new JsonObject());assertTrue(result.handled(),result.failure());return result.commands().get(0).args().get("clip").getAsString();
    }
}
