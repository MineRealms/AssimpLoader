"""Blender background export for the optional engine pack or redistributable fallback."""

import argparse
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector


def material(name, color, metallic, roughness):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    node = next(node for node in mat.node_tree.nodes if node.type == 'BSDF_PRINCIPLED')
    node.inputs['Base Color'].default_value = (*color, 1)
    node.inputs['Metallic'].default_value = metallic
    node.inputs['Roughness'].default_value = roughness
    mat.diffuse_color = (*color, 1)
    return mat


def revolve(name, profile, mat, segments=64):
    vertices = [(radius * math.cos(i * math.tau / segments), radius * math.sin(i * math.tau / segments), z)
                for radius, z in profile for i in range(segments)]
    faces = []
    for ring in range(len(profile) - 1):
        for i in range(segments):
            a = ring * segments + i
            b = ring * segments + (i + 1) % segments
            faces.append((a, b, b + segments, a + segments))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(mat)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    uv = mesh.uv_layers.new(name='UVMap')
    for poly in mesh.polygons:
        poly.use_smooth = True
        for loop_index in poly.loop_indices:
            vertex_index = mesh.loops[loop_index].vertex_index
            uv.data[loop_index].uv = (vertex_index % segments / segments,
                                      vertex_index // segments / (len(profile) - 1))
    return obj


def fallback_meshes():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    steel = material('rocket_steel', (0.29, 0.33, 0.36), 0.95, 0.32)
    copper = material('rocket_copper', (0.44, 0.21, 0.09), 0.9, 0.3)
    dark = material('rocket_dark', (0.055, 0.06, 0.065), 0.8, 0.6)
    bell = revolve('rocket_bell', [(1.03425, 0), (0.96, 0.3), (0.8, 0.7), (0.57, 1.15),
                                  (0.34, 1.6), (0.2, 1.9), (0.25, 2.05), (0.28, 2.1),
                                  (0.24, 1.9), (0.38, 1.6), (0.61, 1.15), (0.84, 0.7),
                                  (1.0, 0.3), (1.07425, 0), (1.03425, 0)], steel)
    chamber = revolve('rocket_chamber', [(0.25, 2.0), (0.4, 2.2), (0.42, 3.4),
                                        (0.3, 3.65), (0.0, 3.65)], dark)
    meshes = [bell, chamber]
    for z in (0.12, 0.4, 0.75, 1.1, 1.45):
        radius = 1.07 - 0.42 * z
        meshes.append(revolve('cooling_band', [(radius, z), (radius + 0.03, z),
                                              (radius + 0.03, z + 0.06), (radius, z + 0.06)], copper))
    for angle in (0, math.pi * 0.5, math.pi, math.pi * 1.5):
        bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.075, depth=2.55,
                                           location=(0.6 * math.cos(angle), 0.6 * math.sin(angle), 3.15))
        pipe = bpy.context.object
        pipe.name = 'rocket_feed_pipe'
        pipe.data.materials.append(copper)
        meshes.append(pipe)
    meshes.append(revolve('mount_ring', [(0.35, 4.4), (0.75, 4.4), (0.75, 4.6),
                                       (0.35, 4.6), (0.35, 4.4)], steel))
    return meshes, {'kind': 'authored_fallback', 'scale': 1.0, 'source_nozzle_center_blender': [0, 0, 0],
                    'source_license': 'Project-authored fallback; same license as AssimpLoader.'}


def source_meshes(source, scale):
    bpy.ops.wm.open_mainfile(filepath=str(source))
    root = bpy.data.objects.get('rocket_engine_root')
    meshes = [] if root is None else [obj for obj in root.children_recursive if obj.type == 'MESH']
    if not meshes:
        raise RuntimeError('Prepared rocket_engine_root mesh hierarchy is missing')
    motor = next(obj for obj in meshes if obj.name == 'rocket_moteur')
    points = [motor.matrix_world @ vertex.co for vertex in motor.data.vertices]
    bottom = min(point.z for point in points)
    lip = [point for point in points if point.z <= bottom + 0.005]
    center = Vector(((min(point.x for point in lip) + max(point.x for point in lip)) * 0.5,
                     (min(point.y for point in lip) + max(point.y for point in lip)) * 0.5, bottom))
    transform = Matrix.Scale(scale, 4) @ Matrix.Translation(-center)
    for obj in meshes:
        world = obj.matrix_world.copy()
        obj.parent = None
        obj.matrix_world = transform @ world
    return meshes, {'kind': 'local_third_party_asset', 'scale': scale,
                    'source_nozzle_center_blender': list(center),
                    'source_license': 'No redistribution license included in supplied archive.'}


def main():
    args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-blend', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--scale', type=float, default=0.25)
    parser.add_argument('--fallback', action='store_true')
    options = parser.parse_args(args)
    if options.scale <= 0 or not math.isfinite(options.scale):
        raise ValueError('Scale must be finite and positive')
    if options.fallback == bool(options.input_blend):
        raise ValueError('Select exactly one of --fallback or --input-blend')
    meshes, report = fallback_meshes() if options.fallback else source_meshes(options.input_blend.resolve(), options.scale)
    bpy.ops.object.select_all(action='DESELECT')
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    root = bpy.data.objects.new('rocket_showcase_root', None)
    bpy.context.scene.collection.objects.link(root)
    for obj in meshes:
        obj.parent = root
    root.select_set(True)
    corners = [obj.matrix_world @ vertex.co for obj in meshes for vertex in obj.data.vertices]
    triangles = 0
    for obj in meshes:
        obj.data.calc_loop_triangles()
        triangles += len(obj.data.loop_triangles)
    if not options.fallback and triangles != 692900:
        raise RuntimeError('Source triangle count changed: ' + str(triangles))
    options.output.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.fbx(filepath=str(options.output.resolve()), use_selection=True,
                             object_types={'MESH', 'EMPTY'}, global_scale=1.0,
                             axis_forward='-Z', axis_up='Y', apply_unit_scale=True,
                             apply_scale_options='FBX_SCALE_NONE', bake_space_transform=False,
                             use_mesh_modifiers=True, mesh_smooth_type='FACE', use_triangles=True,
                             use_tspace=True, add_leaf_bones=False, bake_anim=False,
                             path_mode='COPY', embed_textures=True, use_custom_props=False)
    report.update({'triangles': triangles, 'mesh_parts': len(meshes), 'model_name': 'rocket_engine',
                   'nozzle_center_y_up': [0, 0, 0], 'exhaust_direction_y_up': [0, -1, 0],
                   'nozzle_inner_radius': 1.03425 if options.fallback else 4.137 * options.scale,
                   'bounds_blender': {'min': [min(p[k] for p in corners) for k in range(3)],
                                      'max': [max(p[k] for p in corners) for k in range(3)]}})
    options.output.with_suffix('.manifest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('ROCKET_SHOWCASE_READY ' + json.dumps(report), flush=True)


if __name__ == '__main__':
    main()
