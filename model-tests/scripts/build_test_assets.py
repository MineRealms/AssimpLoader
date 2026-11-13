import bpy
import bmesh
import json
import math
from pathlib import Path
from mathutils import Matrix, Vector


OUTPUT = Path(__file__).resolve().parents[1]
OUTPUT.mkdir(parents=True, exist_ok=True)
(OUTPUT / 'fbx').mkdir(exist_ok=True)
(OUTPUT / 'glb').mkdir(exist_ok=True)
(OUTPUT / 'textures').mkdir(exist_ok=True)
(OUTPUT / 'blender').mkdir(exist_ok=True)

source_root = bpy.data.objects.get('CIWS_ROOT | Y-Up')
if source_root is None:
    raise RuntimeError('The original CIWS hierarchy is not present; no user data was changed.')
if bpy.data.collections.get('AssimpLoader | Test Assets'):
    raise RuntimeError('Test collection already exists; inspect before rebuilding.')

original_active = bpy.context.view_layer.objects.active
original_selection = list(bpy.context.selected_objects)
collection = bpy.data.collections.new('AssimpLoader | Test Assets')
bpy.context.scene.collection.children.link(collection)


def material(name, color, metallic=0.0, roughness=0.5):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.diffuse_color = (*color, 1.0)
    bsdf = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    bsdf.inputs['Base Color'].default_value = (*color, 1.0)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = roughness
    return mat


def link_mesh(name, verts, faces, mats, indices=None, parent=None):
    mesh = bpy.data.meshes.new(name + '_Mesh')
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    collection.objects.link(obj)
    if parent:
        obj.parent = parent
    for mat in mats:
        mesh.materials.append(mat)
    if indices:
        for polygon, index in zip(mesh.polygons, indices):
            polygon.material_index = index
    return obj


