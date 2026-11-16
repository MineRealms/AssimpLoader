# Character Semantic Motion

The rig sidecar accepts an optional `motions` object. Each named motion has a
duration in seconds and named `bones`, `morphs` and `ik` tracks. This layer runs
before bone Morph, append transforms, ground/IK, rigid-body physics and skinning.
An imported animation still supplies nodes not named by the active semantic
motion. There is no PMX/VMD parser in this feature.

```json
{
  "version": 1,
  "motions": {
    "nod": {
      "duration": 1,
      "bones": {
        "Head": [
          {"time": 0, "translation": [0,0,0], "rotation": [0,0,0,1]},
          {"time": 1, "translation": [0,0,0], "rotation": [0.1,0,0,0.9949874],
           "curves": {"rotation": [0.25,0,0.75,1]}}
        ]
      },
      "morphs": {"Blink": [{"time":0,"weight":0},{"time":1,"weight":1}]},
      "ik": {"left_leg_probe": [{"time":0.5,"enabled":false}]}
    }
  }
}
```

Bone keys are rest-relative local translation offsets and quaternion rotations
in XYZW order. Translation uses the imported node's local units, which may differ
from metres in FBX. Existing rest translation, rotation and scale are preserved
around those offsets. Track values do not directly mutate the cached model.

The destination key owns the interval's cubic Bezier controls. `curves.x`, `y`,
`z` and `rotation` each accept `[x1,y1,x2,y2]` in `[0,1]`. Omitted curves are
linear. Position axes are independent; quaternion interpolation follows the
shortest arc with its own time curve. Bezier X is inverted with bounded bisection,
including flat endpoint derivatives. Linear curves use a direct fast path.

Morph tracks interpolate linearly and feed the existing group/bone/vertex Morph
solver. Nonzero explicit `setWeight` inputs override a motion's input for that
same name; setting zero retains the existing API meaning of clearing the manual
input. Imported default mesh weights can be overridden through rig `defaults`.
IK switches are stepped at exact timestamps and retain the pre-play enabled
value before the first switch. Stop restores that value. Bone/Morph endpoints
hold outside the key range; looping wraps the entire declared duration.

```java
var installed = CharacterRigDefinition.read(reader).install(animation);
installed.motions().play("nod", false);
installed.motions().pause();
installed.motions().seek(0.5);
installed.motions().resume();
installed.motions().reset();
```

The animation state's single writer advances the motion once per simulation
update. Rendering, snapshot reads and same-time pose publication never advance
it. Nonlooping playback holds the final sample. Seek and play become visible at
the next pose publication. Pausing this motion holds its sample while underlying
clips and physics continue; pausing the animation state pauses the whole solver.

Validation rejects unknown nodes/Morph/IK names, unordered or duplicate times,
keys beyond duration, non-finite values, zero quaternions and unknown fields.
Limits are 256 named motions, 256 tracks per type, 4096 keys per track, 16384 keys
per motion and at most 86400 seconds. Names and motion data are immutable after
installation. The optional layer remains inactive until explicitly played.

This does not implement camera/light tracks, model visibility, root-motion
extraction, cross-rig retargeting, SDEF/QDEF or format-specific coordinate
conversion. A future format adapter must convert those semantics explicitly.
