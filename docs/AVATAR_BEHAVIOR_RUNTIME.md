# Avatar Behavior Runtime

RigWorks avatar behavior is independent of KubeJS's global `client_scripts`
registry. A local author package lives in `kubejs/rigworks/<avatar>/`:

```text
manifest.json
mappings.json
controller.js
```

The manifest selects an existing profile resource and a namespaced controller:

```json
{
  "version": 1,
  "id": "example:avatar",
  "profile": "example:models/avatar/profile.json",
  "controller": "example:avatar",
  "scripts": ["controller.js"],
  "mappings": "mappings.json"
}
```

Files are JSON/JS sources only. The bundle does not contain FBX, texture, sound or
native-library assets. Those resources must already exist on each client.
`AvatarPackageRepository.forGame(gameDirectory)` scans the dedicated directory;
`refresh()` scans all authors' packages, and `reload(id)` replaces only that
avatar. Invalid reloads report an error and retain the last published bundle.
Downloaded-package cache folders are excluded from author discovery.

## Script API

```javascript
RigWorks.register('example:avatar', frame => {
  const input = frame.input();
  const state = frame.state();
  state.ticks = (state.ticks || 0) + 1;
  frame.defaultAnimation();
});
```

`Character.register` is a registration alias. `frame.input()` is an immutable
plain JavaScript snapshot. `frame.data` and `frame.input.speed` are convenient
read aliases. `frame.state()` is JSON state belonging to this owner and bundle;
retain JSON values there instead of a frame, player or model object. State is
normalized through JSON after each call. Each local/remote owner has a separate
interpreter, globals, callbacks and state; a session reset does not touch others.

The snapshot includes motion, stance, air phase, item/use/swing state, physical
hand side, profile bone/clip mapping, world position and tick/time. It exposes no
player entity, Minecraft instance, Assimp model handle or Java collection.

Available commands:

- `defaultAnimation()`, `defaultMotion()` / `locomotion()`.
- `play(clip, blendSeconds)` and `model().play/state/phase/releasePhase/playback`.
- `model().pause/resume/stop`.
- `layer(id, clip, weight, additive, mask)` and
  `model().removeLayer/layerWeight/layerPhase`.
- `model().pose(id, options)` / `removePose(id)` using the existing bounded pose
  schema (`bones`, optional `space`, `stage`, `additive`, `weight`).
- `morph(name, weight)`, `ik(name, enabled)`, `ikTarget(name, x, y, z)`.
- `contact(id, mainHand)`, `contactWeight(weight)`, `noContact()`.
- `motion(name, loop)`, `stopMotion()`.
- Read-only `bone(role)`, `clip(intent)`, `profile()` and `mappings`.

Commands are data, validated in Java before engine mutation. Arbitrary method
calls are not forwarded. Missing clips, bones or solver definitions cannot be
invented by a script. The consumer must return to its native controller after an
interpreter or application failure.

## Runtime Boundary

The implementation uses the actual optional Rhino Forge `2001.2.2-build.17`
API. Its `Context.enter()` creates a context directly; this fork has no upstream
`ContextFactory`. A private `Context` subclass therefore implements
`observeInstructionCount`, with `setInstructionObserverThreshold(128)` and
`setMaximumInterpreterStackDepth(128)`. Compilation uses Rhino's interpreter.

`initSafeStandardObjects`, an all-denying two-argument `ClassShutter`, a rejecting
`WrapFactory` and a disabled `createClassLoader` keep Java reflection, Java
objects and generated JVM classes out of the script scope. Pure JS API functions
are installed without binding Java objects or exposing a host callback.
Global Java/import/file/network APIs, `eval` and the global `Function` constructor
are unavailable. Regular expressions are disabled because their native backtracking
does not provide the interpreter instruction callback. Native constructors and
prototypes are sealed using Rhino's Java API; this avoids bugs in this pinned
version's JavaScript `Object.freeze` on built-in descriptors. Input and command
objects use ordinary JSON data and frozen script objects.

Limits include 32 source files, 64 KiB per source, 512 KiB source bundle, 64 KiB
frame input, 128 commands per tick, 32 KiB combined output, 16 KiB JSON state,
100,000 interpreter instructions and a 50 ms owner-thread CPU watchdog. The
instruction limit is the primary per-call work bound; 50 ms tolerates Windows
CPU accounting that can advance in 15.625/31.25 ms steps. This watchdog is not a
normal rendering-frame target. Thread
scheduling delays and JVM pauses do not consume that CPU allowance. A separate
5-second wall-clock watchdog remains active; when thread CPU telemetry is
unavailable, a conservative 250 ms wall-clock fallback applies instead. Startup
has a separate bounded initialization allowance. Arrays and repeated/padded
strings have bounded constructors; where supported, an owner-thread allocation
counter enforces a 32 MiB allocation allowance at interpreter checkpoints. Other
threads' allocations do not count against an avatar. An instruction/time failure
retires that session and returns an
empty unsuccessful result. A hostile script cannot catch the Java budget signal
as an ordinary JavaScript exception and retry forever.

This is a restricted in-process interpreter, not an OS process sandbox or a hard
JVM memory quota. A single native operation is checked after it returns; the
deadline is not a hard real-time GPU/frame budget. Missing Rhino yields a native
fallback session instead of loading an optional-dependency class eagerly.

## Consumer Integration

```java
AvatarScriptBundle bundle = AvatarScriptBundle.fromPackage(sharedPackage);
AvatarScriptRuntime.Session script = AvatarScriptRuntime.open(bundle);
AvatarFrameInput observations = new AvatarFrameInput();
AvatarCommandApplier commands = new AvatarCommandApplier(
    animation, solverRig, contacts, nativeAnimationCallback, nativeMotionCallback);

var result = script.execute(observations.capture(player, profile, input));
if (!commands.apply(result)) {
    // The owner invokes its normal built-in compatible/native policy.
}
```

Create, execute and dispose each session on its owner's client tick thread.
Apply commands inside the existing tick, before the one normal pose evaluation;
never tick scripts separately from GUI, shadow or main-world render calls.
`AvatarCommandApplier` preserves existing animation-layer players when the clip,
mask and mode stay equal, so repeated script declarations do not reset playback.
`reset/close` removes its own layers/poses and restores solver settings.

`AvatarScriptBundle.toPackage(visual)` bridges into the shared configuration
package. Its source hash is deterministic across file-map insertion order;
`CharacterVisualPackage` remains the authority for the complete transfer hash.
The repository never writes downloaded JS into the author source directory.

Unit tests cover real pinned-Rhino execution, Java bridge rejection, immutable
input, per-owner state/reset, startup/runtime loops, invalid command output,
resource restrictions, source discovery, transactional reload and bundle hashes.
