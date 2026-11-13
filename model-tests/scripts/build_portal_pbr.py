"""Build a small, original portal asset with authored UV PBR textures in Blender.

Run with Blender's --python option or execute this file in the live Blender.
All output paths are relative to this repository unless --output is supplied.
"""

import argparse
import json
import math
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[2]
arguments = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT / 'model-tests/portal')
parser.add_argument('--texture-size', type=int, default=512)
parser.add_argument('--skip-render', action='store_true')
OPTIONS, _ = parser.parse_known_args(arguments)
OUTPUT = OPTIONS.output.resolve()
OUTPUT.mkdir(parents=True, exist_ok=True)
(OUTPUT / 'textures').mkdir(exist_ok=True)
SIZE = OPTIONS.texture_size

PREFIX = 'PortalPBR_'
old_scene = bpy.context.window.scene
scene = bpy.data.scenes.get('AssimpLoader | PBR Portal')
if old_scene == scene:
    old_scene = next((item for item in bpy.data.scenes if item != scene), None)
    if old_scene is not None:
        bpy.context.window.scene = old_scene
if scene is not None:
    for obj in list(scene.objects):
        mesh = obj.data if obj.type == 'MESH' else None
        bpy.data.objects.remove(obj, do_unlink=True)
        if mesh is not None and mesh.users == 0:
            bpy.data.meshes.remove(mesh)
    bpy.data.scenes.remove(scene)
for mat in list(bpy.data.materials):
    if mat.name.startswith(PREFIX) and not any(mat in obj.data.materials[:]
            for obj in bpy.data.objects if obj.type == 'MESH' and obj.users_scene):
        bpy.data.materials.remove(mat, do_unlink=True)
for img in list(bpy.data.images):
    if img.name.startswith(PREFIX) and img.users == 0:
        bpy.data.images.remove(img)
scene = bpy.data.scenes.new('AssimpLoader | PBR Portal')
bpy.context.window.scene = scene
scene.unit_settings.system = 'METRIC'
scene.unit_settings.scale_length = 1.0
asset_collection = bpy.data.collections.new('PortalPBR | Exported Parts')
scene.collection.children.link(asset_collection)
stage_collection = bpy.data.collections.new('PortalPBR | Preview Only')
scene.collection.children.link(stage_collection)
parts = []
textures = []
materials = []


def enum_value(owner, prop, candidates):
    available = {item.identifier for item in owner.bl_rna.properties[prop].enum_items}
    return next(value for value in candidates if value in available)


def rectangle(x, y, cx, cy, width, height, bevel=0.01):
    distance = np.maximum(np.abs(x - cx) - width / 2,
                          np.abs(y - cy) - height / 2)
    return np.clip((bevel - distance) / max(bevel, 0.0001), 0, 1)


y, x = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
x = (x + 0.5) / SIZE
y = (y + 0.5) / SIZE
rng = np.random.default_rng(49317)
grain = rng.uniform(-1, 1, (SIZE, SIZE)).astype(np.float32)


def make_image(name, rgba, color=False):
    full_name = PREFIX + name
    old_image = bpy.data.images.get(full_name)
    if old_image and old_image.users == 0:
        bpy.data.images.remove(old_image)
    img = bpy.data.images.new(full_name, width=SIZE, height=SIZE, alpha=True)
    img.colorspace_settings.name = 'sRGB' if color else 'Non-Color'
    img.pixels.foreach_set(np.asarray(rgba, dtype=np.float32).reshape(-1))
    img.filepath_raw = str(OUTPUT / 'textures' / (name + '.png'))
    img.file_format = enum_value(img, 'file_format', ['PNG'])
    img.save()
    img.pack()
    textures.append(img)
    return img


def rgba(rgb, alpha=1.0):
    out = np.ones((SIZE, SIZE, 4), dtype=np.float32)
    out[:, :, :3] = rgb
    out[:, :, 3] = alpha
    return out


def scalar_image(name, value):
    values = np.broadcast_to(value, (SIZE, SIZE))
    return make_image(name, rgba(np.repeat(values[:, :, None], 3, axis=2)))


