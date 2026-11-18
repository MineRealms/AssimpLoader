# Engine Composition And RigWorks Validation

## Delivered

| Area | Implemented contract |
| --- | --- |
| Evaluation | Cached stable category/topological plan, explicit `after`, cycle/missing/late-stage checks, atomic modifier replacement, exclusive physics writers |
| Clip composition | Processor clips share skeletal and Morph clock, phase, mask, weight, looping and additive policy |
| Asset inventory | Cached immutable `capabilities()` reports imported meshes, bones, clips, Morph and material facts without inventing FBX authoring semantics |
| Native player | Separate attack/item sampling switches, root and anatomical region weights, cached JSON definitions and validated explicit reload |
| Contacts | Item-space support rules, physical-side/state selectors, pole and bend limits, optional orientation/hand offset, tick-scoped script overrides |
| Script reload | Pinned KubeJS standard client entry stages registrations; errors retain published callbacks, revision and session; successful empty loads clear them |
| Rhino interoperability | Public immutable collection facade avoids Java 17 private JDK collection reflection failures |

Native motion, imported clips, script pose layers, IK and physics remain Java
evaluated. KubeJS submits policy once per tick. No per-bone JavaScript callbacks
or new server gameplay handlers were added. Item support is independently
configurable and starts disabled, preserving the native baseline.

## Build Gate

Combined build and script acceptance command:

```powershell
.\gradlew.bat build compileIntegrationTestJava :rigworks:compileIntegrationJava runClient -PwithKubeJS -PclientKubeJsSmoke --console=plain
```

Passed. JUnit discovered 453 tests: 373 engine and 80 RigWorks; zero failures,
zero errors, one optional `RuntimePerformanceProbeTest` skip. All three isolated
release artifacts were built and passed `verifyModuleBoundaries`. Archive
inspection also confirmed that the integration probes were absent and the
RigWorks manifest registered both its ordinary and optional KubeJS mixin configs.
The final release build also passed separately after aligning the contacts
configuration fallback with its disabled default; see `build/engine-next-release.log`.

| Artifact | SHA-256 |
| --- | --- |
| `build/libs/assimploader-1.0.0-all.jar` | `6310CA0509183347DA542718C5EC5DCD3110AE9A6803C125CAD474CB5A6DC07E` |
| `build/libs/assimpshowcase-1.0.0.jar` | `94FFD916E7B1E3633BD6347CBB568A361792A18F5AA28E0D70E0CE75F0ACD688` |
| `rigworks/build/libs/rigworks-1.0.0.jar` | `0BC444C7E942E47129876C7AC5DFFB61BE383C325DA1635D4752B9272A77B9B6` |

Hashes identify this local build, not a reproducible-build guarantee.

## Actual Client Evidence

The combined development client loaded AssimpLoader, RigWorks, AssimpShowcase,
Oculus and KubeJS. These are numerical runtime checks, not performance estimates.

### Native Geometry And Contacts

```powershell
.\gradlew.bat runClient -PwithKubeJS -PclientLookDevSmoke=Character_BlockPlayer_Acceptance -PclientVanillaPoseOnly --console=plain
```

Passed run `1794901907002`, using the imported FBX and original PlayerRenderer /
PlayerModel. The probe uses an unregistered player and leaves the real player
state unchanged. Resource/stage settling precedes capture by at least 10 seconds.

| Check | Result |
| --- | --- |
| Standard/slim swimming, 54 samples | Maximum independent native endpoint error `5.609748e-7 m` |
| Native bow/crossbow, 192 samples | Maximum independent native endpoint error `5.0456714e-7 m` |
| Configured support contacts, 864 samples | Both skins, both main arms, both inventory hands, bow/charging/charged states, three pitches, three yaws, four use times |
| Reachable target error | Maximum `4.8516097e-7 m` |
| Nearest bend-limited target error | Maximum `4.8514664e-7 m` |
| Primary hand displacement | `0 m`; full primary matrices also checked |
| Unreachable fixed targets | 176 samples, explicitly clamped; largest residual to the requested target `0.35757563 m` |

The oracle independently computes reach limits from actual segment lengths and
the cosine law. Every support endpoint must meet that legal point, diagnostics
must agree, and segment lengths must remain constant. An unreachable target is
not counted as exact contact with the requested point. These optional fixture
rules demonstrate wrist constraints; they are not calibrated weapon-mesh or
palm/finger contact claims.

Local evidence: `build/engine-next-final.log`, `run/lookdev-smoke-result.json`
and `run/hand-contact-probe.json`. Earlier failed contact runs exposed unreachable
fixed targets; the final oracle verifies their expected constrained result rather
than stretching bones or changing the primary item frame.

### Real KubeJS And GPU Bridge

`build/engine-next-kubejs-standard-entry.log` and
`run/kubejs-smoke-result.json` report success for:

- Imported capabilities, including explicit unknown authoring provenance.
- Immutable schedules and rejection of missing dependencies, late stages and
  cycles, while preserving installed modifier identity after rejection.
- Processor Morph phase, mask, weight, common clock and disabled baseline.
- Five uniform parameter types, disabled values, type mismatch handling and
  resource retirement through the real GPU parameter bridge.
- `KubeJSClient.reloadClientScripts()` itself, including the transformed redirect:
  two-file syntax failure, muted error, preload error, replaced Rhino scope,
  three retained closure calls and successful empty-registry commit.

The reload probe temporarily substitutes an in-memory manager and restores it.
It runs only in the explicit disposable acceptance client because KubeJS itself
clears global CLIENT handlers. RigWorks preserves its own registry and callbacks;
it cannot roll back other mods or arbitrary script side effects. Other accepted
KubeJS versions and unwrapped manager loads retain legacy clear-on-bind behavior.

Parallel review found and fixed two additional cases: zero JSON contact weight
must preserve exact incoming matrices, including shear; unwrapped KubeJS loads
must clear deleted controllers. Both have regression coverage.

## Remaining Boundaries

- Contact accuracy between interpolated ticks and after dynamic physics feedback
  is not established. Body intersections and arbitrary item mesh contact are not
  solved by a wrist target. The global contact switch is independent of physics.
- The evaluation plan orders existing modifier categories. It does not implement
  arbitrary per-TRS graph ownership, processor-owned rigid-body graphs, or
  same-frame feedback loops.
- Capability inventory cannot recover original Blender constraints, drivers or
  physics authoring graphs from a baked FBX. A versioned sidecar exporter and
  complete LookDev provenance/override inspection remain follow-up work.
- General render-graph passes, depth/history resource scheduling and arbitrary
  shader source insertion are not supplied by the uniform bridge.
- This pass did not validate an engine-only client or a standalone dedicated
  server, live input pixels, first-person replacement, remote-player replacement,
  or arbitrary resource-pack geometry. PMX/VMD remain deferred.

## Usage

- Contacts: LookDev Player **Contacts**, `character.handContacts`, or
  `/assimploader character contacts on|off`.
- [Contact schema and script API](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/RIGWORKS_HAND_CONTACTS.md).
- [Native channel composition](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/RIGWORKS_NATIVE_CHANNELS.md).
- [Modifier dependencies](../reference/POSE_EVALUATION_PLAN.md).
- [Imported capabilities and processor Morph](../reference/MODEL_CAPABILITIES_AND_CLIP_MORPHS.md).
- [KubeJS reload behavior](../reference/KUBEJS_RELOAD_LIFECYCLE.md).
