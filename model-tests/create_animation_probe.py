"""Rebuild the original AssimpLoader skeletal acceptance fixture with Blender."""
import json
import math
from pathlib import Path

import bpy
from mathutils import Quaternion, Vector


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'model-tests' / 'animation_probe.blend'
OUTPUT = ROOT / 'src/main/resources/assets/assimploader/models/validation/animation_probe.fbx'
PREVIEW = ROOT / 'model-tests' / 'animation_probe.png'
MANIFEST = ROOT / 'model-tests' / 'animation_probe.json'
REFERENCE = ROOT / 'src/test/resources/fixtures/animation_probe_reference.json'
OUTPUT.parent.mkdir(parents=True, exist_ok=True)

# This script runs in a factory-startup background process, not the user's live scene.
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
scene.render.fps = 24
scene.frame_start = 1
scene.frame_end = 49


def material(name, color):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    shader = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = .48
    mat.diffuse_color = (*color, 1)
    return mat


teal = material('ShoulderTeal', (.025, .55, .47))
red = material('ElbowRed', (.86, .075, .12))
yellow = material('JointYellow', (1, .64, .025))
white = material('DirectionWhite', (.9, .95, .96))
dark = material('BaseGraphite', (.09, .12, .14))

checker = bpy.data.images.new('ProbeChecker', width=64, height=64)
pixels = []
for y in range(64):
    for x in range(64):
        color = (.04, .55, .47, 1) if ((x // 8) + (y // 8)) % 2 else (.7, .94, .88, 1)
        pixels.extend(color)
checker.pixels.foreach_set(pixels)
checker.filepath_raw = str(ROOT / 'model-tests' / 'animation_probe_checker.png')
checker.file_format = 'PNG'
checker.save()
checker.pack()
image_node = teal.node_tree.nodes.new('ShaderNodeTexImage')
image_node.image = checker
teal_shader = next(n for n in teal.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
teal.node_tree.links.new(image_node.outputs['Color'], teal_shader.inputs['Base Color'])

rig_data = bpy.data.armatures.new('AnimationProbeSkeleton')
rig = bpy.data.objects.new('AnimationProbeRig', rig_data)
scene.collection.objects.link(rig)
bpy.context.view_layer.objects.active = rig
rig.select_set(True)
bpy.ops.object.mode_set(mode='EDIT')
shoulder = rig_data.edit_bones.new('Shoulder')
shoulder.head, shoulder.tail = (0, 0, .3), (0, 0, 1.5)
elbow = rig_data.edit_bones.new('Elbow')
elbow.head, elbow.tail = shoulder.tail, (0, 0, 2.7)
elbow.parent = shoulder
elbow.use_connect = True
socket = rig_data.edit_bones.new('ToolSocket')
socket.head, socket.tail = elbow.tail, (.28, 0, 2.7)
socket.parent = elbow
socket.use_deform = False
bpy.ops.object.mode_set(mode='OBJECT')
rig.show_in_front = True
for bone in rig.pose.bones:
    bone.rotation_mode = 'QUATERNION'


def bind(obj, bone_name):
    group = obj.vertex_groups.new(name=bone_name)
    group.add(list(range(len(obj.data.vertices))), 1, 'REPLACE')
    obj.parent = rig
    modifier = obj.modifiers.new('Armature', 'ARMATURE')
    modifier.object = rig
    return obj


def box(name, location, scale, mat, bone=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(mat)
    if bone:
        bind(obj, bone)
    return obj


box('Base', (0, 0, .12), (.9, .65, .24), dark)
box('UpperArm', (0, 0, .88), (.4, .32, 1.16), teal, 'Shoulder')
box('Forearm', (0, 0, 2.1), (.31, .28, 1.18), red, 'Elbow')
box('ForwardMarker', (.22, -.2, 2.47), (.25, .15, .12), white, 'Elbow')
box('SocketHousing', (.19, 0, 2.7), (.5, .26, .16), yellow, 'Elbow')
box('ShoulderAxis', (0, -.23, .3), (.52, .13, .22), yellow, 'Shoulder')
box('ElbowAxis', (0, -.2, 1.5), (.47, .13, .22), yellow, 'Elbow')

# A segmented bridge exercises genuinely blended weights as well as rigid segments.
vertices, faces = [], []
for z in (1.3, 1.4, 1.5, 1.6, 1.7):
    vertices.extend([(-.12, .17, z), (.12, .17, z), (.12, .26, z), (-.12, .26, z)])
for ring in range(4):
    for side in range(4):
        a, b = ring * 4 + side, ring * 4 + (side + 1) % 4
        faces.append((a, b, b + 4, a + 4))
faces.extend([(3, 2, 1, 0), (16, 17, 18, 19)])
mesh = bpy.data.meshes.new('BlendBridgeMesh')
mesh.from_pydata(vertices, [], faces)
mesh.update()
bridge = bpy.data.objects.new('BlendBridge', mesh)
scene.collection.objects.link(bridge)
bridge.data.materials.append(white)
bridge.parent = rig
groups = {name: bridge.vertex_groups.new(name=name) for name in ('Shoulder', 'Elbow')}
for index, vertex in enumerate(bridge.data.vertices):
    weight = max(0, min(1, (vertex.co.z - 1.3) / .4))
    if weight < 1:
        groups['Shoulder'].add([index], 1 - weight, 'REPLACE')
    if weight > 0:
        groups['Elbow'].add([index], weight, 'REPLACE')
bridge.modifiers.new('Armature', 'ARMATURE').object = rig

rig.animation_data_create()
clips = {
    'Idle': [(-8, 12), (0, 20), (8, 12), (0, 4), (-8, 12)],
    'Reach': [(-42, 65), (-35, 40), (-28, 18), (-35, 40), (-42, 65)],
    'Fold': [(35, 115), (42, 125), (35, 135), (28, 125), (35, 115)],
}
for name, samples in clips.items():
    action = bpy.data.actions.new(name)
    action.use_fake_user = True
    rig.animation_data.action = action
    for frame, (shoulder_angle, elbow_angle) in zip((1, 13, 25, 37, 49), samples):
        for bone_name, angle in (('Shoulder', shoulder_angle), ('Elbow', elbow_angle)):
            bone = rig.pose.bones[bone_name]
            bone.rotation_quaternion = Quaternion((0, 0, 1), math.radians(angle))
            bone.keyframe_insert(data_path='rotation_quaternion', frame=frame, group=bone_name)
        rig.pose.bones['ToolSocket'].keyframe_insert(data_path='rotation_quaternion', frame=frame, group='ToolSocket')
    # Keep named NLA strips for authoring; the exporter below bakes the action takes.
    track = rig.animation_data.nla_tracks.new()
    track.name = name
    strip = track.strips.new(name, 1, action)
    strip.name = name
    track.mute = True
rig.animation_data.action = None
for bone in rig.pose.bones:
    bone.rotation_quaternion = Quaternion()
scene.frame_set(1)
bpy.context.view_layer.update()

export_objects = [obj for obj in scene.objects if obj.type in {'MESH', 'ARMATURE'}]
bpy.ops.object.select_all(action='DESELECT')
for obj in export_objects:
    obj.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.export_scene.fbx(
    filepath=str(OUTPUT), use_selection=True, object_types={'ARMATURE', 'MESH'},
    axis_forward='-Z', axis_up='Y', global_scale=1,
    apply_unit_scale=True, apply_scale_options='FBX_SCALE_ALL',
    bake_space_transform=False, use_mesh_modifiers=True, use_triangles=True,
    add_leaf_bones=False, use_armature_deform_only=False,
    bake_anim=True, bake_anim_use_nla_strips=False, bake_anim_use_all_actions=True,
    bake_anim_force_startend_keying=True, bake_anim_step=1,
    bake_anim_simplify_factor=0, path_mode='COPY', embed_textures=True)

rig.animation_data.action = bpy.data.actions['Idle']
scene.frame_set(13)

samples = []
for name in clips:
    rig.animation_data.action = bpy.data.actions[name]
    scene.frame_set(13)
    bpy.context.view_layer.update()
    point = (rig.matrix_world @ rig.pose.bones['ToolSocket'].matrix).translation
    low, high = [float('inf')] * 3, [float('-inf')] * 3
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for obj in export_objects:
        if obj.type != 'MESH':
            continue
        evaluated = obj.evaluated_get(depsgraph)
        evaluated_mesh = evaluated.to_mesh()
        for vertex in evaluated_mesh.vertices:
            world = evaluated.matrix_world @ vertex.co
            # Export rotates Blender to Y-up, then AssimpLoader's FBX basis reflects X.
            coordinates = (-world.x, world.z, -world.y)
            low = [min(a, b) for a, b in zip(low, coordinates)]
            high = [max(a, b) for a, b in zip(high, coordinates)]
        evaluated.to_mesh_clear()
    samples.append({'clip': name, 'seconds': .5, 'socket': [-point.x, point.z, -point.y],
                    'geometry_min': low, 'geometry_max': high})
REFERENCE.parent.mkdir(parents=True, exist_ok=True)
REFERENCE.write_text(json.dumps({'coordinates': 'Blender (x,y,z) -> FBX Y-up -> AssimpLoader (-x,z,-y)',
                                  'samples': samples}, indent=2) + '\n', encoding='utf-8')
rig.animation_data.action = bpy.data.actions['Idle']
scene.frame_set(13)

# Source file includes a camera and lights, excluded from the exported FBX selection.
bpy.ops.object.camera_add(location=(5, -8, 4))
camera = bpy.context.object
camera.name = 'ProbeCamera'
camera.rotation_euler = (Vector((0, 0, 1.35)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 3.8
scene.camera = camera
for name, location, energy, size in [('Key', (2, -4, 6), 750, 4), ('Fill', (-4, -1, 3), 500, 3)]:
    bpy.ops.object.light_add(type='AREA', location=location)
    light = bpy.context.object
    light.name = name
    light.data.energy, light.data.shape, light.data.size = energy, 'DISK', size
    light.rotation_euler = (Vector((0, 0, 1.3)) - light.location).to_track_quat('-Z', 'Y').to_euler()
scene.world.color = (.22, .22, .22)
scene.render.resolution_x, scene.render.resolution_y = 720, 720
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(PREVIEW)
try:
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 24
except TypeError:
    pass
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE))
bpy.ops.render.render(write_still=True)
MANIFEST.write_text(json.dumps({
    'origin': 'Original fixture authored for AssimpLoader; no third-party assets.',
    'blender': bpy.app.version_string,
    'fbx': str(OUTPUT.relative_to(ROOT)),
    'source': str(SOURCE.relative_to(ROOT)),
    'texture': 'Original 64x64 RGBA checker, embedded in FBX',
    'fps': scene.render.fps,
    'clips': list(clips),
    'deform_bones': ['Shoulder', 'Elbow'],
    'attachment_node': 'ToolSocket',
    'coordinate_export': {'forward': '-Z', 'up': 'Y', 'scale': 'FBX_SCALE_ALL'},
    'vertices': sum(len(obj.data.vertices) for obj in export_objects if obj.type == 'MESH'),
}, indent=2) + '\n', encoding='utf-8')
print('ANIMATION_PROBE_EXPORTED', OUTPUT, 'bytes', OUTPUT.stat().st_size)
