# Active Work - 2026-11-17

## Next Engine Pass

- [x] Add JSON item support sockets and constrained two-bone contact solving.
- [x] Expose tick-scoped contact policy through RigWorks client scripts.
- [x] Add native motion channel selection and preserve good controllers on failed reload.
- [x] Compose processor clip Morph and report observed asset capabilities.
- [x] Compile modifier dependencies and validate conflicting physics writers.
- [x] Verify actual FBX contact geometry, Rhino controls and combined Mod runtime.
- [ ] Document limits and commit/push verified changes.

Build and combined Oculus/KubeJS acceptance passed: 453 tests, zero failures or
errors, one optional skip. The 864-pose contact probe verified reachable wrist
targets and independent bend-limited clamping; 176 targets were unreachable and
were reported as clamped, not as successful mesh contact. See
`ENGINE_NEXT_VALIDATION_2026-11-17.md` for current evidence and remaining work.

## Follow-up: Module Split and Data-driven Runtime

This section supersedes the old completion statement below for new requests.

- [x] Correct the dates of the earliest 51 commits; preserve all later timestamps and commit contents.
- [x] Split player integration into the independent `:rigworks` Gradle project and Mod.
- [x] Split Showcase into a separate Mod/source set and JAR.
- [x] Move gameplay registries, networking, commands and world writes out of the engine.
- [x] Extract animation clock, model visibility, item contract and optional UI/diagnostic providers.
- [x] Build three isolated artifacts and compile the combined development harness.
- [x] Verify the three Mods load together with Oculus and KubeJS.
- [x] Finish and test the generic JSON/KubeJS shader parameter bridge.
- [x] Correct physical limb sides, standard/slim native retargeting, default bow/crossbow delegation and fishing endpoint math.
- [x] Deliver reusable JSON/KubeJS processor examples and clarify remaining graph work.

The combined startup, initial bake, native geometry probe and real Rhino API
acceptance passed. `build compileIntegrationTestJava :rigworks:compileIntegrationJava`
passed: 402 tests, zero failures/errors, one skipped. The stronger probe found and
then verified the fix for standard/slim shoulder origins. See
`MODULE_SPLIT_VALIDATION_2026-11-17.md` for evidence and remaining boundaries.

The next engine pass above adds optional item support constraints, a modifier
dependency compiler and imported capability inventory. Exclusive physics writers
are checked at whole-bone granularity; arbitrary per-channel graph ownership and
processor-owned rigid-body graphs remain future work. Native arm geometry and
configured wrist targets do not prove arbitrary custom item mesh contact. The old
handoff below is historical, not current process state.

The design and the distinction between current APIs and future work are recorded in
`MODULE_SPLIT_AND_DATA_DRIVEN_PLAN_2026-11-17.md`.

Status: completed for the scope below. This document tracks the current user requests, not a claim
that all older expansion proposals have already been delivered.

## Player Animation Compatibility

- [x] Identify missing vanilla setup after replacing RenderPlayerEvent rendering.
- [x] Correct the fixture's visible head forward axis (+Z).
- [x] Implement legacy clip travel alignment for swimming and Elytra.
- [x] Integrate original PlayerRenderer arm-pose selection and setupRotations.
- [x] Retarget original PlayerModel preparation/setup to the hierarchical FBX rig.
- [x] Preserve head pitch/yaw, walk/run, crouch, swim strokes/kicks, riding,
      sleep, flight, death, item use and accepted attack timing.
- [x] Correct FBX neutral/reference pose: export outside NLA ranges and recover skin bind transforms.
- [x] Verify model-space script rotations against actual imported limb positions.
- [x] Verify five sword moves, combo reset, mirroring and rapid accepted swings.
- [x] Wire VANILLA / SCRIPTED / CLIPS configuration and client command choices.
- [x] Wire independent RigPhysics configuration and client commands.
- [x] Verify mode changes, script failure fallback and physics on/off at runtime.
- [x] Update KubeJS API/template instructions and Mo' Bends integration report.

## Modern Flat GUI

- [x] Inventory active GUI entry points and reuse one consistent visual system.
- [x] Restyle character selection, motion modes and physics controls.
- [x] Restyle loaded-model browser and preview controls.
- [x] Provide debug controls through the GUI.
- [x] Consolidate all active GUIs into LookDev Models / Nodes / Player / Debug navigation.
- [x] Remove ModernUI runtime dependency; active components use independent FlatUi code.
      Dormant historical node-editor sources retain a compile/test-only Core dependency.
- [x] Verify narrow layouts, text clipping and navigation at 640x360 and 427x240 GUI sizes.
      Screenshots and widget bounds/overlap checks passed. Hover/focus follows FlatUi.

## Client Debug Tools

- [x] Add client-only debug commands and persistent client configuration.
- [x] Draw model bounds, skeleton/joints and available collision/physics shapes.
- [x] Add material views: default, untextured, PBR off, normal, AO,
      roughness, metallic and other supported diagnostic channels.
- [x] Expose applicable shader/render-path controls with honest support limits.
- [x] Add a performance HUD with its own switch, independent of F3.
- [x] Report real frame/render/animation/physics/batch counters where available;
      show unavailable metrics explicitly rather than inventing timings.
- [x] Guard disabled/shadow paths, bound debug traversal/history, and preserve preview GPU state.
      The active Oculus shader-pack acceptance completed with no GL errors.

## Verification And Delivery

- [x] Configure subsequent development clients with Oculus by default (`-PwithoutOculus` opts out).
- [x] Run focused actual-FBX and original-pose regressions, then build/test gates.
- [x] Validate the new runtime paths with Oculus and KubeJS.
- [x] Wait at least ten seconds after asset/stage changes before screenshots.
- [x] Commit coherent changes incrementally and push verified commits.
- [x] Leave an Oculus-enabled manual client available for user inspection.
      Detached manual launch on 2026-11-17; Oculus/Embeddium initialized and the
      installed action template loaded with zero KubeJS errors. Enter a world and
      press L; Player defaults to Vanilla, with Scripted selectable for combos.

## Scope Boundaries

All changes remain client visual behavior. Server movement, damage and collision
authority are unchanged. PMX/VMD parsing is still deferred. Local third-person
character replacement does not imply first-person or remote-player replacement.
Referenced Mo' Bends code remains local analysis material; production templates
are independent, editable equivalents, not redistributed decompiled classes.

## Evidence

- LookDev world run `1794882904162`: 22 stages, active RocketFlow_Local shader
  pack, all pages at 640x360 and 427x240, physics cleanup, script failure fallback,
  NORMAL/AO diagnostics, F3-off HUD, no GL/bounds/overlap failures.
- Native player pose run `1794883594421`: real renderer/model/Mixin, imported FBX,
  head pitch +/-60, crouch, walk, swim phases, Elytra pitch, riding/sleeping,
  held/item use poses and attack phases. Real player state remained unchanged.
- Real Rhino acceptance: all five sword moves displaced the actual FBX hand.
- Offline action policy: combo/reset/gates, mirroring, rapid retrigger, item
  classifications, action extensions and cleanup passed.
- Final `gradlew test build`: successful; 378 tests discovered, zero failures or
  errors, one pre-existing optional skip. 377 tests executed successfully.
- See [LookDev guide](LOOKDEV_DEBUG.md) and [player integration report](PLAYER_ACTION_INTEGRATION_2026-11-17.md).