def surface_textures(name, color, roughness, metallic, panel=True, alpha=1.0):
    height = np.zeros((SIZE, SIZE), dtype=np.float32)
    base = np.zeros((SIZE, SIZE, 3), dtype=np.float32)
    base[:] = color
    base += grain[:, :, None] * 0.006
    rough = np.full((SIZE, SIZE), roughness, dtype=np.float32) + grain * 0.022
    metal = np.full((SIZE, SIZE), metallic, dtype=np.float32)
    if panel:
        raised = rectangle(x, y, 0.5, 0.5, 0.87, 0.87, 0.012)
        border = rectangle(x, y, 0.5, 0.5, 0.94, 0.94, 0.008) - raised
        height += raised * 0.018 - border * 0.009
        base *= (0.63 + raised[:, :, None] * 0.37)
        # Deliberately asymmetric channels make a flipped normal Y visible.
        channel = rectangle(x, y, 0.33, 0.64, 0.38, 0.027, 0.012)
        channel += rectangle(x, y, 0.53, 0.745, 0.027, 0.22, 0.012)
        diagonal_distance = np.abs(y - (0.33 + x * 0.36))
        diagonal = np.clip((0.025 - diagonal_distance) / 0.010, 0, 1)
        diagonal *= rectangle(x, y, 0.56, 0.53, 0.50, 0.28, 0.012)
        height -= channel * 0.024 + diagonal * 0.020
        base *= (1 - np.clip(channel + diagonal, 0, 1)[:, :, None] * 0.45)
        for sx, sy in [(0.12, 0.12), (0.87, 0.12), (0.12, 0.87), (0.87, 0.87)]:
            distance = np.sqrt((x - sx) ** 2 + (y - sy) ** 2)
            screw = np.clip((0.034 - distance) / 0.008, 0, 1)
            slot = np.clip((0.005 - np.abs((x - sx) * 0.6 + y - sy)) / 0.003, 0, 1) * screw
            height += screw * 0.015 - slot * 0.024
            base = base * (1 - screw[:, :, None] * 0.4) + screw[:, :, None] * 0.035
            base *= (1 - slot[:, :, None] * 0.6)
            metal = np.maximum(metal, screw * 0.95)
            rough -= screw * 0.13
        marks = rectangle(x, y, 0.39, 0.20, 0.15, 0.012, 0.003)
        marks += rectangle(x, y, 0.55, 0.20, 0.045, 0.012, 0.003)
        base += marks[:, :, None] * np.array([0.13, 0.16, 0.14])
    height += grain * 0.00007
    dy, dx = np.gradient(height)
    normal = np.dstack((-dx * SIZE * 1.5, -dy * SIZE * 1.5, np.ones_like(height)))
    normal /= np.linalg.norm(normal, axis=2, keepdims=True)
    # Blender/glTF tangent normals: R=+U, G=+V, B=outward (OpenGL convention).
    normal = normal * 0.5 + 0.5
    return {
        'albedo': make_image(name + '_albedo', rgba(np.clip(base, 0, 1), alpha), True),
        'normal': make_image(name + '_normal_opengl', rgba(normal)),
        'roughness': scalar_image(name + '_roughness', np.clip(rough, 0.04, 1)),
        'metallic': scalar_image(name + '_metallic', np.clip(metal, 0, 1)),
    }


