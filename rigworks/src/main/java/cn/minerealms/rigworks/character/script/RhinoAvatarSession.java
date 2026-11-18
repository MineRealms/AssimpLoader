package cn.minerealms.rigworks.character.script;

import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import com.google.gson.JsonObject;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.GeneratedClassLoader;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.WrapFactory;
import dev.latvian.mods.rhino.regexp.RegExp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** The optional Rhino dependency is confined to this implementation. No Java wrapper enters its scope. */
final class RhinoAvatarSession implements AvatarScriptRuntime.Session {
    private final AvatarScriptBundle source;
    private final Thread owner=Thread.currentThread();
    private BudgetContext context;
    private ScriptableObject scope;
    private Scriptable bridge;
    private Function run;
    private String failure="";
    private boolean closed;

    RhinoAvatarSession(AvatarScriptBundle source) {
        this.source=source;
        initialize();
    }
    private void initialize() {
        try {
            context=new BudgetContext();
            context.setClassShutter((name,type)->false);
            context.setWrapFactory(new NoJavaWrappers());
            context.setMaximumInterpreterStackDepth(128);
            context.setInstructionObserverThreshold(128);
            scope=context.initSafeStandardObjects();
            context.arm(1_000_000,500_000_000);
            bridge=(Scriptable)context.evaluateString(scope,BOOTSTRAP,"rigworks:sandbox",1,null);
            sealBuiltins();
            Function configure=(Function)ScriptableObject.getProperty(bridge,"configure",context);
            configure.call(context,scope,bridge,new Object[]{source.mappings().toString()});
            for(String path:source.scripts()) {
                context.check();
                context.evaluateString(scope,source.files().get(path),"rigworks:"+source.id()+"/"+path,1,null);
            }
            context.check();
            run=(Function)ScriptableObject.getProperty(bridge,"run",context);
            Function exists=(Function)ScriptableObject.getProperty(bridge,"registered",context);
            if(!Boolean.TRUE.equals(exists.call(context,scope,bridge,new Object[]{source.controller()})))
                throw new IllegalArgumentException("Avatar did not register controller: "+source.controller());
            scope.sealObject(context);failure="";
        } catch(AvatarExecutionBudget.Exceeded|RuntimeException|LinkageError error) {fail(error);}
    }
    @Override public AvatarScriptRuntime.Result execute(JsonObject input) {
        if(Thread.currentThread()!=owner)return AvatarScriptRuntime.Result.fallback("Avatar session belongs to its client tick thread");
        if(closed||run==null)return AvatarScriptRuntime.Result.fallback(failure.isEmpty()?"Avatar session is closed":failure);
        try {
            String json=input.toString();
            if(json.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Avatar frame exceeds 64 KiB");
            // Windows thread CPU accounting can advance in 15.625/31.25 ms steps.
            context.arm(100_000,50_000_000);
            Object value=run.call(context,scope,bridge,new Object[]{json,source.controller()});
            context.check();
            if(!(value instanceof String serialized)||serialized.getBytes(StandardCharsets.UTF_8).length>32768)
                throw new IllegalArgumentException("Avatar command/state output exceeds 32 KiB");
            var result=CharacterVisualConfig.parseBounded(serialized).getAsJsonObject();
            var commands=new ArrayList<AvatarCommand>();var output=result.getAsJsonArray("commands");
            if(output.size()>128)throw new IllegalArgumentException("Avatar exceeded 128 commands in one tick");
            if(result.get("state").toString().getBytes(StandardCharsets.UTF_8).length>16384)
                throw new IllegalArgumentException("Avatar persistent state exceeds 16 KiB");
            for(var command:output) {
                var object=command.getAsJsonObject();
                commands.add(new AvatarCommand(object.get("type").getAsString(),object.getAsJsonObject("args")));
            }
            return new AvatarScriptRuntime.Result(true,commands,"");
        } catch(AvatarExecutionBudget.Exceeded|RuntimeException|LinkageError error) {fail(error);return AvatarScriptRuntime.Result.fallback(failure);}
    }
    private void fail(Throwable error) {failure=AvatarScriptRuntime.shortError(error);run=null;bridge=null;scope=null;context=null;}
    private void sealBuiltins() {
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Scriptable,Boolean>());
        for(String name:new String[]{"Object","Array","String","Number","Boolean","Math","JSON","Function"}) {
            Object value=ScriptableObject.getProperty(scope,name,context);
            if(value instanceof Scriptable object)seal(object,seen);
        }
        seal(bridge,seen);
    }
    private void seal(Scriptable value,java.util.Set<Scriptable> seen) {
        if(value==null||!seen.add(value))return;
        Object prototype=ScriptableObject.getProperty(value,"prototype",context);
        if(prototype instanceof Scriptable object)seal(object,seen);
        seal(value.getPrototype(context),seen);
        if(value instanceof ScriptableObject object)object.sealObject(context);
    }
    @Override public String failure(){return failure;}
    @Override public boolean available(){return !closed&&run!=null;}
    @Override public String bundleHash(){return source.hash();}
    @Override public void reset(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Wrong avatar session thread");if(!closed)initialize();}
    @Override public void close(){closed=true;run=null;bridge=null;scope=null;context=null;}

    private static final class BudgetContext extends Context {
        private final AvatarExecutionBudget budget=new AvatarExecutionBudget();
        void arm(long limit,long nanos){budget.arm(limit,nanos);}
        void check(){budget.check();}
        @Override protected void observeInstructionCount(int count){budget.observe(count);}
        @Override public GeneratedClassLoader createClassLoader(ClassLoader parent){throw new SecurityException("Avatar Java code generation is disabled");}
        @Override public RegExp getRegExp(){throw new SecurityException("Avatar regular expressions are disabled; use simple string comparisons");}
    }
    private static final class NoJavaWrappers extends WrapFactory {
        @Override public Object wrap(Context context,Scriptable scope,Object value,Class<?> type){
            if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean||value instanceof Scriptable)return value;
            throw new SecurityException("Java objects are not available to avatar scripts");
        }
        @Override public Scriptable wrapAsJavaObject(Context context,Scriptable scope,Object value,Class<?> type){throw new SecurityException("Java objects are disabled");}
        @Override public Scriptable wrapJavaClass(Context context,Scriptable scope,Class<?> type){throw new SecurityException("Java classes are disabled");}
        @Override public Scriptable wrapNewObject(Scriptable scope,Object value,Context context){throw new SecurityException("Java constructors are disabled");}
    }

