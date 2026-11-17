"""Offline Blender repair/export of the existing fixture, preserving every scene."""
import bpy
from pathlib import Path

ROOT = Path("H:/MinecraftMods/AssimpLoader")
ASSET = ROOT / "src/main/resources/assets/assimploader/models/character/block_player"
scene = bpy.data.scenes["BlockPlayer_CharacterEngine"]
bpy.context.window.scene = scene
rig = next(obj for obj in scene.objects if obj.type == "ARMATURE")
mesh = next(obj for obj in scene.objects if obj.type == "MESH" and obj.parent == rig)
for obj in bpy.context.selected_objects:
    obj.select_set(False)
rig.select_set(True)
mesh.select_set(True)
bpy.context.view_layer.objects.active = rig
idle = rig.animation_data.action
rig.animation_data.action = None
for track in rig.animation_data.nla_tracks:
    track.mute = False
    for strip in track.strips:
        strip.extrapolation = "NOTHING"
for bone in rig.pose.bones:
    bone.matrix_basis.identity()
scene.frame_set(0)
bpy.context.view_layer.update()
print("NEUTRAL", [(side, list(rig.pose.bones["Hand." + side].head)) for side in ("L", "R")])
bpy.ops.export_scene.fbx(filepath=str(ASSET / "block_player.fbx"), use_selection=True,
    object_types={"ARMATURE", "MESH"}, add_leaf_bones=False, bake_anim=True,
    bake_anim_use_all_actions=False, bake_anim_use_nla_strips=True,
    bake_anim_force_startend_keying=True, axis_forward="-Z", axis_up="Y",
    apply_unit_scale=True, use_mesh_modifiers=False, path_mode="RELATIVE")
bpy.ops.export_scene.gltf(filepath=str(ASSET / "block_player.glb"), export_format="GLB",
    use_selection=True, use_active_scene=True, export_animations=True, export_animation_mode="NLA_TRACKS",
    export_morph=True, export_skins=True, export_yup=True)
for track in rig.animation_data.nla_tracks:
    track.mute = True
rig.animation_data.action = idle
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "model-tests/block_player/block_player.blend"))
print("EXPORTED", len(rig.data.bones), "bones", len(rig.animation_data.nla_tracks), "tracks")
