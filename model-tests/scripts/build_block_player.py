"""Run in Blender. Build independent character fixtures without deleting the deer scene."""

import bpy
import json
import math
from pathlib import Path
from mathutils import Vector

ROOT = Path("H:/MinecraftMods/AssimpLoader")
OUT = ROOT / "model-tests/block_player"
ASSET = ROOT / "src/main/resources/assets/assimploader/models/character/block_player"
OUT.mkdir(parents=True, exist_ok=True)
ASSET.mkdir(parents=True, exist_ok=True)

scene = bpy.data.scenes.new("BlockPlayer_CharacterEngine")
bpy.context.window.scene = scene
scene.unit_settings.system = "METRIC"
scene.unit_settings.scale_length = 1
scene.render.fps = 30

palette = [
    (0.64, 0.36, 0.22), (0.025, 0.23, 0.28), (0.025, 0.07, 0.12),
    (0.04, 0.045, 0.05), (0.18, 0.055, 0.025), (0.92, 0.94, 0.92),
    (0.04, 0.31, 0.65), (0.64, 0.09, 0.06),
]


def texture(name, values, color_space):
    image = bpy.data.images.new(name, width=64, height=64, alpha=True)
    pixels = []
    for y in range(64):
        for x in range(64):
            color = values[min(7, x // 8)]
            noise = ((x * 13 + y * 7) % 9 - 4) / 255 if name.endswith("albedo") else 0
            pixels.extend([max(0, min(1, c + noise)) for c in color] + [1])
    image.pixels = pixels
    image.colorspace_settings.name = color_space
    image.filepath_raw = str(ASSET / (name + ".png"))
    image.file_format = "PNG"
    image.save()
    image.pack()
    return image


albedo = texture("block_player_albedo", palette, "sRGB")
roughness = texture("block_player_roughness", [(v, v, v) for v in [0.65, 0.8, 0.9, 0.5, 0.85, 0.4, 0.3, 0.45]], "Non-Color")
metallic = texture("block_player_metallic", [(v, v, v) for v in [0, 0, 0, 0, 0, 0, 0, 0.7]], "Non-Color")
normal = texture("block_player_normal", [(0.5, 0.5, 1)] * 8, "Non-Color")
material = bpy.data.materials.new("BlockPlayer_PBR")
material.use_nodes = True
nodes = material.node_tree.nodes
links = material.node_tree.links
bsdf = nodes.get("Principled BSDF")
for image, socket in [(albedo, "Base Color"), (roughness, "Roughness"), (metallic, "Metallic")]:
    node = nodes.new("ShaderNodeTexImage")
    node.image = image
    node.interpolation = "Closest"
    links.new(node.outputs["Color"], bsdf.inputs[socket])
normal_tex = nodes.new("ShaderNodeTexImage")
normal_tex.image = normal
normal_map = nodes.new("ShaderNodeNormalMap")
links.new(normal_tex.outputs["Color"], normal_map.inputs["Color"])
links.new(normal_map.outputs["Normal"], bsdf.inputs["Normal"])

armature = bpy.data.armatures.new("BlockPlayerSkeleton")
rig = bpy.data.objects.new("BlockPlayerRig", armature)
scene.collection.objects.link(rig)
bpy.context.view_layer.objects.active = rig
rig.select_set(True)
bpy.ops.object.mode_set(mode="EDIT")
bones = [
    ("Root", (0, 0, 0), (0, 0, .18), None),
    ("Hips", (0, 0, .85), (0, 0, 1.0), "Root"),
    ("Spine", (0, 0, 1.0), (0, 0, 1.2), "Hips"),
    ("Chest", (0, 0, 1.2), (0, 0, 1.4), "Spine"),
    ("Neck", (0, 0, 1.4), (0, 0, 1.5), "Chest"),
    ("Head", (0, 0, 1.5), (0, 0, 1.8), "Neck"),
    ("Hair1", (0, .19, 1.71), (0, .21, 1.5), "Head"),
    ("Hair2", (0, .21, 1.5), (0, .23, 1.3), "Hair1"),
    ("Pendant1", (0, -.18, 1.38), (0, -.21, 1.18), "Chest"),
    ("Pendant2", (0, -.21, 1.18), (0, -.23, .99), "Pendant1"),
    ("ChestFollower", (.1, 0, 1.2), (.1, 0, 1.35), "Root"),
]
for side, x in [("L", .36), ("R", -.36)]:
    bones.extend([
        (f"UpperArm.{side}", (x, 0, 1.36), (x, 0, 1.07), "Chest"),
        (f"Forearm.{side}", (x, 0, 1.07), (x, 0, .80), f"UpperArm.{side}"),
        (f"Hand.{side}", (x, 0, .80), (x, 0, .68), f"Forearm.{side}"),
        (f"Thigh.{side}", (x / 3, 0, .85), (x / 3, -.025, .45), "Hips"),
        (f"Shin.{side}", (x / 3, -.025, .45), (x / 3, 0, .10), f"Thigh.{side}"),
        (f"Foot.{side}", (x / 3, 0, .10), (x / 3, -.20, .10), f"Shin.{side}"),
    ])
for name, head, tail, parent in bones:
    bone = armature.edit_bones.new(name)
    bone.head, bone.tail = head, tail
    if parent:
        bone.parent = armature.edit_bones[parent]
bpy.ops.object.mode_set(mode="OBJECT")
rig.show_in_front = True

vertices, faces, assignments, columns = [], [], [], []
part_vertices = {}


def box(name, center, size, bone, color):
    start = len(vertices)
    corners = [Vector(center) + Vector((sx * size[0] / 2, sy * size[1] / 2, sz * size[2] / 2))
               for sx, sy, sz in [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                                 (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]]
    # Split corners across faces for flat normals and independently addressable UVs.
    for face in [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]:
        offset = len(vertices)
        vertices.extend([tuple(corners[i]) for i in face])
        faces.append(tuple(range(offset, offset + 4)))
        assignments.extend([bone] * 4)
        columns.append(color)
    part_vertices[name] = list(range(start, len(vertices)))


box("Torso", (0, 0, 1.125), (.48, .29, .55), "Chest", 1)
box("Belt", (0, 0, .87), (.48, .30, .08), "Hips", 3)
box("Head", (0, 0, 1.59), (.45, .43, .42), "Head", 0)
box("HairCap", (0, .01, 1.79), (.47, .45, .07), "Head", 4)
box("HairBack", (0, .20, 1.65), (.46, .05, .28), "Head", 4)
box("Hair1", (0, .24, 1.54), (.18, .08, .20), "Hair1", 4)
box("Hair2", (0, .26, 1.34), (.16, .08, .20), "Hair2", 4)
box("Pendant1", (0, -.21, 1.28), (.075, .065, .20), "Pendant1", 7)
box("Pendant2", (0, -.23, 1.08), (.09, .065, .18), "Pendant2", 7)
for side, x in [("L", .36), ("R", -.36)]:
    box(f"UpperArm.{side}", (x, 0, 1.215), (.20, .24, .29), f"UpperArm.{side}", 1)
    box(f"Forearm.{side}", (x, 0, .935), (.18, .22, .27), f"Forearm.{side}", 0)
    box(f"Hand.{side}", (x, -.015, .745), (.19, .24, .11), f"Hand.{side}", 0)
    box(f"Thigh.{side}", (x / 3, 0, .65), (.215, .27, .40), f"Thigh.{side}", 2)
    box(f"Shin.{side}", (x / 3, 0, .275), (.20, .255, .35), f"Shin.{side}", 2)
    box(f"Boot.{side}", (x / 3, -.04, .075), (.22, .34, .15), f"Foot.{side}", 3)
    box(f"Eye.{side}", (x / 3, -.22, 1.64), (.12, .012, .075), "Head", 5)
    box(f"Iris.{side}", (x / 3 - .015, -.23, 1.64), (.042, .014, .075), "Head", 6)
box("Mouth", (0, -.22, 1.49), (.12, .014, .035), "Head", 7)

mesh = bpy.data.meshes.new("BlockPlayerMesh")
mesh.from_pydata(vertices, [], faces)
mesh.update()
obj = bpy.data.objects.new("BlockPlayer", mesh)
scene.collection.objects.link(obj)
mesh.materials.append(material)
uvs = mesh.uv_layers.new(name="UVMap")
for polygon, column in zip(mesh.polygons, columns):
    for loop_index, (u, v) in zip(polygon.loop_indices, [(0, 0), (1, 0), (1, 1), (0, 1)]):
        uvs.data[loop_index].uv = ((column * 8 + 1 + u * 5) / 64, (.1 + v * .8))
for name in armature.bones.keys():
    group = obj.vertex_groups.new(name=name)
    indices = [i for i, owner in enumerate(assignments) if owner == name]
    if indices:
        group.add(indices, 1, "REPLACE")
# A small seam strip uses genuine blended weights, not just rigid segmented cubes.
for side in ("L", "R"):
    for index in part_vertices[f"Forearm.{side}"]:
        z = vertices[index][2]
        if z > 1.02:
            obj.vertex_groups[f"Forearm.{side}"].add([index], .7, "REPLACE")
            obj.vertex_groups[f"UpperArm.{side}"].add([index], .3, "REPLACE")
modifier = obj.modifiers.new("SkeletalSkin", "ARMATURE")
modifier.object = rig
obj.parent = rig
obj.shape_key_add(name="Basis")
blink = obj.shape_key_add(name="Blink")
for side in ("L", "R"):
    for part in (f"Eye.{side}", f"Iris.{side}"):
        for index in part_vertices[part]:
            blink.data[index].co.z = 1.64 + (blink.data[index].co.z - 1.64) * .12
smile = obj.shape_key_add(name="Smile")
for index in part_vertices["Mouth"]:
    smile.data[index].co.z += abs(smile.data[index].co.x) * .30

actions = {}
for name in ["idle", "walk", "run", "crouch", "crouch_walk", "jump", "fall", "swim", "fly", "elytra", "ride", "sleep", "dead", "use_item", "swing"]:
    rig.animation_data_create()
    action = bpy.data.actions.new(name)
    rig.animation_data.action = action
    for frame in range(1, 32, 3):
        t = (frame - 1) / 30
        wave = math.sin(t * math.tau)
        for bone in rig.pose.bones:
            bone.rotation_mode = "XYZ"
            bone.rotation_euler = (0, 0, 0)
            bone.location = (0, 0, 0)
        if name in ("walk", "run", "crouch_walk"):
            amplitude = .48 if name != "run" else .82
            for side, sign in [("L", 1), ("R", -1)]:
                rig.pose.bones[f"Thigh.{side}"].rotation_euler.x = wave * amplitude * sign
                rig.pose.bones[f"Shin.{side}"].rotation_euler.x = max(0, -wave * sign) * .60
                rig.pose.bones[f"UpperArm.{side}"].rotation_euler.x = -wave * amplitude * sign
                rig.pose.bones[f"Forearm.{side}"].rotation_euler.x = -.12
        if name in ("crouch", "crouch_walk"):
            rig.pose.bones["Hips"].location.y = -.16
            rig.pose.bones["Chest"].rotation_euler.x = .25
            for side in ("L", "R"):
                rig.pose.bones[f"Thigh.{side}"].rotation_euler.x += -.45
                rig.pose.bones[f"Shin.{side}"].rotation_euler.x += .72
        if name in ("jump", "fall"):
            for side in ("L", "R"):
                rig.pose.bones[f"UpperArm.{side}"].rotation_euler.x = -.5
                rig.pose.bones[f"Thigh.{side}"].rotation_euler.x = -.22
                rig.pose.bones[f"Shin.{side}"].rotation_euler.x = .4
        if name in ("swim", "elytra", "fly", "sleep", "dead"):
            rig.pose.bones["Hips"].rotation_euler.x = math.pi / 2 if name != "fly" else .15
            if name in ("swim", "fly"):
                rig.pose.bones["UpperArm.L"].rotation_euler.x = -1.3 + wave * .5
                rig.pose.bones["UpperArm.R"].rotation_euler.x = -1.3 - wave * .5
        if name == "ride":
            for side in ("L", "R"):
                rig.pose.bones[f"Thigh.{side}"].rotation_euler.x = -1.4
                rig.pose.bones[f"Shin.{side}"].rotation_euler.x = 1.4
        if name in ("use_item", "swing"):
            rig.pose.bones["UpperArm.R"].rotation_euler.x = -1.3 + (wave * .7 if name == "swing" else 0)
            rig.pose.bones["Forearm.R"].rotation_euler.x = -.5
        if name == "idle":
            rig.pose.bones["Chest"].rotation_euler.x = wave * .015
        for bone in rig.pose.bones:
            bone.keyframe_insert(data_path="rotation_euler", frame=frame, group=bone.name)
            bone.keyframe_insert(data_path="location", frame=frame, group=bone.name)
    actions[name] = action
    track = rig.animation_data.nla_tracks.new()
    track.name = name
    strip = track.strips.new(name, 1, action)
    track.mute = True
rig.animation_data.action = actions["idle"]
scene.frame_set(1)

# Keep authoring IK controls separate from the exported deform rig's baked actions.
ik_collection = bpy.data.collections.new("Authoring_IK_Controls")
scene.collection.children.link(ik_collection)
for side in ("L", "R"):
    control = bpy.data.objects.new(f"IK_Target.{side}", None)
    control.location = (.12 if side == "L" else -.12, 0, .1)
    control.empty_display_type = "SPHERE"
    control.empty_display_size = .08
    ik_collection.objects.link(control)
    constraint = rig.pose.bones[f"Shin.{side}"].constraints.new("IK")
    constraint.name = "AuthoringIK_ExportRuntimeDefinition"
    constraint.target = control
    constraint.chain_count = 2
    constraint.mute = True

for selected in bpy.context.selected_objects:
    selected.select_set(False)
rig.select_set(True)
obj.select_set(True)
bpy.context.view_layer.objects.active = rig
rig.animation_data.action = None
for track in rig.animation_data.nla_tracks:
    track.mute = False
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
rig.animation_data.action = actions["idle"]
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / "block_player.blend"))
manifest = {
    "source": "Independent generated block-style character; no CustomSteve assets",
    "blender": bpy.app.version_string, "vertices": len(mesh.vertices),
    "triangles": sum(len(p.vertices) - 2 for p in mesh.polygons),
    "bones": len(armature.bones), "actions": list(actions),
    "morphs": ["Blink", "Smile"], "heightMetres": 1.825,
    "rig": rig.name, "asset": "assimploader:models/character/block_player/block_player.fbx",
    "note": "Runtime IK/append/rigid-body semantics are declared in sidecar resources, not baked physics."
}
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(json.dumps(manifest))
