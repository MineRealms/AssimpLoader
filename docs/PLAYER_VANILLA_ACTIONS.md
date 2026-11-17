# Vanilla Player Action Compatibility

RigWorks uses the actual player state and the original PlayerModel pose sampler.
Minecraft retains movement, collision, item use and damage authority. These
policies only change the rendered character.

## Shared Observations

`MinecraftPlayerInputSampler` samples an `AbstractClientPlayer` once per game
tick. Local and remote players use observed motion; jump-key state is not used.
`PlayerVisualInput.actionState()` adds pose, climbing, water/lava, Riptide,
freezing, hurt ticks, use animation and physical left/right held-item facts.

Air phases are `grounded`, `takeoff`, `rising`, `apex`, `falling` and `landing`.
A real contact-to-air transition produces takeoff. A ledge fall does not.
Landing has a three-tick observation window, but a new takeoff interrupts it.
The articulated native leg layer has its own contact hysteresis to avoid a
one-frame straight-leg flash during repeated moving jumps.

KubeJS `frame.input()` exposes `pose`, `airPhase`, `takingOff`, `landing`,
`crawling`, `riptide`, `frozen` and `climbDirection` (`up`, `down`, `idle`,
`none`) in addition to the existing input fields. Crawling means swimming
pose without actual swimming, so dry ground crawling is distinct from water.

## Clip Compatibility Policy

Production CLIPS integration calls `controller.updateCompatible(input)` and
passes its returned `VanillaPoseSelection` to the binding's native driver.
The legacy `update(input)` remains a single-primary-clip API for integrations
which explicitly own their own complete action composition.

The compatible path keeps a locomotion clip running while use/swing/hurt clips
play in `character:upper-action`. The mask contains mapped torso/arm subtrees,
and explicitly excludes the hips and leg subtrees. Its player advances through
the existing animation update; render passes never advance the action clock.
Accepted swings synchronize to Minecraft's swing generation and phase.

Missing run/directional clips may use walk. Missing special poses request the
native skeleton instead of displaying an upright walk or idle. The native
sampler also preserves physical left/right held-item poses when no authored
upper action owns them. An explicit profile animation mapping overrides the
default name resolution.

Additional profile animation keys are `crawl`, `crawl_idle`, `ladder_up`,
`ladder_down`, `ladder_idle`, `water_idle`, `lava`, `riptide`, `frozen`, `hurt`
and `land`. `hurt` is an optional upper-body clip. `land` is selected only when
provided, so ordinary movement continues when no landing asset exists.

## Native Details

The sampler calls original arm-pose selection, model preparation, model setup
and entity rotations. Articulated rigs add bounded elbows/knees for climbing,
water idle kicks, eating/drinking arm motion and ordinary airborne knees. They
do not change bone lengths or gameplay physics.

`VanillaPlayerPoseDriver.renderOffset(player)` reproduces the original bed
offset omitted by a canceled player renderer. Apply it in world coordinates,
before the custom model yaw and scale. The native pose still supplies bed
direction, swim/flight pitch, death rotation, Riptide and freezing rotations.

Arbitrary hand contact, finger grip, seat contact and foot grounding still
depend on calibrated model mappings and solver definitions. A native pose
fallback cannot manufacture a model-specific authored animation.

## Verification Boundary

Focused tests cover real-contact phase transitions, consecutive hops, stale
observations, dry crawling, special-state priorities, native fallback and
upper-body clip masks while preserving the locomotion clock. Lusia's policy
test includes crawl, frozen and Riptide in addition to moving hop continuity.
Full visual compatibility also requires the equipment, first-person, remote
player and rendering integration acceptance; these tests alone do not prove it.