def material(name, maps, alpha=1.0, transmission=0.0, emission=None):
    mat = bpy.data.materials.new(PREFIX + name)
    mat.use_nodes = True
    mat.diffuse_color = (0.3, 0.4, 0.45, alpha)
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    bsdf = next(node for node in nodes if node.type == 'BSDF_PRINCIPLED')
    bsdf.inputs['Base Color'].default_value = (1, 1, 1, 1)
    bsdf.inputs['Metallic'].default_value = 1
    bsdf.inputs['Roughness'].default_value = 1
    bsdf.inputs['Alpha'].default_value = alpha
    if 'Transmission Weight' in bsdf.inputs:
        bsdf.inputs['Transmission Weight'].default_value = transmission
    bsdf.inputs['IOR'].default_value = 1.45
    for socket, key in [('Base Color', 'albedo'), ('Roughness', 'roughness'), ('Metallic', 'metallic')]:
        tex = nodes.new('ShaderNodeTexImage')
        tex.image = maps[key]
        tex.label = key
        links.new(tex.outputs['Color'], bsdf.inputs[socket])
    normal_tex = nodes.new('ShaderNodeTexImage')
    normal_tex.image = maps['normal']
    normal = nodes.new('ShaderNodeNormalMap')
    normal.inputs['Strength'].default_value = 1.0
    links.new(normal_tex.outputs['Color'], normal.inputs['Color'])
    links.new(normal.outputs['Normal'], bsdf.inputs['Normal'])
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*emission, 1)
        bsdf.inputs['Emission Strength'].default_value = 2.0
    if alpha < 1:
        if 'surface_render_method' in mat.bl_rna.properties:
            mat.surface_render_method = enum_value(mat, 'surface_render_method', ['DITHERED', 'BLENDED'])
        elif 'blend_method' in mat.bl_rna.properties:
            mat.blend_method = enum_value(mat, 'blend_method', ['BLEND', 'HASHED'])
        mat.use_backface_culling = False
    materials.append(mat)
    return mat


frame_maps = surface_textures('frame', (0.105, 0.15, 0.17), 0.48, 1.0)
ceramic_maps = surface_textures('ceramic', (0.56, 0.62, 0.60), 0.62, 0.0)
trim_maps = surface_textures('polished', (0.58, 0.66, 0.69), 0.13, 1.0, False)
copper_maps = surface_textures('copper', (0.63, 0.27, 0.10), 0.24, 1.0, False)
rubber_maps = surface_textures('rubber', (0.025, 0.035, 0.035), 0.92, 0.0, False)
glass_maps = surface_textures('glass', (0.29, 0.82, 0.78), 0.08, 0.0, False, 1.0)
light_maps = surface_textures('light', (0.09, 0.76, 0.70), 0.24, 0.0, False)
energy_maps = surface_textures('energy', (0.035, 0.30, 0.38), 0.30, 0.0, False, 1.0)
frame_mat = material('Titanium_Frame', frame_maps)
ceramic_mat = material('Ceramic_Armor', ceramic_maps)
trim_mat = material('Polished_Steel', trim_maps)
copper_mat = material('Copper_Coils', copper_maps)
rubber_mat = material('Rubber_Insulator', rubber_maps)
glass_mat = material('Transparent_Glass', glass_maps, alpha=0.24, transmission=0.6)
light_mat = material('Cyan_Light', light_maps, emission=(0.04, 0.8, 0.65))
energy_mat = material('Portal_Energy', energy_maps, alpha=0.18, emission=(0.02, 0.24, 0.30))


root = bpy.data.objects.new('PortalPBR_ROOT_BottomCenter', None)
asset_collection.objects.link(root)
root.empty_display_size = 0.3
root['units'] = 'meters; Blender Z up, exports Y up'
root['pivot'] = 'bottom center at (0, 0, 0)'


def register(obj, name, mat, bevel=0):
    obj.name = PREFIX + name
    obj.data.name = obj.name + '_Mesh'
    for collection in list(obj.users_collection):
        collection.objects.unlink(obj)
    asset_collection.objects.link(obj)
    obj.parent = root
    obj.data.materials.append(mat)
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    if bevel:
        mod = obj.modifiers.new('Small manufactured edge', 'BEVEL')
        mod.width = bevel
        mod.segments = 1
        bpy.ops.object.modifier_apply(modifier=mod.name)
    if not obj.data.uv_layers:
        obj.data.uv_layers.new(name='UVMap')
    uv_data = obj.data.uv_layers.active.data
    for poly in obj.data.polygons:
        axes = sorted(range(3), key=lambda axis: abs(poly.normal[axis]))[:2]
        coords = [obj.data.vertices[obj.data.loops[loop].vertex_index].co for loop in poly.loop_indices]
        lo = [min(co[axis] for co in coords) for axis in axes]
        hi = [max(co[axis] for co in coords) for axis in axes]
        for loop, co in zip(poly.loop_indices, coords):
            uv_data[loop].uv = tuple((co[axis] - lo[i]) / max(hi[i] - lo[i], 0.001)
                                    for i, axis in enumerate(axes))
    parts.append(obj)
    return obj