    private static final String BOOTSTRAP="""
            (function(global) {
              'use strict';
              var parse = JSON.parse, stringify = JSON.stringify, freeze = Object.freeze;
              var keys = Object.keys, create = Object.create, define = Object.defineProperty;
              var callbacks = create(null), state = create(null), mappings = freeze({});
              var disabled = function() { throw new Error('This operation is unavailable in avatar scripts'); };
              var functionPrototype = Function.prototype;
              // Rhino resolves lazy native functions through the global Function.prototype.
              define(disabled,'prototype',{value:functionPrototype,writable:false});
              functionPrototype.constructor = disabled;
              var OriginalArray = Array;
              var boundedArray = function() {
                var result=[];
                if (arguments.length === 1 && typeof arguments[0] === 'number') {
                  var length=arguments[0];
                  if(!(length>=0&&length<=4096&&Math.floor(length)===length))throw new Error('Invalid avatar array length');
                  result.length=length;
                } else {
                  if(arguments.length>4096)throw new Error('Avatar array exceeds 4096 entries');
                  for(var i=0;i<arguments.length;i++)result[i]=arguments[i];
                }
                return result;
              };
              boundedArray.prototype = OriginalArray.prototype;
              define(boundedArray,'isArray',{value:OriginalArray.isArray,writable:false});
              OriginalArray.prototype.constructor = boundedArray;
              ['push','unshift','splice','concat'].forEach(function(name) {
                var original=OriginalArray.prototype[name];
                OriginalArray.prototype[name]=function() {
                  var count=this.length;
                  if(name==='push'||name==='unshift') count+=arguments.length;
                  else if(name==='splice') count+=Math.max(0,arguments.length-2);
                  else for(var i=0;i<arguments.length;i++)count+=OriginalArray.isArray(arguments[i])?arguments[i].length:1;
                  if(count>4096)throw new Error('Avatar array exceeds 4096 entries');
                  return original.apply(this,arguments);
                };
              });
              OriginalArray.prototype.sort=disabled;
              var repeat = String.prototype.repeat;
              if (repeat) String.prototype.repeat = function(count) {
                if (!(count >= 0 && count <= 8192 && this.length * count <= 8192)) throw new Error('Avatar string exceeds 8192 characters');
                return repeat.call(this, count);
              };
              ['padStart','padEnd'].forEach(function(name) {var original=String.prototype[name];if(original)String.prototype[name]=function(length,pad) {
                if(!(length>=0&&length<=8192))throw new Error('Avatar string exceeds 8192 characters');return original.call(this,length,pad);
              };});
              var forbidden = ['Packages','java','javax','org','com','net','JavaAdapter','JavaImporter','Java','importClass','importPackage',
                'getClass','load','loadClass','require','readFile','readUrl','eval','Function','RegExp','Date','ArrayBuffer','DataView',
                'Int8Array','Uint8Array','Uint8ClampedArray','Int16Array','Uint16Array','Int32Array','Uint32Array','Float32Array','Float64Array'];
              forbidden.forEach(function(name) { define(global,name,{value:disabled,writable:false,configurable:false}); });
              define(global,'Array',{value:boundedArray,writable:false,configurable:false});
              function deepFreeze(value, depth) {
                if (depth > 24) throw new Error('Avatar frame is nested too deeply');
                if (value && typeof value === 'object') { keys(value).forEach(function(k) { deepFreeze(value[k],depth+1); }); freeze(value); }
                return value;
              }
              var register = function(id, callback) {
                if (typeof id !== 'string' || id.length > 128 || typeof callback !== 'function') throw new Error('register requires an ID and function');
                if (!callbacks[id] && keys(callbacks).length >= 32) throw new Error('Too many avatar controllers');
                callbacks[id] = callback;
              };
              var api=freeze({register:register});
              define(global,'RigWorks',{value:api,writable:false,configurable:false});
              define(global,'Character',{value:api,writable:false,configurable:false});
              return freeze({
                configure:function(json) { mappings=deepFreeze(parse(json),0); },
                registered:function(id) { return typeof callbacks[id] === 'function'; },
                run:function(json,id) {
                  var input=deepFreeze(parse(json),0), commands=[];
                  function emit(type,args) { if(commands.length>=128)throw new Error('Avatar exceeded 128 commands'); commands.push({type:type,args:args}); }
                  function accessor(value) {
                    var fn=function(){return value;};
                    keys(value).forEach(function(k){if(['name','length','arguments','caller','callee','prototype','constructor'].indexOf(k)<0)define(fn,k,{value:value[k],writable:false});});
                    return fn;
                  }
                  var model=freeze({
                    play:function(clip,blend){emit('clip',{clip:clip,blend:blend===undefined ? .15 : blend});},
                    state:function(name){emit('state',{name:name});},
                    phase:function(value){emit('phase',{value:value});}, releasePhase:function(){emit('releasePhase',{});},
                    playback:function(speed,loop){emit('playback',{speed:speed,loop:loop});},
                    pause:function(){emit('pause',{});}, resume:function(){emit('resume',{});}, stop:function(){emit('stop',{});},
                    layer:function(id,clip,weight,additive,mask){emit('layer',{id:id,clip:clip,weight:weight,additive:additive,mask:mask||{}});},
                    removeLayer:function(id){emit('removeLayer',{id:id});}, layerWeight:function(id,weight){emit('layerWeight',{id:id,weight:weight});},
                    layerPhase:function(id,value){emit('layerPhase',{id:id,value:value});},
                    pose:function(id,options){emit('pose',{id:id,options:options});}, removePose:function(id){emit('removePose',{id:id});}
                  });
                  var frame=freeze({
                    input:accessor(input), data:input, state:function(){return state;}, mappings:mappings,
                    model:function(){return model;},
                    profile:function(){return input.profile||freeze({});},
                    bone:function(role){var value=(mappings.bones||((input.profile||{}).bones)||{})[role];return typeof value==='string'?value:value&&value.node||null;},
                    clip:function(intent){var value=(mappings.animations||((input.profile||{}).animations)||{})[intent];return typeof value==='string'?value:value&&value.clip||null;},
                    defaultAnimation:function(){emit('defaultAnimation',{});}, defaultMotion:function(){emit('defaultMotion',{});},
                    locomotion:function(){emit('defaultMotion',{});}, play:function(clip,blend){model.play(clip,blend);},
                    layer:model.layer, morph:function(name,weight){emit('morph',{name:name,weight:weight});},
                    ik:function(name,enabled){emit('ik',{name:name,enabled:enabled});},
                    ikTarget:function(name,x,y,z){emit('ikTarget',{name:name,x:x,y:y,z:z});},
                    contact:function(id,mainHand){emit('contact',{id:id,mainHand:mainHand});},
                    contactWeight:function(weight){emit('contactWeight',{weight:weight});}, noContact:function(){emit('noContact',{});},
                    motion:function(name,loop){emit('motion',{name:name,loop:loop});}, stopMotion:function(){emit('stopMotion',{});}
                  });
                  callbacks[id](frame);
                  var serializedState=stringify(state);
                  if(serializedState.length>16384)throw new Error('Avatar state exceeds 16 KiB');
                  state=parse(serializedState);
                  return stringify({commands:commands,state:state});
                }
              });
            })(this)
            """;
}
