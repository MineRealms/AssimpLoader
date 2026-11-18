# Spring Bone And Collision Runtime

`SpringBoneModifier` runs after the instance's clip and layer composition through
`PoseModifier`. It reads imported node transforms and writes instance local overrides;
the shared imported scene remains unchanged. Construct one modifier per instance.

## Configuration And Timing

`Settings(node, tail, stiffness, damping, gravity, radius, fixedStep, maxSubsteps,
teleportDistance)` uses model-space units and seconds. `tail` is a local-space point
relative to the driven node. Gravity is in model space. Stiffness attracts the tail
to its currently animated rest position; damping is an exponential velocity decay.
The simulated tail retains its animated bone length. A parent rotation or arbitrary
bone axis is handled by `PoseGraph.rotateModel`.

Updates use a bounded fixed step. At most `maxSubsteps` are integrated per call;
excess hitch time is discarded to prevent unbounded catch-up. A zero-time publication
reapplies the existing tail direction without integrating. `reset()` clears velocity
and accumulated time. Moving the bone origin by more than `teleportDistance` resets
the simulation on the next application. Call `AnimationState.resetDynamics()` after
a discontinuity that does not move the model-space bone origin, such as teleporting
the entire model's world transform.

Sphere and capsule contacts accept an optional node name; null selects model space,
otherwise points follow that node's current composed pose. Scaled or sheared
ancestry uses a conservative radius bound. Contacts use bounded alternating
projections, with bone length taking precedence in an infeasible overlap. This is
secondary animation, not a general rigid-body or ragdoll solver.

Version 1 instance JSON accepts `springs` and `colliders` objects:

```json
{
  "version": 1,
  "model": "assimploader:models/validation/animation_probe.fbx",
  "colliders": {
    "body": {"type": "sphere", "node": "Shoulder", "center": [0, 0, 0], "radius": 0.1}
  },
  "springs": {
    "elbowMotion": {
      "node": "Elbow", "tail": [0, 0.25, 0],
      "stiffness": 30, "damping": 5, "gravity": [0, -9.81, 0],
      "radius": 0.02, "fixedStep": 0.008333333, "maxSubsteps": 8,
      "teleportDistance": 4
    }
  }
}
```

The example selects a node-local tail for demonstration; an asset should use the
actual exported bone axis and length. All declared colliders are supplied to each
spring in that definition. Stiffness is constrained by `stiffness * fixedStep^2 <= 1`
for the explicit integrator. Fixed steps below one microsecond are rejected.

## Collision Queries

`Ray(origin, direction)` copies and normalizes its direction. All `raycast(ray,
maximumDistance)` calls return `Optional<RayHit>`, with distances in model units.
Maximum distance is inclusive and must be finite and nonnegative. Closed shapes
return the exit surface when the ray starts inside. Hit vectors are defensive copies.

- `AabbCollider(minimum, maximum)` uses parallel-safe slab intersection.
- `CapsuleCollider(start, end, radius)` includes cylindrical side and spherical caps;
  coincident endpoints form a sphere. `project` supports spring particle contacts.
- `ObbCollider(minimum, maximum, transform)` supports an invertible affine transform,
  including nonuniform scale; it transforms normals with the inverse transpose.
- `TriangleMeshBvh(vertices, indices)` or `(AssimpMesh)` copies static indexed
  triangles, builds a median-split BVH, and returns the nearest two-sided triangle
  hit with its original triangle index. Degenerate triangles are skipped.
- `BoneCollisionProxy(node, start, end, radius).at(snapshot)` constructs a conservative
  capsule from an immutable pose snapshot. It follows the same published pose as rendering.

The mesh BVH is explicitly static. Skinning or Morph changes require a rebuilt
structure over deformed vertices, or a bone proxy; querying the static BVH does not
claim precise animated surface intersection. World-space callers must transform
their ray and range into the model coordinate system, and transform the hit back.

`ModelInstance.collision()` supplies the integrated `ModelCollision` query API.
`BOUNDS` tests the snapshot AABB; `BONE` tests configured bone capsules without
discarding proxies outside the visual AABB; `MESH` broad-phases against the snapshot
and builds a BVH over that pose's CPU-deformed vertices. Static geometry is shared,
and repeated queries reuse a snapshot's structure. Changing poses can incur CPU
deformation and BVH rebuild costs. These explicit instantaneous queries do not
replace Minecraft entity physics, server authority or continuous collision detection.

## Verification

Focused tests cover frame partition invariance, bone length, rotated parents,
bounded hitches, teleport/reset, zero-time republication, particle contacts, snapshot
proxies, slab parallelism, inside rays, tangent/edge boundaries, scaled OBB distances
and normals, nearest BVH hits across branches, defensive copies and invalid input.

The tests above cover math and imported scenes. The controlled client preview
also exercises a Spring-modified Blender FBX with CPU/GPU silhouette comparison;
world collision and character locomotion are separate integration checks. See the
[implementation report](../history/EXPANSION_IMPLEMENTATION_2026-11-16.md) for run IDs and scope.
