# Shader Parameter Bridge

AssimpLoader owns the optional Oculus program-binding hook. Content mods own
their effect semantics. Showcase's rocket uniform provider uses this hook;
the engine does not reference rocket classes.

## JSON And Client Scripts

The complete example is in `examples/kubejs`. Place its `assets` and
`client_scripts` trees under the instance's `kubejs` directory. The demo only
changes programs that declare and consume its three uniform names. It produces
no visible effect in a shader pack that does not implement them.

```js
Assimp.shaders().define('demo:tint', 'assimploader:shader_parameters/demo.json')
Assimp.shaders().uniform('demo:tint', 'assimpDemoTint', [0.2, 0.8, 1.0])
Assimp.shaders().enabled('demo:tint', true)
```

Definitions are parsed when `define` is called. Repeating it every tick resets
the pass and wastes parsing work. Call it once after resources are available,
and again intentionally when replacing a JSON definition. After editing an asset,
reload resources with F3+T, then reload client scripts. A script reload is not a
resource reload. Definitions are replaced only after validation succeeds.

`version` is the integer `1`. Supported types are `int`, `float`, `vec2`, `vec3`
and `vec4`. Every uniform has a `default`; examples also specify the value to
upload while disabled using `disabled`. Without it, the disabled value is zero.
Optional `min` and `max` accept a scalar or an array matching the type.
Values must be finite and within an absolute bound of 1,000,000. Active values
must satisfy the declared range. A disabled sentinel may be outside that range.
Declare the same type as the GLSL uniform. Unknown uniforms in a program are skipped.
A mismatched active GL type is skipped and logged once per program/type, instead
of submitting an invalid upload. Supported programs require OpenGL 3.1 or later.

There are at most 32 groups with 64 uniforms each. A uniform name has one active
owner across all groups, because several groups may target the same program.
Replacing or clearing a group retains disabled values for its retired names so
that previously written GL values do not remain active. This history is bounded
at 2,048 active plus retired names; excessive renaming is rejected. `clear()` clears all script groups,
so ordinary scripts should disable their own group instead.

## Evaluation And Lifetime

Commands run on the client thread. Java uploads numeric snapshots when an Oculus
program is bound; no JavaScript executes inside the draw or vertex loop. Uniform
locations belong to the actual program object, avoiding recycled GL ID caches.
Retired disabled values upload first, native providers second, and active script
groups last. Removing a script therefore allows an existing native provider to
restore its own defaults; an actively disabled script group still overrides it.
Rebuilding KubeJS client bindings retires old script declarations on the client
thread, including declarations from deleted scripts. Resource/world cleanup
restores declarative defaults. Explicitly disable a group
on logout if its JSON defaults enable it.

The hook currently targets the supported Oculus 1.8.0 program implementation.
This API does not compile GLSL, inject source, bind arbitrary textures, activate
internal shader-pack passes, or implement a render graph. A pack must already
contain the corresponding uniform-driven effect. Native providers can register
with `ShaderProgramHooks.register` and cache their state using `Binding.state`.
