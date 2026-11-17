# Active Work - 2026-11-17

Status: in progress. This document tracks the current user requests, not a claim
that all older expansion proposals have already been delivered.

## Player Animation Compatibility

- [x] Identify missing vanilla setup after replacing RenderPlayerEvent rendering.
- [x] Correct the fixture's visible head forward axis (+Z).
- [x] Implement legacy clip travel alignment for swimming and Elytra.
- [x] Integrate original PlayerRenderer arm-pose selection and setupRotations.
- [x] Retarget original PlayerModel preparation/setup to the hierarchical FBX rig.
- [ ] Preserve head pitch/yaw, walk/run, crouch, swim strokes/kicks, riding,
      sleep, flight, death, item use and accepted attack timing.
- [x] Correct FBX neutral/reference pose: export outside NLA ranges and recover skin bind transforms.
- [x] Verify model-space script rotations against actual imported limb positions.
- [ ] Verify five sword moves, combo reset, mirroring and rapid accepted swings.
- [x] Wire VANILLA / SCRIPTED / CLIPS configuration and client command choices.
- [x] Wire independent RigPhysics configuration and client commands.
- [ ] Verify mode changes, script failure fallback and physics on/off at runtime.
- [ ] Update KubeJS API/template instructions and Mo' Bends integration report.

## Modern Flat GUI

- [x] Inventory active GUI entry points and reuse one consistent visual system.
- [x] Restyle character selection, motion modes and physics controls.
- [x] Restyle loaded-model browser and preview controls.
- [ ] Provide debug controls through the GUI.
- [ ] Verify narrow layouts, text clipping, hover/focus and navigation.

## Client Debug Tools

- [ ] Add client-only debug commands and persistent client configuration.
- [ ] Draw model bounds, skeleton/joints and available collision/physics shapes.
- [ ] Add material views: default, untextured, PBR off, normal, AO,
      roughness, metallic and other supported diagnostic channels.
- [ ] Expose applicable shader/render-path controls with honest support limits.
- [ ] Add a performance HUD with its own switch, independent of F3.
- [ ] Report real frame/render/animation/physics/batch counters where available;
      show unavailable metrics explicitly rather than inventing timings.
- [ ] Keep disabled debug tools cheap and restore GPU state after rendering.

## Verification And Delivery

- [x] Configure subsequent development clients with Oculus by default (`-PwithoutOculus` opts out).
- [ ] Run focused actual-FBX and original-pose regressions, then build/test gates.
- [ ] Validate the new runtime paths with Oculus and KubeJS.
- [ ] Wait at least ten seconds after asset/stage changes before screenshots.
- [ ] Commit coherent changes incrementally and push verified commits.
- [ ] Leave an Oculus-enabled manual client available for user inspection.

## Scope Boundaries

All changes remain client visual behavior. Server movement, damage and collision
authority are unchanged. PMX/VMD parsing is still deferred. Local third-person
character replacement does not imply first-person or remote-player replacement.
Referenced Mo' Bends code remains local analysis material; production templates
are independent, editable equivalents, not redistributed decompiled classes.
