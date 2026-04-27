# IK System Integration Plan

## Overview
Integrate Inverse Kinematics (IK) solving capabilities into the existing skeletal animation system using Caliko library, providing high-performance runtime bone manipulation for procedural animations.

## Goals
- Add IK solving without breaking existing keyframe animation system
- Provide high-performance IK chains for common use cases
- Enable hybrid animations (keyframe + IK)
- Maintain backward compatibility with pure keyframe animations

## Architecture

### Core Components

#### 1. IKChain
Represents a chain of bones to be solved by IK.

```java
public class IKChain {
    private final List<Bone> bones;
    private final IKSolver solver;
    private Vector3f targetPosition;
    private Quaternionf targetRotation;
    private float weight = 1.0f; // Blend weight with keyframe animation
    
    public void solve();
    public void setTarget(Vector3f position, Quaternionf rotation);
    public void setWeight(float weight);
}
```

#### 2. IKSolver (Interface)
Abstract IK solving strategy.

```java
public interface IKSolver {
    void solve(List<Bone> chain, Vector3f target, Quaternionf targetRotation);
    SolverType getType();
}

enum SolverType {
    TWO_BONE,      // Fast 2-bone IK (legs, arms)
    FABRIK,        // Forward And Backward Reaching IK (multi-bone chains)
    CCD,           // Cyclic Coordinate Descent (fast approximation)
    LOOK_AT        // Simple look-at constraint
}
```

#### 3. IKManager
Manages IK chains and integrates with AnimationPlayer.

```java
public class IKManager {
    private final Map<String, IKChain> ikChains;
    private final Skeleton skeleton;
    
    public IKChain createChain(String name, List<String> boneNames, SolverType type);
    public void removeChain(String name);
    public void updateAll(); // Called after keyframe animation update
    public void setEnabled(boolean enabled);
}
```

#### 4. Skeleton
Enhanced bone hierarchy manager (extends existing Bone system).

```java
public class Skeleton {
    private final Map<String, Bone> boneMap;
    private final Bone rootBone;
    
    public Bone getBone(String name);
    public List<Bone> getBoneChain(String startBone, String endBone);
    public void updateTransforms(); // Existing method
}
```

### Integration with Existing System

```
AnimationPlayer.update(deltaTime)
    ↓
1. Update keyframe transforms (existing)
    ↓
2. IKManager.updateAll() (new)
    ↓
3. Blend IK results with keyframe transforms
    ↓
4. Skeleton.updateTransforms() (existing)
    ↓
5. Upload bone matrices to GPU (existing)
```

## Implementation Phases

### Phase 1: Foundation (Week 1)
**Goal:** Set up Caliko dependency and basic infrastructure

- [ ] Add Caliko to build.gradle
- [ ] Create `cn.minerealms.assimploader.animation.ik` package
- [ ] Implement `Skeleton` class (wraps existing Bone hierarchy)
- [ ] Implement `IKSolver` interface
- [ ] Implement `IKChain` class
- [ ] Add unit tests for bone chain extraction

**Files to create:**
- `animation/ik/Skeleton.java`
- `animation/ik/IKSolver.java`
- `animation/ik/IKChain.java`
- `animation/ik/IKManager.java`

**Files to modify:**
- `build.gradle` (add Caliko dependency)
- `animation/Bone.java` (add IK-related getters/setters)

### Phase 2: Basic Solvers (Week 2)
**Goal:** Implement common IK solvers

