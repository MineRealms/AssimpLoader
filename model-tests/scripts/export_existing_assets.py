import bpy
from pathlib import Path

output = Path(__file__).resolve().parents[1]
renames = {
    'Knife_Textured_Knife_Material_ROOT': 'Knife_Textured_ROOT',
    'Knife_Textured_Knife_Material_Mesh': 'Knife_Textured_Mesh',
    'Probe_Textured_Probe_NoUV_ROOT': 'Probe_Textured_ROOT',
    'Probe_Textured_Probe_NoUV_Base': 'Probe_Textured_Base',
    'Probe_Textured_Probe_NoUV_PositiveX': 'Probe_Textured_PositiveX',
    'Probe_Textured_Probe_NoUV_PositiveZ': 'Probe_Textured_PositiveZ',
    'Probe_Textured_Probe_NoUV_Top': 'Probe_Textured_Top',
}
for old, new in renames.items():
    obj = bpy.data.objects.get(old)
    if obj:
        obj.name = new

assets = [('ciws_material', 'CIWS_Material_ROOT'), ('ciws_textured', 'CIWS_Textured_ROOT'),
          ('knife_material', 'Knife_Material_ROOT'), ('knife_textured', 'Knife_Textured_ROOT'),
          ('probe_no_uv', 'Probe_NoUV_ROOT'), ('probe_textured', 'Probe_Textured_ROOT')]
original_scene = bpy.context.scene
selection = list(bpy.context.selected_objects)
active = bpy.context.view_layer.objects.active
for file_stem, root_name in assets:
    root = bpy.data.objects[root_name]
    for obj in bpy.context.view_layer.objects:
        obj.select_set(False)
    selected = [root] + list(root.children_recursive)
    for obj in selected:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = root
    location = root.location.copy()
    root.location = (0, 0, 0)
    bpy.context.view_layer.update()
    bpy.ops.export_scene.fbx(filepath=str(output / 'fbx' / (file_stem + '.fbx')),
                             use_selection=True, object_types={'MESH', 'EMPTY'},
                             axis_forward='-Z', axis_up='Y', apply_unit_scale=True,
                             apply_scale_options='FBX_SCALE_NONE', bake_space_transform=False,
                             use_mesh_modifiers=True, mesh_smooth_type='FACE', use_triangles=True,
                             use_custom_props=True, add_leaf_bones=False, bake_anim=False,
                             path_mode='COPY', embed_textures=True)
    bpy.ops.export_scene.gltf(filepath=str(output / 'glb' / (file_stem + '.glb')),
                              export_format='GLB', use_selection=True, use_active_scene=True,
                              export_animations=False)
    root.location = location
for obj in bpy.context.view_layer.objects:
    obj.select_set(False)
for obj in selection:
    obj.select_set(True)
bpy.context.view_layer.objects.active = active
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(output / 'blender' / 'assimp_test_assets.blend'), copy=True)
print('REEXPORTED', len(assets), 'FBX and GLB files, active scene only')
