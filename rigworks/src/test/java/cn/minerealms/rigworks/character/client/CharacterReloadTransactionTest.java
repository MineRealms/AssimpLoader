package cn.minerealms.rigworks.character.client;

import cn.minerealms.rigworks.compat.kubejs.CharacterActionFrame;
import cn.minerealms.rigworks.compat.kubejs.CharacterScriptApi;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CharacterReloadTransactionTest {
    public static final class Counter {
        private int value;
        public void set(int value){this.value=value;}
        public int get(){return value;}
    }
    @AfterEach void clear(){CharacterScriptRuntime.clear();}

    @Test void failedSecondFileRetainsThePublishedCallbackAndRevision(){
        var oldCalls=new AtomicInteger();
        CharacterScriptRuntime.register("pack:good",frame->oldCalls.incrementAndGet());
        long revision=CharacterScriptRuntime.revision();
        var callback=CharacterScriptRuntime.publishedController("pack:good");
        try(var transaction=CharacterScriptRuntime.beginRegistrationReload()){
            CharacterScriptRuntime.register("pack:new",frame->fail("Uncommitted callback"));
            assertNull(CharacterScriptRuntime.publishedController("pack:new"));
            CharacterScriptRuntime.recordReloadError("second.js failed");
            transaction.complete();
        }
        assertEquals(revision,CharacterScriptRuntime.revision());
        assertEquals("pack:good",CharacterScriptRuntime.active());
        assertSame(callback,CharacterScriptRuntime.publishedController("pack:good"));
        callback.accept(null);assertEquals(1,oldCalls.get());
        assertEquals("second.js failed",CharacterScriptRuntime.reloadFailure());
    }

    @Test void successfulEmptyReloadPublishesAnEmptyRegistryExactlyOnce(){
        CharacterScriptRuntime.register("pack:old",frame->{});long revision=CharacterScriptRuntime.revision();
        try(var transaction=CharacterScriptRuntime.beginRegistrationReload()){transaction.complete();}
        assertEquals(revision+1,CharacterScriptRuntime.revision());
        assertTrue(CharacterScriptRuntime.controllers().isEmpty());assertNull(CharacterScriptRuntime.active());
    }

    @Test void bindingsPreserveWrappedStagingButUnwrappedLoadsClearDeletedControllers(){
        CharacterScriptRuntime.register("pack:old",frame->{});long revision=CharacterScriptRuntime.revision();
        try(var transaction=CharacterScriptRuntime.beginRegistrationReload()){
            CharacterScriptRuntime.register("pack:staged",frame->{});
            CharacterScriptRuntime.prepareClientBindings();
            assertEquals(java.util.List.of("pack:staged"),CharacterScriptRuntime.controllers());
            assertEquals(revision,CharacterScriptRuntime.revision());transaction.complete();
        }
        assertEquals("pack:staged",CharacterScriptRuntime.active());
        CharacterScriptRuntime.prepareClientBindings();
        assertTrue(CharacterScriptRuntime.controllers().isEmpty());assertNull(CharacterScriptRuntime.active());
    }

    @Test void thrownSetupAndStagedClearCannotRemoveThePublishedRegistry(){
        CharacterScriptRuntime.register("pack:old",frame->{});long revision=CharacterScriptRuntime.revision();
        assertThrows(IllegalStateException.class,()->{
            try(var transaction=CharacterScriptRuntime.beginRegistrationReload()){
                CharacterScriptRuntime.clear();throw new IllegalStateException("binding setup failed");
            }
        });
        assertEquals(revision,CharacterScriptRuntime.revision());assertEquals("pack:old",CharacterScriptRuntime.active());
    }

    @Test void nestedReloadAndExplicitStagedSelectionCommitOnlyAtTheOuterBoundary(){
        CharacterScriptRuntime.register("pack:old",frame->{});long revision=CharacterScriptRuntime.revision();
        try(var outer=CharacterScriptRuntime.beginRegistrationReload()){
            CharacterScriptRuntime.register("pack:a",frame->{});
            try(var inner=CharacterScriptRuntime.beginRegistrationReload()){
                CharacterScriptRuntime.register("pack:b",frame->{});CharacterScriptRuntime.use("pack:a");inner.complete();
            }
            assertEquals(revision,CharacterScriptRuntime.revision());outer.complete();
        }
        assertEquals(revision+1,CharacterScriptRuntime.revision());assertEquals("pack:a",CharacterScriptRuntime.active());
        assertEquals(2,CharacterScriptRuntime.controllers().size());
    }

    @Test void concurrentLiveRegistrationIsNotOverwrittenByAStaleReload() throws Exception {
        CharacterScriptRuntime.register("pack:old",frame->{});
        try(var transaction=CharacterScriptRuntime.beginRegistrationReload()){
            CharacterScriptRuntime.register("pack:staged",frame->{});
            var other=new Thread(()->CharacterScriptRuntime.register("pack:live",frame->{}));other.start();other.join();
            transaction.complete();
        }
        assertEquals("pack:live",CharacterScriptRuntime.active());
        assertFalse(CharacterScriptRuntime.controllers().contains("pack:staged"));
    }

    @Test void realPinnedRhinoClosureStillRunsAfterFailedReloadReplacesItsContextAndScope(){
        Context oldContext=Context.enter();var oldScope=oldContext.initStandardObjects();
        var counter=new Counter();
        ScriptableObject.putProperty(oldScope,"RigWorks",Context.javaToJS(oldContext,CharacterScriptApi.INSTANCE,oldScope),oldContext);
        ScriptableObject.putProperty(oldScope,"counter",Context.javaToJS(oldContext,counter,oldScope),oldContext);
        try(var transaction=CharacterScriptRuntime.beginRegistrationReload()){
            oldContext.evaluateString(oldScope,"var retained = 40; RigWorks.register('pack:rhino', function(frame) { retained++; counter.set(retained); });","good.js",1,null);
            transaction.complete();
        }
        long revision=CharacterScriptRuntime.revision();var callback=CharacterScriptRuntime.publishedController("pack:rhino");
        callback.accept(null);assertEquals(41,counter.get());
        Context newContext=Context.enter();var newScope=newContext.initStandardObjects();
        assertNotSame(oldContext,newContext);assertNotSame(oldScope,newScope);
        ScriptableObject.putProperty(newScope,"RigWorks",Context.javaToJS(newContext,CharacterScriptApi.INSTANCE,newScope),newContext);
        try(var transaction=CharacterScriptRuntime.beginRegistrationReload()){
            newContext.evaluateString(newScope,"RigWorks.register('pack:partial', function(frame) {});","first.js",1,null);
            assertThrows(RuntimeException.class,()->newContext.evaluateString(newScope,"function (", "second.js",1,null));
            CharacterScriptRuntime.recordReloadError("second.js syntax error");transaction.complete();
        }
        assertEquals(revision,CharacterScriptRuntime.revision());assertSame(callback,CharacterScriptRuntime.publishedController("pack:rhino"));
        callback.accept(null);assertEquals(42,counter.get());
    }

    @Test void resourceAndInlineNativeApisHaveDistinctNamesForRhino(){
        var methods=Arrays.stream(CharacterActionFrame.class.getDeclaredMethods()).filter(method->method.getName().equals("nativeAnimation")).toList();
        assertEquals(1,methods.size());assertArrayEquals(new Class<?>[]{String.class},methods.get(0).getParameterTypes());
        assertDoesNotThrow(()->CharacterActionFrame.class.getMethod("nativeAnimationDefinition",java.util.Map.class));
    }
}
