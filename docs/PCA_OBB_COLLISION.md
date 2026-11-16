# PCA OBB Collision

`PcaObb.fit(float[])` fits one conservative oriented enclosure to XYZ positions.
Use it for coarse picking or broad phase queries, not exact mesh intersection.
PCA chooses the axes; it does not find the minimum-volume oriented box and it is
not a triangle collision engine. All positions are projected when computing the
extents, so a point excluded from covariance sampling is still enclosed.

## Work Limits

The default limits are one million vertices per uncached fitting batch and 4096
uniformly spaced covariance samples per mesh. `PcaObb.Limits` accepts 1-10 million
vertices and 3-65536 covariance samples. Small meshes use every vertex. The fitter
uses double Welford covariance, at most 32 Jacobi rotations, constant scratch
storage, then a linear pass over every vertex. No native dependencies or per-vertex
objects are allocated by fitting. Increasing the covariance sample limit changes
the axis estimate, not the enclosure guarantee.

Empty or malformed XYZ arrays, non-finite coordinates, invalid limits and exceeded
budgets throw `IllegalArgumentException`. Point, line, plane and repeated-point
inputs are supported. Underconstrained line axes are selected deterministically.
Extents include a small scale-dependent float rounding margin; at very large
coordinates the margin is necessarily larger. Unrepresentable float extents are
rejected. Returned matrices and extents are defensive copies.

## Pose Integration

```java
var query = new ModelCollision(proxies, new PcaObb.Limits(100_000, 2048));
var hit = query.raycast(snapshot, modelSpaceRay, maximumDistance,
        ModelCollision.Mode.OBB);
```

Existing `BOUNDS`, `BONE` and `MESH` modes retain their behavior. An OBB query first
checks the published scene bounds, then the aggregate vertex count of the unique
uncached meshes. If that batch exceeds the configured budget it is rejected before
CPU skinning or fitting; there is no silent fallback or partial result. Cached
meshes do not consume a subsequent fitting budget.

Static mesh enclosures are cached by mesh identity. Rigid node movement reuses the
mesh-local enclosure and applies the snapshot node transform. Skinned meshes and
active Morph meshes are fitted to current CPU-deformed positions only when queried,
cached per immutable snapshot, and use weak snapshot keys. Queries against a new
animated pose therefore cost a new skinning/fitting pass. `clear()` drops both
static and pose caches. No render or animation tick automatically fits an OBB.
Ownership and query calls follow the existing single-client-thread collision
contract; caches are not concurrent.

Rays and distances are in model space. Transform world-space rays at the caller's
authoritative tick. OBB hits report triangle `-1`, like other analytic colliders.
They do not install server damage handling or continuous collision detection.

## Verification

Focused tests cover rotated-box extents, enclosure, normals and distance limits;
point/line/plane degeneracy; deterministic repeat fitting; a covariance-unsampled
outlier; large translations; invalid inputs; cache lifetime; actual deer skinning
per pose; and rejection of an oversized uncached batch. These are numerical and
API checks, not a world projectile or throughput benchmark.

Verified 2026-11-16 with `test --tests '*PcaObbTest'`: seven tests, zero failures
or errors, `BUILD SUCCESSFUL`. Log: `build/pca-obb-tests.log`. The collision package
suite also completed successfully before the final multi-mesh batch test was added.