def box(name, minimum, maximum, mats, mat_index=0, parent=None):
    x0, y0, z0 = minimum
    x1, y1, z1 = maximum
    vertices = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4),
             (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    return link_mesh(name, vertices, faces, mats, [mat_index] * 6, parent)


def empty(name, rotation=(0, 0, 0)):
    obj = bpy.data.objects.new(name, None)
    obj.empty_display_type = 'PLAIN_AXES'
    obj.empty_display_size = 0.2
    obj.rotation_euler = rotation
    collection.objects.link(obj)
    return obj


def hierarchy(root):
    return [root] + list(root.children_recursive)


def duplicate_hierarchy(original, prefix):
    mapping = {}
    for old in hierarchy(original):
        clone = old.copy()
        if old.type == 'MESH':
            clone.data = old.data.copy()
        collection.objects.link(clone)
        mapping[old] = clone
    canonical = {
        'CIWS_ROOT | Y-Up': 'ROOT',
        '01_YAW | Rotate Local Y': 'Yaw',
        '02_PITCH | Rotate Local X': 'Pitch',
        '03_ROTOR | Rotate Local Z': 'Rotor',
        'Knife_Material_ROOT': 'ROOT',
        'Knife_Material_Mesh': 'Mesh',
        'Probe_NoUV_ROOT': 'ROOT',
        'Probe_NoUV_Base': 'Base',
        'Probe_NoUV_PositiveX': 'PositiveX',
        'Probe_NoUV_PositiveZ': 'PositiveZ',
        'Probe_NoUV_Top': 'Top',
    }
    for old, clone in mapping.items():
        clone.name = prefix + '_' + canonical.get(old.name, old.name)
        clone.parent = mapping.get(old.parent)
        clone.matrix_parent_inverse = old.matrix_parent_inverse.copy()
        clone.matrix_basis = old.matrix_basis.copy()
    return mapping[original]


def atlas(name, colors):
    size = 128
    image = bpy.data.images.new(name, width=size, height=size, alpha=True)
    tile = size // 4
    pixels = []
    for y in range(size):
        for x in range(size):
            index = (y // tile) * 4 + x // tile
            color = colors[index % len(colors)]
            lx, ly = x % tile, y % tile
            shade = 0.97 if ((lx // 4 + ly // 4) % 2) else 1.0
            if lx < 2 or ly < 2 or lx >= tile - 2 or ly >= tile - 2:
                shade *= 0.83
            if index == 6 and ((lx + ly) // 6) % 2:
                shade *= 0.45
            if index == 7 and 10 <= lx <= 21 and 10 <= ly <= 21:
                shade *= 1.35
            pixels.extend([min(1.0, c * shade) for c in color] + [1.0])
    image.pixels.foreach_set(pixels)
    image.filepath_raw = str(OUTPUT / 'textures' / (name + '.png'))
    image.file_format = 'PNG'
    image.save()
    image.pack()
    return image


def apply_atlas(root, image, name):
    mat = material(name, (1.0, 1.0, 1.0), 0.15, 0.5)
    bsdf = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    texture = mat.node_tree.nodes.new('ShaderNodeTexImage')
    texture.image = image
    texture.interpolation = 'Closest'
    mat.node_tree.links.new(texture.outputs['Color'], bsdf.inputs['Base Color'])
    for obj in hierarchy(root):
        if obj.type != 'MESH':
            continue
        mesh = obj.data
        uv = mesh.uv_layers.new(name='UVMap')
        for polygon in mesh.polygons:
            tile_index = polygon.material_index % 16
            tile_x, tile_y = tile_index % 4, tile_index // 4
            normal = polygon.normal
            drop = max(range(3), key=lambda i: abs(normal[i]))
            axes = [i for i in range(3) if i != drop]
            coords = [mesh.vertices[mesh.loops[loop].vertex_index].co for loop in polygon.loop_indices]
            minimum = [min(v[i] for v in coords) for i in axes]
            spans = [max(v[i] for v in coords) - minimum[k] for k, i in enumerate(axes)]
            for loop_index, co in zip(polygon.loop_indices, coords):
                u = (co[axes[0]] - minimum[0]) / spans[0] if spans[0] > 1e-7 else 0.5
                v = (co[axes[1]] - minimum[1]) / spans[1] if spans[1] > 1e-7 else 0.5
                uv.data[loop_index].uv = ((tile_x + 0.1 + u * 0.8) / 4,
                                          (tile_y + 0.1 + v * 0.8) / 4)
            polygon.material_index = 0
        mesh.materials.clear()
        mesh.materials.append(mat)


ciws_material = duplicate_hierarchy(source_root, 'CIWS_Material')
ciws_material.location.x = -4.0
ciws_textured = duplicate_hierarchy(source_root, 'CIWS_Textured')
ciws_textured.location.x = -7.0
palette = []
for mat in bpy.data.objects['00_FIXED_BASE'].data.materials:
    node = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    palette.append(tuple(node.inputs['Base Color'].default_value[:3]))
ciws_image = atlas('ciws_palette_128', palette)
apply_atlas(ciws_textured, ciws_image, 'CIWS_Atlas_Bitmap')

knife_mats = [
    material('Knife_Steel', (0.56, 0.65, 0.70), 0.7, 0.32),
    material('Knife_Edge', (0.83, 0.89, 0.91), 0.65, 0.23),
    material('Knife_Grip', (0.045, 0.075, 0.083), 0.05, 0.75),
    material('Knife_Guard', (0.18, 0.24, 0.27), 0.5, 0.42),
    material('Knife_Pin', (0.10, 0.57, 0.58), 0.25, 0.38),
    material('Knife_Ribs', (0.075, 0.11, 0.12), 0.05, 0.72),
    material('Knife_Amber', (0.95, 0.55, 0.08), 0.2, 0.42),
    material('Knife_Stamp', (0.16, 0.26, 0.29), 0.3, 0.4),
]
knife = empty('Knife_Material_ROOT', (math.pi / 2, 0, 0))
knife['axis_convention'] = 'Local Y up, blade points +Y, blade width X, thickness Z.'
knife['origin'] = 'Grip heel center at floor; all parts use this fixed origin.'

# A solid tapered blade: a raised ridge between two beveled side strips.
sections = [(0.54, -0.105, 0.105), (0.64, -0.11, 0.11),
            (1.27, -0.11, 0.11), (1.53, -0.095, 0.042)]
verts = []
for y, left, right in sections:
    verts.extend([(left, y, 0.0), ((left + right) * 0.5, y, 0.035),
                  (right, y, 0.0), ((left + right) * 0.5, y, -0.035)])
tip = len(verts)
verts.append((-0.055, 1.73, 0.0))
faces, face_mats = [], []
for ring in range(len(sections) - 1):
    for side in range(4):
        a, b = ring * 4 + side, ring * 4 + (side + 1) % 4
        faces.append((a, b, b + 4, a + 4))
        face_mats.append(1 if side in (1, 2) else 0)
for side in range(4):
    faces.append(((len(sections) - 1) * 4 + side,
                  (len(sections) - 1) * 4 + (side + 1) % 4, tip))
    face_mats.append(1 if side in (1, 2) else 0)
faces.append((3, 2, 1, 0))
face_mats.append(0)
link_mesh('Knife_Material_Blade', verts, faces, knife_mats, face_mats, knife)
box('Knife_Material_Tang', (-0.052, 0.01, -0.027), (0.052, 0.57, 0.027), knife_mats, 3, knife)
box('Knife_Material_Grip', (-0.092, 0.045, -0.065), (0.092, 0.49, 0.065), knife_mats, 2, knife)
box('Knife_Material_Guard', (-0.175, 0.49, -0.082), (0.175, 0.55, 0.082), knife_mats, 3, knife)
box('Knife_Material_Pommel', (-0.101, 0.0, -0.073), (0.101, 0.06, 0.073), knife_mats, 3, knife)
for index in range(5):
    y = 0.10 + index * 0.073
    box('Knife_Material_GripRib_%02d' % index, (-0.098, y, -0.07), (0.098, y + 0.024, 0.07), knife_mats, 5, knife)
for index, y in enumerate((0.10, 0.43)):
    box('Knife_Material_Pin_%02d' % index, (-0.026, y, -0.073), (0.026, y + 0.022, 0.073), knife_mats, 4, knife)

# Join the knife's disjoint solid parts while keeping the grip-heel pivot.
bpy.ops.object.select_all(action='DESELECT')
knife_parts = [o for o in hierarchy(knife) if o.type == 'MESH']
for obj in knife_parts:
    obj.select_set(True)
bpy.context.view_layer.objects.active = knife_parts[0]
bpy.ops.object.join()
knife_parts[0].name = 'Knife_Material_Mesh'
knife.location.x = 3.0
knife_textured = duplicate_hierarchy(knife, 'Knife_Textured')
knife_textured.location.x = 4.0
knife_palette = [tuple(next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
                       .inputs['Base Color'].default_value[:3]) for mat in knife_mats]
knife_image = atlas('knife_palette_128', knife_palette)
apply_atlas(knife_textured, knife_image, 'Knife_Atlas_Bitmap')

# Asymmetric test blocks make axis flips and missing-UV behavior easy to identify.
probe_mats = [material('Probe_Red', (0.82, 0.12, 0.10)),
              material('Probe_Green', (0.11, 0.63, 0.26)),
              material('Probe_Blue', (0.10, 0.28, 0.85)),
              material('Probe_Yellow', (0.95, 0.74, 0.08))]
probe = empty('Probe_NoUV_ROOT', (math.pi / 2, 0, 0))
box('Probe_NoUV_Base', (-0.4, 0, -0.3), (0.4, 0.8, 0.3), probe_mats, 0, probe)
box('Probe_NoUV_PositiveX', (0.4, 0.18, -0.14), (0.7, 0.54, 0.14), probe_mats, 1, probe)
box('Probe_NoUV_PositiveZ', (-0.12, 0.2, 0.3), (0.12, 0.6, 0.58), probe_mats, 2, probe)
box('Probe_NoUV_Top', (-0.22, 0.8, -0.18), (0.22, 1.02, 0.18), probe_mats, 3, probe)
probe.location.x = 5.4
probe_textured = duplicate_hierarchy(probe, 'Probe_Textured')
probe_textured.location.x = 7.0
probe_image = atlas('probe_palette_128', [(0.82, 0.12, 0.10), (0.11, 0.63, 0.26),
                                          (0.10, 0.28, 0.85), (0.95, 0.74, 0.08)])
apply_atlas(probe_textured, probe_image, 'Probe_Atlas_Bitmap')

assets = [('ciws_material', ciws_material), ('ciws_textured', ciws_textured),
          ('knife_material', knife), ('knife_textured', knife_textured),
          ('probe_no_uv', probe), ('probe_textured', probe_textured)]
manifest = {'units': '1 unit = 1 Minecraft block', 'export_axis_up': 'Y', 'export_axis_forward': '-Z',
            'local_asset_forward': '+Z', 'animation': 'No baked animation. Named object hierarchy and pivots retained.',
            'assets': []}
for file_stem, root in assets:
    bpy.ops.object.select_all(action='DESELECT')
    selected = hierarchy(root)
    for obj in selected:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = root
    old_location = root.location.copy()
    root.location = (0, 0, 0)
    bpy.context.view_layer.update()
    fbx_options = dict(filepath=str(OUTPUT / 'fbx' / (file_stem + '.fbx')), use_selection=True,
                       object_types={'MESH', 'EMPTY'}, axis_forward='-Z', axis_up='Y',
                       apply_unit_scale=True, apply_scale_options='FBX_SCALE_NONE',
                       bake_space_transform=False, use_mesh_modifiers=True, mesh_smooth_type='FACE',
                       use_triangles=True, use_custom_props=True, add_leaf_bones=False,
                       bake_anim=False, path_mode='COPY', embed_textures=True)
    bpy.ops.export_scene.fbx(**fbx_options)
    gltf_options = dict(filepath=str(OUTPUT / 'glb' / (file_stem + '.glb')), export_format='GLB',
                        use_selection=True, use_active_scene=True, export_animations=False)
    bpy.ops.export_scene.gltf(**gltf_options)
    meshes = [o for o in selected if o.type == 'MESH']
    tri_count = 0
    for obj in meshes:
        obj.data.calc_loop_triangles()
        tri_count += len(obj.data.loop_triangles)
    bounds = [[min((o.matrix_world @ Vector(v))[k] for o in meshes for v in o.bound_box),
               max((o.matrix_world @ Vector(v))[k] for o in meshes for v in o.bound_box)] for k in range(3)]
    entry = {'name': file_stem, 'root': root.name, 'triangles': tri_count,
             'meshes': len(meshes), 'texture': 'textured' in file_stem,
             'uv_layers': sum(len(o.data.uv_layers) for o in meshes), 'blender_bounds': bounds,
             'hierarchy': [{'name': o.name, 'parent': o.parent.name if o.parent else None,
                            'location': list(o.location), 'rotation': list(o.rotation_euler)} for o in selected]}
    manifest['assets'].append(entry)
    root['triangles'] = tri_count
    root['test_variant'] = file_stem
    root.location = old_location

bpy.ops.object.select_all(action='DESELECT')
for obj in original_selection:
    obj.select_set(True)
bpy.context.view_layer.objects.active = original_active
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(OUTPUT / 'blender' / 'assimp_test_assets.blend'), copy=True)
print('ASSET_MANIFEST', json.dumps(manifest))
print('ASSET_OUTPUT', str(OUTPUT))
