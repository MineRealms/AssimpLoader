"""Run in the live Blender after saving deer_source.blend. Source is user supplied."""
import bpy
import json
import math
from pathlib import Path
from mathutils import Matrix, Vector

BASE = Path('H:/MinecraftMods/AssimpLoader/model-tests/deer')
scene = bpy.context.scene
source_root = bpy.data.objects['root']
nodes = [source_root] + [o for o in source_root.children_recursive if o.type == 'EMPTY']
def depth(obj):
    return 1 + depth(obj.parent) if obj.parent else 0
nodes.sort(key=depth)
meshes = [o for o in source_root.children_recursive if o.type == 'MESH']
actions = {a.name: a for a in bpy.data.actions}
conversion = Matrix.Identity(4)

def select_action(action):
    for node in nodes:
        node.animation_data_create()
        slot = next((s for s in action.slots if s.identifier == 'OB' + node.name), None)
        node.animation_data.action = action if slot else None
        if slot:
            node.animation_data.action_slot = slot

select_action(actions['idle'])
scene.frame_set(1)
bpy.context.view_layer.update()
rest = {o.name: conversion @ o.matrix_world for o in nodes}
samples = {}
for name, action in actions.items():
    select_action(action)
    samples[name] = []
    for frame in range(1, round(action.frame_range[1]) + 1):
        scene.frame_set(frame)
        samples[name].append({o.name: conversion @ o.matrix_world for o in nodes})
select_action(actions['idle'])
scene.frame_set(1)

collection = bpy.data.collections.new('DEER_EXPORT')
scene.collection.children.link(collection)
armature = bpy.data.armatures.new('DeerSkeleton')
rig = bpy.data.objects.new('DeerRig', armature)
collection.objects.link(rig)
bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
for node in nodes:
    bone = armature.edit_bones.new(node.name)
    bone.head = rest[node.name].translation
    children = [c for c in node.children if c.type == 'EMPTY']
    vectors = [rest[c.name].translation - bone.head for c in children]
    direction = next((v for v in vectors if v.length > .025), Vector((0, 0, .12)))
    bone.tail = bone.head + direction
    if node.parent and node.parent.name in armature.edit_bones:
        bone.parent = armature.edit_bones[node.parent.name]
for leg in ('FL', 'FR', 'BL', 'BR'):
    end = armature.edit_bones['leg' + leg + '2']
    foot = next(o for o in meshes if o.parent.name == end.name and 'foot' in o.name.lower())
    foot_points = [conversion @ foot.matrix_world @ v.co for v in foot.data.vertices]
    center = sum(foot_points, Vector()) / len(foot_points)
    center.z = min(p.z for p in foot_points)
    end.tail = center
    hoof = armature.edit_bones.new('hoof' + leg)
    hoof.head = center
    hoof.tail = center + Vector((0, -.1, 0))
    hoof.parent = end
bpy.ops.object.mode_set(mode='OBJECT')
bone_rest = {b.name: b.matrix_local.copy() for b in armature.bones}

vertices, faces, uv_faces, owners = [], [], [], []
for obj in meshes:
    offset = len(vertices)
    vertices.extend([conversion @ obj.matrix_world @ v.co for v in obj.data.vertices])
    owners.extend([obj.parent.name] * len(obj.data.vertices))
    uv = obj.data.uv_layers.active
    for polygon in obj.data.polygons:
        faces.append([offset + i for i in polygon.vertices])
        uv_faces.append([tuple(uv.data[i].uv) if uv else (0, 0) for i in polygon.loop_indices])
mesh = bpy.data.meshes.new('DeerMesh')
mesh.from_pydata(vertices, [], faces)
mesh.update()
layer = mesh.uv_layers.new(name='UVMap')
for polygon, coords in zip(mesh.polygons, uv_faces):
    for loop_index, coord in zip(polygon.loop_indices, coords):
        layer.data[loop_index].uv = coord
