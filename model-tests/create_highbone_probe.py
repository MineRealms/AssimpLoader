"""Original 256-bone/Morph acceptance fixture. Run in a background factory Blender."""
import json
import math
from pathlib import Path

import bpy
from mathutils import Quaternion, Vector

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'src/showcase/resources/assets/assimploader/models/validation/highbone_probe.glb'
SOURCE = ROOT / 'model-tests/highbone_probe.blend'
REFERENCE = ROOT / 'src/test/resources/fixtures/highbone_reference.json'
scene = bpy.context.scene
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene.render.fps = 24
scene.frame_start, scene.frame_end = 1, 49

armature = bpy.data.armatures.new('HighBoneSkeleton')
rig = bpy.data.objects.new('HighBoneRig', armature)
scene.collection.objects.link(rig)
bpy.context.view_layer.objects.active = rig
rig.select_set(True)
bpy.ops.object.mode_set(mode='EDIT')
centers = []
for index in range(256):
    column, row = index % 16, index // 16
    center = Vector(((column - 7.5) * .23, 0, .16 + row * .23))
    centers.append(center)
    bone = armature.edit_bones.new(f'B{index:03}')
    bone.head, bone.tail = center, center + Vector((0, 0, .12))
bpy.ops.object.mode_set(mode='OBJECT')