def box(name, location, dimensions, mat, bevel=0.012):
    bpy.ops.mesh.primitive_cube_add(size=1, location=location)
    obj = bpy.context.object
    obj.dimensions = dimensions
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return register(obj, name, mat, bevel)


def cylinder(name, location, radius, depth, mat, vertices=12):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth,
                                      location=location)
    return register(bpy.context.object, name, mat, 0)


def ring_segment(name, start, end, inner_start, inner_end, front, back, mat):
    points = [start, end, inner_end, inner_start]
    vertices = [(p[0], depth, p[1]) for depth in [front, back] for p in points]
    faces = [(3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4),
             (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    mesh = bpy.data.meshes.new(PREFIX + name)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(PREFIX + name, mesh)
    asset_collection.objects.link(obj)
    register(obj, name, mat, 0.014)
    return obj


outer = [(-0.88, 0.28), (0.88, 0.28), (1.43, 0.83), (1.43, 3.23),
         (0.88, 3.83), (-0.88, 3.83), (-1.43, 3.23), (-1.43, 0.83)]
inner = [(-0.75, 0.58), (0.75, 0.58), (1.12, 0.96), (1.12, 3.10),
         (0.75, 3.52), (-0.75, 3.52), (-1.12, 3.10), (-1.12, 0.96)]
trim_inner = [(px * 0.953, 2.05 + (pz - 2.05) * 0.972) for px, pz in inner]
light_inner = [(px * 0.970, 2.05 + (pz - 2.05) * 0.984) for px, pz in trim_inner]
for index in range(8):
    next_index = (index + 1) % 8
    ring_segment('Frame_%02d' % index, outer[index], outer[next_index],
                 inner[index], inner[next_index], -0.25, 0.25, frame_mat)
    ring_segment('Inner_Chrome_%02d' % index, inner[index], inner[next_index],
                 trim_inner[index], trim_inner[next_index], -0.28, 0.20, trim_mat)
    ring_segment('Inner_Light_%02d' % index, trim_inner[index], trim_inner[next_index],
                 light_inner[index], light_inner[next_index], -0.23, 0.06, light_mat)

box('Ground_Plinth', (0, 0, 0.13), (3.45, 1.05, 0.26), frame_mat, 0.035)
box('Front_Step', (0, -0.49, 0.095), (2.18, 0.42, 0.19), rubber_mat, 0.025)
box('Sill_Bright_Edge', (0, -0.72, 0.12), (1.92, 0.035, 0.08), trim_mat, 0.008)
for side, direction in [('L', -1), ('R', 1)]:
    box('Foot_' + side, (direction * 1.48, 0.03, 0.23), (0.52, 0.90, 0.24), ceramic_mat, 0.025)
    for i, z in enumerate([0.92, 2.80]):
        box('Ceramic_Shoulder_%s_%d' % (side, i), (direction * 1.435, -0.30, z),
            (0.36, 0.18, 0.63), ceramic_mat, 0.024)
        box('Shoulder_Trim_%s_%d' % (side, i), (direction * 1.44, -0.405, z),
            (0.40, 0.035, 0.065), trim_mat, 0.005)
    box('Pod_Back_' + side, (direction * 1.47, 0.01, 1.86), (0.47, 0.46, 1.18), rubber_mat, 0.025)
    for i, z in enumerate([1.27, 2.45]):
        box('Pod_Cap_%s_%d' % (side, i), (direction * 1.47, -0.23, z),
            (0.51, 0.55, 0.15), frame_mat, 0.020)
    for i, xx in enumerate([-0.19, 0.19]):
        box('Window_Rail_%s_%d' % (side, i), (direction * 1.47 + xx, -0.405, 1.86),
            (0.048, 0.12, 1.05), trim_mat, 0.005)
    cylinder('Reactor_Core_' + side, (direction * 1.47, -0.27, 1.86), 0.060, 1.03, light_mat)
    for i in range(8):
        cylinder('Copper_Coil_%s_%02d' % (side, i),
                 (direction * 1.47, -0.27, 1.39 + i * 0.134), 0.142, 0.061, copper_mat)
    # One plane avoids doubled absorption from the front and back of a glass box.
    mesh = bpy.data.meshes.new(PREFIX + 'Glass_Window_' + side)
    xx = direction * 1.47
    mesh.from_pydata([(xx - 0.163, -0.485, 1.35), (xx + 0.163, -0.485, 1.35),
                     (xx + 0.163, -0.485, 2.37), (xx - 0.163, -0.485, 2.37)], [], [(0, 1, 2, 3)])
    mesh.update()
    glass_obj = bpy.data.objects.new(PREFIX + 'Glass_Window_' + side, mesh)
    asset_collection.objects.link(glass_obj)
    register(glass_obj, 'Glass_Window_' + side, glass_mat)

box('Top_Armor', (0, -0.29, 3.70), (1.31, 0.15, 0.28), ceramic_mat, 0.025)
box('Top_Chrome', (0, -0.40, 3.86), (1.68, 0.07, 0.075), trim_mat, 0.008)
for i, xx in enumerate([-0.42, -0.21, 0, 0.21, 0.42]):
    box('Top_Status_%d' % i, (xx, -0.382, 3.69), (0.10, 0.035, 0.040), light_mat, 0.006)
for i, xx in enumerate([-0.57, 0.57]):
    box('Sill_Detail_%d' % i, (xx, -0.31, 0.44), (0.26, 0.14, 0.19), copper_mat, 0.014)

energy_mesh = bpy.data.meshes.new(PREFIX + 'Energy_Membrane')
energy_mesh.from_pydata([(px, 0.03, pz) for px, pz in light_inner], [], [tuple(range(8))])
energy_mesh.update()
energy_obj = bpy.data.objects.new(PREFIX + 'Energy_Membrane', energy_mesh)
asset_collection.objects.link(energy_obj)
register(energy_obj, 'Energy_Membrane', energy_mat)


def move_to_stage(obj):
    for collection in list(obj.users_collection):
        collection.objects.unlink(obj)
    stage_collection.objects.link(obj)


bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, -0.012))
floor = bpy.context.object
floor.name = 'Portal Preview Floor (not exported)'
move_to_stage(floor)
floor_mat = bpy.data.materials.new(PREFIX + 'Stage_Ground')
floor_mat.use_nodes = True
floor_bsdf = next(n for n in floor_mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
floor_bsdf.inputs['Base Color'].default_value = (0.075, 0.090, 0.095, 1)
floor_bsdf.inputs['Roughness'].default_value = 0.75
floor.data.materials.append(floor_mat)
world = bpy.data.worlds.new(PREFIX + 'Studio_World')
world.use_nodes = True
background = next(n for n in world.node_tree.nodes if n.type == 'BACKGROUND')
background.inputs['Color'].default_value = (0.20, 0.25, 0.28, 1)
background.inputs['Strength'].default_value = 0.45
scene.world = world


def area_light(name, location, color, energy, size):
    data = bpy.data.lights.new(name, 'AREA')
    data.color = color
    data.energy = energy
    data.shape = enum_value(data, 'shape', ['DISK', 'SQUARE'])
    data.size = size
    obj = bpy.data.objects.new(name, data)
    stage_collection.objects.link(obj)
    obj.location = location
    obj.rotation_euler = (Vector((0, 0, 1.9)) - obj.location).to_track_quat('-Z', 'Y').to_euler()


area_light('Portal Studio Key', (-3.5, -4.5, 6.5), (0.83, 0.93, 1.0), 1100, 4.0)
area_light('Portal Studio Warm Fill', (4.0, -1.8, 3.5), (1.0, 0.73, 0.42), 1000, 3.0)
area_light('Portal Studio Rim', (0.5, 2.8, 5.0), (0.40, 1.0, 0.86), 1400, 2.5)
camera_data = bpy.data.cameras.new('Portal Studio Camera')
camera = bpy.data.objects.new('Portal Studio Camera', camera_data)
stage_collection.objects.link(camera)
camera.location = (5.2, -8.5, 4.3)
camera.rotation_euler = (Vector((0, 0, 1.92)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera_data.type = enum_value(camera_data, 'type', ['ORTHO'])
camera_data.ortho_scale = 5.05
scene.camera = camera
scene.render.resolution_x = 1280
scene.render.resolution_y = 1280
scene.render.resolution_percentage = 100
scene.render.engine = enum_value(scene.render, 'engine', ['BLENDER_EEVEE_NEXT', 'BLENDER_EEVEE'])
scene.render.image_settings.file_format = enum_value(scene.render.image_settings, 'file_format', ['PNG'])
scene.render.filepath = str(OUTPUT / 'portal_pbr_preview.png')
scene.render.film_transparent = False

bpy.ops.object.select_all(action='DESELECT')
root.select_set(True)
for obj in parts:
    obj.select_set(True)
bpy.context.view_layer.objects.active = root

# Only the named asset collection is selected; lights, camera and floor never export.
bpy.ops.export_scene.fbx(filepath=str(OUTPUT / 'portal_pbr.fbx'), use_selection=True,
                         object_types={'MESH', 'EMPTY'}, axis_forward='-Z', axis_up='Y',
                         apply_unit_scale=True, apply_scale_options='FBX_SCALE_NONE',
                         bake_space_transform=False, use_mesh_modifiers=True,
                         mesh_smooth_type='FACE', use_triangles=True, add_leaf_bones=False,
                         bake_anim=False, path_mode='COPY', embed_textures=True)
bpy.ops.export_scene.gltf(filepath=str(OUTPUT / 'portal_pbr.glb'), export_format='GLB',
                          use_selection=True, use_active_scene=True, export_animations=False,
                          export_materials='EXPORT', export_apply=True)

triangles = 0
for obj in parts:
    obj.data.calc_loop_triangles()
    triangles += len(obj.data.loop_triangles)
world_coords = [obj.matrix_world @ Vector(corner) for obj in parts for corner in obj.bound_box]
bounds_min = [min(point[axis] for point in world_coords) for axis in range(3)]
bounds_max = [max(point[axis] for point in world_coords) for axis in range(3)]
manifest = {
    'name': 'Aperture Industrial Portal',
    'author': 'Original procedural asset made for AssimpLoader verification',
    'units': 'meters', 'source_up': 'Z', 'export_up': 'Y',
    'root_pivot': [0, 0, 0], 'root_name': root.name,
    'bounds_min_blender': bounds_min, 'bounds_max_blender': bounds_max,
    'dimensions_blender': [hi - lo for lo, hi in zip(bounds_min, bounds_max)],
    'mesh_parts': len(parts), 'triangles': triangles,
    'texture_size': SIZE, 'normal_convention': 'OpenGL tangent +Y',
    'normal_design': 'Asymmetric L channel, diagonal recess, slotted screw heads and fine grain',
    'glass': {'alpha_factor': 0.24, 'alpha_texture': 1.0, 'transmission': 0.6,
              'mesh': 'two single-sided windows; double-sided material',
              'inspection': 'Copper coil fins and luminous core are physically behind each window'},
    'energy': {'alpha_factor': 0.18, 'description': 'thin octagonal membrane above entity alpha cutoff'},
    'materials': [mat.name for mat in materials],
    'parts': [obj.name for obj in parts],
    'textures': [img.filepath_raw.replace(str(OUTPUT) + '\\', '').replace(str(OUTPUT) + '/', '')
                 for img in textures],
    'license': 'CC0-1.0; original geometry and deterministic generated textures',
}
(OUTPUT / 'portal_manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
bpy.data.libraries.write(str(OUTPUT / 'portal_pbr.blend'), {scene}, fake_user=True)
if not OPTIONS.skip_render:
    bpy.ops.render.render(write_still=True)
if old_scene is not None:
    bpy.context.window.scene = old_scene
print('PORTAL_PBR_EXPORT', json.dumps({key: manifest[key] for key in
      ['mesh_parts', 'triangles', 'dimensions_blender', 'materials']}, indent=2))
print('PORTAL_PBR_OUTPUT', OUTPUT)