material = meshes[0].data.materials[0]
mesh.materials.append(material)
for n in material.node_tree.nodes:
    if n.type == 'TEX_IMAGE' and n.image:
        n.image.pack()
        n.image.filepath_raw = str(BASE / 'deer_albedo.png')
        n.image.file_format = 'PNG'
        n.image.save()
body = bpy.data.objects.new('DeerMesh', mesh)
collection.objects.link(body)
body.parent = rig
for name in sorted(set(owners)):
    group = body.vertex_groups.new(name=name)
    group.add([i for i, owner in enumerate(owners) if owner == name], 1, 'REPLACE')
modifier = body.modifiers.new('Skin', 'ARMATURE')
modifier.object = rig

rig.animation_data_create()
baked = {}
for name, frames in samples.items():
    actions[name].name = 'SOURCE::' + name
    actions[name].use_fake_user = True
    action = bpy.data.actions.new(name)
    rig.animation_data.action = action
    previous = {}
    for index, frame in enumerate(frames):
        desired = {name: frame[name] @ rest[name].inverted() @ bone_rest[name] for name in frame}
        for leg in ('FL', 'FR', 'BL', 'BR'):
            name_hoof = 'hoof' + leg
            parent = 'leg' + leg + '2'
            desired[name_hoof] = desired[parent] @ bone_rest[parent].inverted() @ bone_rest[name_hoof]
        for bone in armature.bones:
            pose = rig.pose.bones[bone.name]
            pose.rotation_mode = 'QUATERNION'
            kwargs = {'parent_matrix': desired[bone.parent.name], 'parent_matrix_local': bone.parent.matrix_local} if bone.parent else {}
            basis = bone.convert_local_to_pose(desired[bone.name], bone.matrix_local, invert=True, **kwargs)
            location, rotation, scale = basis.decompose()
            if bone.name in previous and rotation.dot(previous[bone.name]) < 0:
                rotation.negate()
            previous[bone.name] = rotation.copy()
            pose.location, pose.rotation_quaternion, pose.scale = location, rotation, scale
            for prop in ('location', 'rotation_quaternion', 'scale'):
                pose.keyframe_insert(prop, frame=index + 1, group=bone.name)
    for action_layer in action.layers:
        for strip in action_layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    for key in curve.keyframe_points:
                        key.interpolation = 'LINEAR'
    baked[name] = action
rig.animation_data.action = baked['idle']
source_root.hide_set(True)
source_root.hide_render = True
for obj in source_root.children_recursive:
    obj.hide_set(True)
    obj.hide_render = True
scene.frame_set(1)
bpy.context.view_layer.update()

# Validate every sampled node before authoring additional controls.
maximum_error = 0.0
for name, frames in samples.items():
    rig.animation_data.action = baked[name]
    for index in (0, len(frames) // 2, len(frames) - 1):
        scene.frame_set(index + 1)
        for node in nodes:
            expected = frames[index][node.name] @ rest[node.name].inverted() @ bone_rest[node.name]
            actual = rig.pose.bones[node.name].matrix
            maximum_error = max(maximum_error, max(abs(actual[r][c] - expected[r][c]) for r in range(4) for c in range(4)))
assert maximum_error < .0001, maximum_error
rig.animation_data.action = baked['idle']
scene.frame_set(1)
manifest = {'source': 'User-opened FBX; original license/provenance not supplied',
            'sourceNodes': len(nodes), 'sourceMeshes': len(meshes), 'exportMeshes': 1,
            'bones': len(armature.bones), 'vertices': len(mesh.vertices),
            'clips': {name: len(frames) for name, frames in samples.items()},
            'fps': scene.render.fps, 'bakeMatrixMaximumError': maximum_error,
            'units': 'meters', 'forward': '+Z after AssimpLoader coordinate conversion'}
(BASE / 'deer_manifest.json').write_text(json.dumps(manifest, indent=2))
bpy.ops.wm.save_as_mainfile(filepath=str(BASE / 'deer_rigged.blend'), check_existing=False)
print(json.dumps(manifest))
