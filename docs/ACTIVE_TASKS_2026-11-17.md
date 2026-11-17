# Active Work - 2026-11-17

Status: in progress. This document tracks the current user requests, not a claim
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
- [ ] Commit coherent changes incrementally and push verified commits.
- [ ] Leave an Oculus-enabled manual client available for user inspection.

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