- [ ] Implement `TwoBoneIKSolver` (analytical solution for arms/legs)
- [ ] Implement `LookAtSolver` (simple rotation constraint)
- [ ] Implement `FABRIKSolver` (wrapper around Caliko's FABRIK)
- [ ] Add solver performance benchmarks
- [ ] Add visual debugging (render IK chains in LookDev)

**Files to create:**
- `animation/ik/solvers/TwoBoneIKSolver.java`
- `animation/ik/solvers/LookAtSolver.java`
- `animation/ik/solvers/FABRIKSolver.java`
- `animation/ik/solvers/CCDSolver.java`

### Phase 3: Animation Integration (Week 3)
**Goal:** Integrate IK with existing animation system

- [ ] Modify `AnimationPlayer` to support IK chains
- [ ] Implement IK/keyframe blending
- [ ] Add `IKManager` to `AnimationPlayer`
- [ ] Support IK weight animation (fade in/out)
- [ ] Add IK chain serialization (save/load)

**Files to modify:**
- `animation/AnimationPlayer.java`
- `animation/AnimationBlender.java` (support IK blending)

### Phase 4: High-Level API (Week 4)
**Goal:** Provide easy-to-use API for common scenarios

- [ ] Implement `FootIKController` (ground alignment)
- [ ] Implement `HandIKController` (object grabbing)
- [ ] Implement `HeadLookAtController` (look at target)
- [ ] Add IK presets (humanoid, quadruped, spider)
- [ ] Add LookDev UI for IK chain editing

**Files to create:**
- `animation/ik/controllers/FootIKController.java`
- `animation/ik/controllers/HandIKController.java`
- `animation/ik/controllers/HeadLookAtController.java`
- `animation/ik/IKPresets.java`

### Phase 5: Optimization & Polish (Week 5)
**Goal:** Performance optimization and edge case handling

- [ ] Implement bone constraint system (angle limits)
- [ ] Add IK chain caching (avoid redundant calculations)
- [ ] Optimize matrix operations (reuse buffers)
- [ ] Add multi-threading support for multiple IK chains
- [ ] Profile and optimize hot paths
- [ ] Add comprehensive documentation

## Technical Details

### Caliko Integration

**Dependency:**
```gradle
dependencies {
    implementation 'au.edu.federation.caliko:caliko:1.3.9'
}
```

**Coordinate System Conversion:**
Caliko uses right-handed Y-up, Minecraft uses right-handed Y-up → **No conversion needed**

### Performance Considerations

1. **Solver Selection:**
   - 2-bone IK: ~0.01ms per chain (analytical)
   - FABRIK: ~0.05-0.2ms per chain (iterative, 5-15 iterations)
   - CCD: ~0.03-0.1ms per chain (iterative, 5-10 iterations)

2. **Optimization Strategies:**
   - Only solve active IK chains (skip disabled chains)
   - Cache bone chain lists (avoid repeated lookups)
   - Use dirty flags (skip solving if target unchanged)
   - Batch IK updates (solve multiple chains in parallel)

3. **Memory:**
   - Pre-allocate IK chain structures
   - Reuse Vector3f/Quaternionf objects
   - Pool temporary matrices

### API Examples

#### Example 1: Foot IK (Ground Alignment)
```java
// Setup
IKManager ikManager = new IKManager(skeleton);
IKChain leftFootChain = ikManager.createChain(
    "leftFoot",
    Arrays.asList("LeftHip", "LeftKnee", "LeftAnkle"),
    SolverType.TWO_BONE
);

// Runtime
Vector3f groundPosition = raycastGround(leftFootPosition);
leftFootChain.setTarget(groundPosition, Quaternionf.IDENTITY);
leftFootChain.setWeight(0.8f); // 80% IK, 20% keyframe
ikManager.updateAll();
```

#### Example 2: Look-At
```java
IKChain headChain = ikManager.createChain(
    "head",
    Arrays.asList("Neck", "Head"),
    SolverType.LOOK_AT
);

headChain.setTarget(playerPosition, null);
headChain.setWeight(1.0f);
```

#### Example 3: Hand Grabbing
```java
HandIKController handIK = new HandIKController(ikManager, skeleton);
handIK.setLeftHandTarget(itemPosition, itemRotation);
handIK.setRightHandTarget(handlePosition, handleRotation);
handIK.update();
```

## Testing Strategy

### Unit Tests
- Bone chain extraction correctness
- IK solver accuracy (compare with known solutions)
- Blending weight calculations
- Constraint enforcement

### Integration Tests
- IK + keyframe animation blending
- Multiple IK chains on same skeleton
- IK chain enable/disable
- Serialization/deserialization

### Performance Tests
- Benchmark each solver type
- Stress test (100+ IK chains)
- Memory allocation profiling
- Multi-threading scalability

### Visual Tests (LookDev)
- Foot IK on uneven terrain
- Look-at tracking moving target
- Hand IK grabbing objects
- Full-body IK (multiple chains)

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Caliko API changes | High | Pin to specific version (1.3.9) |
| Performance regression | Medium | Benchmark before/after, add performance tests |
| Breaking existing animations | High | Maintain backward compatibility, IK is opt-in |
| Complex debugging | Medium | Add visual debugging tools in LookDev |
| Coordinate system mismatch | High | Verify coordinate systems early, add tests |

## Success Criteria

- [ ] All IK solvers pass accuracy tests (< 1cm error)
- [ ] Performance: < 0.5ms for 10 IK chains @ 60 FPS
- [ ] Zero breaking changes to existing animation code
- [ ] Complete API documentation with examples
- [ ] Visual debugging tools in LookDev
- [ ] At least 3 high-level controllers (Foot, Hand, LookAt)

## Future Enhancements (Post-MVP)

- Physics-based IK (ragdoll integration)
- IK recording (bake IK results to keyframes)
- Advanced constraints (pole vectors, twist limits)
- IK retargeting (transfer IK between different skeletons)
- GPU-accelerated IK solving (compute shaders)

## References

- Caliko Documentation: https://github.com/FedUni/caliko
- FABRIK Paper: http://www.andreasaristidou.com/FABRIK.html
- Two-Bone IK: https://theorangeduck.com/page/simple-two-joint
- Unity IK System: https://docs.unity3d.com/Manual/InverseKinematics.html
