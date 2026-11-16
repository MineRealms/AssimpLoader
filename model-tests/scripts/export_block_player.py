"""Validate/re-export the existing fixture scene without rebuilding or deleting objects.

Run with __file__ set to this script's path. Set ASSIMPLOADER_EXPORT=False for
validation only. Existing selection, action, NLA mutes and scene are restored.
"""

import bpy
import json
import math
from pathlib import Path
from mathutils import Vector

root = Path(__file__).resolve().parents[2]
assets = root / "src/main/resources/assets/assimploader/models/character/block_player"
scene = bpy.data.scenes.get("BlockPlayer_CharacterEngine")
if scene is None:
    raise RuntimeError("Open model-tests/block_player/block_player.blend first")
rig = scene.objects.get("BlockPlayerRig")
meshes = [obj for obj in scene.objects if obj.type == "MESH" and
          any(mod.type == "ARMATURE" and mod.object == rig for mod in obj.modifiers)]
if rig is None or rig.type != "ARMATURE" or not meshes:
    raise RuntimeError("Missing fixture rig or skinned meshes")
if bpy.context.mode != "OBJECT":
    raise RuntimeError("Finish the active edit/pose operation before exporting")

deform = {bone.name for bone in rig.data.bones if bone.use_deform}
unweighted = 0
non_normalized = 0
blended = 0
vertices = 0
bounds = []
for obj in meshes:
    group_names = {group.index: group.name for group in obj.vertex_groups}
    for vertex in obj.data.vertices:
        weights = [group.weight for group in vertex.groups if
                   group_names.get(group.group) in deform and group.weight > 0]
        if any(not math.isfinite(weight) for weight in weights):
            raise RuntimeError("Non-finite skin weight")
        total = sum(weights)
        unweighted += total <= 0
        non_normalized += abs(total - 1) > 1e-4
        blended += len(weights) > 1
        vertices += 1
    bounds.extend(obj.matrix_world @ Vector(corner) for corner in obj.bound_box)

tracks = list(rig.animation_data.nla_tracks) if rig.animation_data else []
actions = [track.name for track in tracks if len(track.strips)]
expected = {"idle", "walk", "run", "crouch", "crouch_walk", "jump", "fall",
            "swim", "fly", "elytra", "ride", "sleep", "dead", "use_item", "swing"}
if set(actions) != expected or len(actions) != len(expected):
    raise RuntimeError("Expected exactly 15 named action tracks: " + str(actions))
if unweighted or non_normalized or not blended:
    raise RuntimeError(f"Invalid skin: unweighted={unweighted}, non_normalized={non_normalized}, blended={blended}")
minimum = [min(point[axis] for point in bounds) for axis in range(3)]
maximum = [max(point[axis] for point in bounds) for axis in range(3)]
if abs(minimum[2]) > .02 or not 1.7 < maximum[2] - minimum[2] < 1.95:
    raise RuntimeError("Unexpected authored metre-space height/floor")
report = {"vertices": vertices, "bones": len(rig.data.bones), "blendedVertices": blended,
          "unweighted": unweighted, "nonNormalized": non_normalized,
          "actions": sorted(actions), "minimum": minimum, "maximum": maximum}

if globals().get("ASSIMPLOADER_EXPORT", True):
    old_scene = bpy.context.window.scene
    old_selected = list(bpy.context.selected_objects)
    old_active = bpy.context.view_layer.objects.active
    old_action = rig.animation_data.action
    old_mutes = [track.mute for track in tracks]
    try:
        bpy.context.window.scene = scene
        for obj in bpy.context.selected_objects:
            obj.select_set(False)
        rig.select_set(True)
        for obj in meshes:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = rig
        rig.animation_data.action = None
        for track in tracks:
            track.mute = False
        bpy.ops.export_scene.fbx(filepath=str(assets / "block_player.fbx"), use_selection=True,
            object_types={"ARMATURE", "MESH"}, add_leaf_bones=False, bake_anim=True,
            bake_anim_use_all_actions=False, bake_anim_use_nla_strips=True,
            bake_anim_force_startend_keying=True, axis_forward="-Z", axis_up="Y",
            apply_unit_scale=True, use_mesh_modifiers=False, path_mode="RELATIVE")
        bpy.ops.export_scene.gltf(filepath=str(assets / "block_player.glb"), export_format="GLB",
            use_selection=True, use_active_scene=True, export_animations=True,
            export_animation_mode="NLA_TRACKS", export_morph=True, export_skins=True, export_yup=True)
        report["exported"] = ["block_player.fbx", "block_player.glb"]
    finally:
        rig.animation_data.action = old_action
        for track, mute in zip(tracks, old_mutes):
            track.mute = mute
        for obj in bpy.context.selected_objects:
            obj.select_set(False)
        bpy.context.window.scene = old_scene
        for obj in old_selected:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = old_active
print(json.dumps(report, indent=2))
