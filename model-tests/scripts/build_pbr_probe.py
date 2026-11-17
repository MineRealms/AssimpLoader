import bpy
import math
from pathlib import Path
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'model-tests/pbr'
OUTPUT.mkdir(parents=True, exist_ok=True)
(OUTPUT / 'textures').mkdir(exist_ok=True)
old_scene = bpy.context.window.scene
old_selection = list(bpy.context.selected_objects)
old_active = bpy.context.view_layer.objects.active
collection = bpy.data.collections.get('AssimpLoader | PBR Probe')
if collection is None:
    collection = bpy.data.collections.new('AssimpLoader | PBR Probe')
    bpy.context.scene.collection.children.link(collection)
else:
    for obj in list(collection.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def image(name, pixel, color=False):
    img = bpy.data.images.new(name, width=64, height=64, alpha=True)
    img.colorspace_settings.name = 'sRGB' if color else 'Non-Color'
    pixels = []
    for y in range(64):
        for x in range(64):
            pixels.extend(pixel(x, y))
    img.pixels.foreach_set(pixels)
    img.filepath_raw = str(OUTPUT / 'textures' / (name + '.png'))
    img.file_format = 'PNG'
    img.save()
    img.pack()
    return img


def solid(value):
    return lambda x, y: (value, value, value, 1.0)


def normal_pixel(x, y):
    nx = 0.42 * math.sin(x * math.pi / 8)
    ny = 0.32 * math.sin(y * math.pi / 8)
    nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
    return (nx * 0.5 + 0.5, ny * 0.5 + 0.5, nz * 0.5 + 0.5, 1.0)


albedo = image('pbr_shared_albedo', lambda x, y: (
    (0.42 if ((x // 8 + y // 8) % 2) else 0.55),
    (0.48 if ((x // 8 + y // 8) % 2) else 0.62),
    (0.53 if ((x // 8 + y // 8) % 2) else 0.68), 1.0), True)
normal = image('pbr_opengl_normal', normal_pixel)
rough = image('pbr_matte_roughness', solid(0.88))
smooth = image('pbr_polished_roughness', solid(0.12))
metal = image('pbr_metallic', solid(1.0))
dielectric = image('pbr_dielectric', solid(0.0))


def material(name, roughness_image, metallic_image):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    bsdf.inputs['Base Color'].default_value = (1, 1, 1, 1)
    for socket, img in [('Base Color', albedo), ('Roughness', roughness_image), ('Metallic', metallic_image)]:
        node = mat.node_tree.nodes.new('ShaderNodeTexImage')
        node.image = img
        mat.node_tree.links.new(node.outputs['Color'], bsdf.inputs[socket])
    tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = normal
    normal_map = mat.node_tree.nodes.new('ShaderNodeNormalMap')
    mat.node_tree.links.new(tex.outputs['Color'], normal_map.inputs['Color'])
    mat.node_tree.links.new(normal_map.outputs['Normal'], bsdf.inputs['Normal'])
    return mat


materials = [material('PBR_Matte_Dielectric', rough, dielectric),
             material('PBR_Polished_Dielectric', smooth, dielectric),
             material('PBR_Polished_Metal', smooth, metal)]
objects = []
for i, mat in enumerate(materials):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=0.36,
                                       location=((i - 1) * 0.84, 0, 0.4))
    obj = bpy.context.object
    obj.name = mat.name
    for owner in list(obj.users_collection):
        owner.objects.unlink(obj)
    collection.objects.link(obj)
    obj.data.materials.append(mat)
    for polygon in obj.data.polygons:
        polygon.use_smooth = True
    objects.append(obj)

bpy.ops.mesh.primitive_plane_add(size=0.65, location=(0, -0.55, 0.22), rotation=(math.radians(24), 0, 0))
plate = bpy.context.object
plate.name = 'PBR_Normal_Slope'
for owner in list(plate.users_collection):
    owner.objects.unlink(plate)
collection.objects.link(plate)
plate.data.materials.append(materials[1])
objects.append(plate)

export_scene = bpy.data.scenes.new('AssimpLoader PBR Export Temporary')
export_scene.collection.children.link(collection)
bpy.context.window.scene = export_scene
for obj in bpy.context.view_layer.objects:
    obj.select_set(False)
for obj in objects:
    obj.select_set(True)
bpy.context.view_layer.objects.active = objects[0]
options = dict(use_selection=True, object_types={'MESH'}, axis_forward='-Z', axis_up='Y',
               apply_unit_scale=True, apply_scale_options='FBX_SCALE_NONE', bake_space_transform=False,
               use_mesh_modifiers=True, mesh_smooth_type='FACE', use_triangles=True, add_leaf_bones=False,
               bake_anim=False, path_mode='COPY', embed_textures=True)
target = ROOT / 'src/showcase/resources/assets/assimploader/models/showcase/pbr_probe.fbx'
target.parent.mkdir(parents=True, exist_ok=True)
try:
    bpy.ops.export_scene.fbx(filepath=str(OUTPUT / 'pbr_probe.fbx'), **options)
    bpy.ops.export_scene.fbx(filepath=str(target), **options)
    bpy.ops.export_scene.gltf(filepath=str(OUTPUT / 'pbr_probe.glb'), export_format='GLB',
                              use_selection=True, use_active_scene=True, export_animations=False)
finally:
    bpy.context.window.scene = old_scene
    bpy.data.scenes.remove(export_scene)
for obj in objects:
    obj.location.x += 11.0
for obj in list(bpy.context.selected_objects):
    obj.select_set(False)
for obj in old_selection:
    if obj.name in bpy.data.objects:
        obj.select_set(True)
bpy.context.view_layer.objects.active = old_active
print('PBR_PROBE_EXPORT', target, 'objects=', len(objects), 'images=6')