vertices, faces = [], []
corners = [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
           (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]
cube_faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4),
              (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
for index, center in enumerate(centers):
    vertices.extend([center + Vector((x * .085, y * .065, z * .085)) for x, y, z in corners])
    faces.extend([tuple(index * 8 + corner for corner in face) for face in cube_faces])
mesh = bpy.data.meshes.new('HighBoneTiles')
mesh.from_pydata(vertices, [], faces)
mesh.update()
uv = mesh.uv_layers.new(name='UVMap')
for polygon in mesh.polygons:
    tile = polygon.index // 6
    column, row = tile % 16, tile // 16
    for loop in polygon.loop_indices:
        uv.data[loop].uv = ((column + .5) / 16, (row + .5) / 16)
body = bpy.data.objects.new('HighBoneTiles', mesh)
scene.collection.objects.link(body)
body.parent = rig
for index in range(256):
    group = body.vertex_groups.new(name=f'B{index:03}')
    group.add(list(range(index * 8, index * 8 + 8)), 1, 'REPLACE')
body.modifiers.new('Skin', 'ARMATURE').object = rig

image = bpy.data.images.new('HighBoneChecker', width=64, height=64)
pixels = []
for y in range(64):
    for x in range(64):
        row, column = y // 4, x // 4
        if row in (0, 15) or column in (0, 15):
            color = (.95, .72, .08, 1)
        elif (row + column) % 2:
            color = (.05, .63, .52, 1)
        else:
            color = (.85, .92, .95, 1)
        pixels.extend(color)
image.pixels.foreach_set(pixels)
image.filepath_raw = str(ROOT / 'model-tests/highbone_checker.png')
image.file_format = 'PNG'
image.save()
image.pack()
material = bpy.data.materials.new('HighBoneChecker')
material.use_nodes = True
principled = next(node for node in material.node_tree.nodes if node.type == 'BSDF_PRINCIPLED')
principled.inputs['Roughness'].default_value = .8
texture = material.node_tree.nodes.new('ShaderNodeTexImage')
texture.image, texture.interpolation = image, 'Closest'
material.node_tree.links.new(texture.outputs['Color'], principled.inputs['Base Color'])
mesh.materials.append(material)

rig.animation_data_create()
wave = bpy.data.actions.new('Wave')
wave.use_fake_user = True
rig.animation_data.action = wave
for frame in range(1, 50, 3):
    seconds = (frame - 1) / 24
    for index, bone in enumerate(rig.pose.bones):
        phase = seconds * math.tau / 2 + (index % 16) * .32 + (index // 16) * .2
        bone.rotation_mode = 'QUATERNION'
        bone.rotation_quaternion = Quaternion((0, 0, 1), math.sin(phase) * .28)
        bone.location = (math.cos(phase) * .025, math.sin(phase) * .055, 0)
        bone.keyframe_insert('rotation_quaternion', frame=frame, group=bone.name)
        bone.keyframe_insert('location', frame=frame, group=bone.name)

body.shape_key_add(name='Basis')
spread = body.shape_key_add(name='Spread')
for index, vertex in enumerate(spread.data):
    tile = index // 8
    column, row = tile % 16, tile // 16
    delta = vertex.co - centers[tile]
    vertex.co.x += (column - 7.5) * .025 + delta.x * .3
    vertex.co.z += .16 * math.sin(column * .6) + (row / 15) * .12
    vertex.co.y += .04 * ((column + row) % 2)
for frame, value in ((1, 0), (25, 1), (49, 0)):
    spread.value = value
    spread.keyframe_insert('value', frame=frame)
keys = body.data.shape_keys
keys.animation_data.action.name = 'MorphPulse'
keys.animation_data.action.use_fake_user = True
for action in (wave, keys.animation_data.action):
    for layer in action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    for point in curve.keyframe_points:
                        point.interpolation = 'LINEAR'

# Evaluated reference centroids exercise every bone, including indices 64..255.
samples = []
keys.animation_data.action = None
for frame, weight in ((1, 0), (13, .5), (25, 1), (37, .25)):
    scene.frame_set(frame)
    spread.value = weight
    bpy.context.view_layer.update()
    evaluated = body.evaluated_get(bpy.context.evaluated_depsgraph_get())
    evaluated_mesh = evaluated.to_mesh()
    points = [evaluated.matrix_world @ vertex.co for vertex in evaluated_mesh.vertices]
    to_runtime = lambda point: [point.x, point.z, point.y]
    samples.append({'seconds': (frame - 1) / 24, 'weight': weight,
                    'centroids': {f'B{i:03}': to_runtime(sum(points[i*8:i*8+8], Vector()) / 8) for i in range(256)},
                    'nodes': {f'B{i:03}': to_runtime((rig.matrix_world @ rig.pose.bones[f'B{i:03}'].matrix).translation) for i in (0, 63, 64, 127, 128, 191, 192, 255)},
                    'min': [min(to_runtime(p)[i] for p in points) for i in range(3)],
                    'max': [max(to_runtime(p)[i] for p in points) for i in range(3)]})
    evaluated.to_mesh_clear()
REFERENCE.parent.mkdir(parents=True, exist_ok=True)
REFERENCE.write_text(json.dumps({'coordinates': 'Blender x,y,z -> runtime x,z,y', 'samples': samples}, indent=2) + '\n', encoding='utf-8')
keys.animation_data.action = bpy.data.actions['MorphPulse']
scene.frame_set(1)
bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
body.select_set(True)
bpy.context.view_layer.objects.active = rig
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.export_scene.gltf(filepath=str(OUTPUT), export_format='GLB', use_selection=True,
                          export_animation_mode='ACTIONS', export_animations=True, export_anim_slide_to_zero=True,
                          export_skins=True, export_morph=True, export_morph_normal=True, export_morph_animation=True,
                          export_optimize_animation_size=False, export_apply=False)
definition = {'version': 1, 'model': 'assimploader:models/validation/highbone_probe.glb', 'initial': 'wave',
              'states': {'wave': {'clip': 'Wave', 'loop': True, 'transition': 0},
                         'pulse': {'clip': 'MorphPulse', 'loop': True, 'transition': 0}}}
OUTPUT.with_suffix('.instance.json').write_text(json.dumps(definition, indent=2) + '\n', encoding='utf-8')
scene.frame_set(13)
camera_data = bpy.data.cameras.new('HighBoneCamera')
camera = bpy.data.objects.new('HighBoneCamera', camera_data)
scene.collection.objects.link(camera)
camera.location = (4, -8, 4)
camera.rotation_euler = (Vector((0, 0, 1.9)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.type, camera.data.ortho_scale = 'ORTHO', 5.7
scene.camera = camera
for location, energy in [((0, -5, 7), 1100), ((-4, -2, 3), 500)]:
    data = bpy.data.lights.new('HighBoneLight', 'AREA')
    light = bpy.data.objects.new(data.name, data)
    scene.collection.objects.link(light)
    light.location, data.energy, data.size = location, energy, 5
    light.rotation_euler = (Vector((0, 0, 1.8)) - light.location).to_track_quat('-Z', 'Y').to_euler()
scene.world.color = (.18, .18, .18)
scene.render.resolution_x, scene.render.resolution_y = 768, 768
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(ROOT / 'model-tests/highbone_probe.png')
try:
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 12
except TypeError:
    pass
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE))
bpy.ops.render.render(write_still=True)
(ROOT / 'model-tests/highbone_probe.json').write_text(json.dumps({
    'origin': 'Original AssimpLoader fixture, no third-party assets', 'blender': bpy.app.version_string,
    'bones': 256, 'weighted_bones': 256, 'meshes': 1, 'source_vertices': len(vertices),
    'morph_targets': ['Spread'], 'clips': ['Wave', 'MorphPulse'], 'fps': scene.render.fps,
    'samples': len(samples), 'centroid_checks_per_sample': 256,
    'source': str(SOURCE.relative_to(ROOT)), 'gltf': str(OUTPUT.relative_to(ROOT)),
    'command': 'blender --background --factory-startup --python model-tests/create_highbone_probe.py'
}, indent=2) + '\n', encoding='utf-8')
print('HIGHBONE_PROBE_EXPORTED', OUTPUT, OUTPUT.stat().st_size)
