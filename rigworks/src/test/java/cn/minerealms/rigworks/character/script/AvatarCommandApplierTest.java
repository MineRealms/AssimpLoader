package cn.minerealms.rigworks.character.script;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AvatarCommandApplierTest {
    @Test void repeatedLayerDeclarationsPreservePlaybackAndFallbackRemovesOnlyOwnedLayers() {
        try(var animation=new AnimationState("avatar-script-test",scene())) {
            animation.initialize();animation.setLayer("another-mod:layer","Idle",1,false,java.util.Map.of());
            var nativeCalls=new AtomicInteger();
            try(var applier=new AvatarCommandApplier(animation,null,null,nativeCalls::incrementAndGet,()->{})) {
                var layer=command("layer","{\"id\":\"arms\",\"clip\":\"Idle\",\"weight\":0.5,\"additive\":false,\"mask\":{\"Head\":1}}");
                var frame=new AvatarScriptRuntime.Result(true,List.of(command("defaultAnimation","{}"),layer),"");
                assertTrue(applier.apply(frame));
                var player=animation.getLayer("rigworks:avatar:arms").getPlayer();
                animation.update(.1f);double phase=player.getCurrentTime();
                assertTrue(applier.apply(frame));
                assertSame(player,animation.getLayer("rigworks:avatar:arms").getPlayer());
                assertEquals(phase,player.getCurrentTime());assertEquals(2,nativeCalls.get());
                assertFalse(applier.apply(AvatarScriptRuntime.Result.fallback("disabled")));
                assertNull(animation.getLayer("rigworks:avatar:arms"));assertNotNull(animation.getLayer("another-mod:layer"));
            }
        }
    }

    @Test void invalidReferencesRejectWholeFrameAndRestoreEarlierControls() {
        try(var animation=new AnimationState("avatar-script-test",scene());
            var applier=new AvatarCommandApplier(animation,null,null,()->{},()->{})) {
            animation.initialize();
            applier.apply(new AvatarScriptRuntime.Result(true,List.of(command("layer","{\"id\":\"arms\",\"clip\":\"Idle\",\"weight\":1,\"additive\":false,\"mask\":{}}")),""));
            var calls=new AtomicInteger();
            try(var guarded=new AvatarCommandApplier(animation,null,null,calls::incrementAndGet,()->{})) {
                var invalid=new AvatarScriptRuntime.Result(true,List.of(command("defaultAnimation","{}"),command("clip","{\"clip\":\"missing\",\"blend\":0}")),"");
                assertThrows(IllegalArgumentException.class,()->guarded.apply(invalid));assertEquals(0,calls.get());
            }
            assertThrows(IllegalArgumentException.class,()->applier.apply(new AvatarScriptRuntime.Result(true,List.of(command("clip","{\"clip\":\"missing\",\"blend\":0}")),"")));
            assertNull(animation.getLayer("rigworks:avatar:arms"));
        }
    }

    private static AvatarCommand command(String type,String json){return new AvatarCommand(type,JsonParser.parseString(json).getAsJsonObject());}
    private static AssimpScene scene() {
        ByteBuffer data=ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
        for(float value:new float[]{0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0})data.putFloat(value);
        String json="""
                {"asset":{"version":"2.0"},"buffers":[{"byteLength":68,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":8},
                 {"buffer":0,"byteOffset":44,"byteLength":24}],"accessors":[
                 {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]},
                 {"bufferView":1,"componentType":5126,"count":2,"type":"SCALAR","min":[0],"max":[1]},
                 {"bufferView":2,"componentType":5126,"count":2,"type":"VEC3"}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"Root","children":[1]},{"name":"Head","mesh":0}],
                 "animations":[{"name":"Idle","samplers":[{"input":1,"output":2}],
                 "channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(data.array()));
        var scene=new AssimpModelLoader().loadModelFromMemory("avatar-script-test.gltf",json.getBytes(StandardCharsets.UTF_8),ModelImportMode.DYNAMIC);
        assertNotNull(scene);return scene;
    }
}
