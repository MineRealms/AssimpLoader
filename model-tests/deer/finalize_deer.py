"""Generate runtime settings and evaluated Blender references alongside the exports."""
import bpy
import json
from pathlib import Path
from mathutils import Matrix, Vector

BASE = Path('H:/MinecraftMods/AssimpLoader/model-tests/deer')
ASSETS = Path('H:/MinecraftMods/AssimpLoader/src/main/resources/assets/assimploader/models/validation')
FIXTURES = Path('H:/MinecraftMods/AssimpLoader/src/test/resources/fixtures')
scene = bpy.context.scene
rig, body = bpy.data.objects['DeerRig'], bpy.data.objects['DeerMesh']
material = body.data.materials[0]
principled = next(n for n in material.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
for link in list(principled.inputs['Alpha'].links):
    material.node_tree.links.remove(link)
principled.inputs['Alpha'].default_value = 1
for node in material.node_tree.nodes:
    if node.type == 'TEX_IMAGE':
        node.interpolation = 'Closest'
clips = json.loads((BASE / 'deer_manifest.json').read_text())['exportClips']
definition = {'version': 1, 'model': 'assimploader:models/validation/deer_showcase.glb', 'initial': 'idle',
              'performance': {'updateIntervalTicks': 1, 'ik': True, 'springBones': True, 'particles': True, 'trails': True,
                              'maxParticleEmissions': 4, 'maxTrailSamples': 32},
              'states': {name: {'clip': name, 'loop': name not in ('death', 'hit', 'jump'), 'transition': .2} for name in clips},
              'attachments': {name: {'node': node} for name, node in {'saddle': 'seat', 'head': 'head', 'tail': 'tail1',
                                 'hoofFL': 'hoofFL', 'hoofFR': 'hoofFR', 'hoofBL': 'hoofBL', 'hoofBR': 'hoofBR'}.items()},
              'colliders': {}, 'springs': {}, 'hitboxes': {}}
def local(node, point):
    value = rig.data.bones[node].matrix_local.inverted() @ Vector(point)
    return [round(value.x, 7), round(value.y, 7), round(-value.z, 7)]
definition['colliders']['torso'] = {'node': 'hips', 'type': 'capsule', 'start': local('hips', (0, .3, 1.05)),
                                     'end': local('hips', (0, -.4, 1.05)), 'radius': .25}
definition['hitboxes']['torso'] = {k: v for k, v in definition['colliders']['torso'].items() if k != 'type'}
definition['hitboxes']['head'] = {'node': 'head', 'start': [0, 0, 0], 'end': [0, .18, 0], 'radius': .15}
for leg in ('FL', 'FR', 'BL', 'BR'):
    for index in (0, 1, 2):
        name = 'leg' + leg + str(index)
        definition['hitboxes'][name] = {'node': name, 'start': [0, 0, 0], 'end': [0, rig.data.bones[name].length, 0], 'radius': .065}
for name, stiffness, damping in [('earL', 70, 8), ('earR', 70, 8), ('tail0', 35, 6), ('tail1', 28, 5), ('stirrupL', 45, 7), ('stirrupR', 45, 7)]:
    bone = rig.data.bones[name]
    tail = [0, bone.length, 0]
    if name.startswith('stirrup'):
        tail = local(name, bone.head_local + Vector((0, 0, -.22)))
    definition['springs'][name] = {'node': name, 'tail': tail, 'stiffness': stiffness, 'damping': damping,
                                   'gravity': [0, -2, 0], 'radius': .012, 'fixedStep': 1/120, 'maxSubsteps': 8,
                                   'teleportDistance': 2}
(ASSETS / 'deer_showcase.instance.json').write_text(json.dumps(definition, indent=2))

# Blender Z-up -> glTF Y-up -> AssimpLoader's left-handed model space.
yup = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
samples = []
for name in clips:
    action = bpy.data.actions[name]
    rig.animation_data.action = action
    for frame in (1, round(action.frame_range[1] / 2), round(action.frame_range[1]) - 1):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        depsgraph = bpy.context.evaluated_depsgraph_get()
        evaluated = body.evaluated_get(depsgraph)
        mesh = evaluated.to_mesh()
        points = [yup @ evaluated.matrix_world @ vertex.co for vertex in mesh.vertices]
        nodes = {node: list((yup @ rig.pose.bones[node].matrix).translation) for node in ('hips', 'head', 'hoofFL', 'hoofFR', 'hoofBL', 'hoofBR', 'tail1')}
        samples.append({'clip': name, 'seconds': (frame - 1) / scene.render.fps,
                        'nodes': nodes, 'min': [min(p[i] for p in points) for i in range(3)],
                        'max': [max(p[i] for p in points) for i in range(3)]})
        evaluated.to_mesh_clear()
FIXTURES.mkdir(parents=True, exist_ok=True)
(FIXTURES / 'deer_reference.json').write_text(json.dumps({'samples': samples}, indent=2))

bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
body.select_set(True)
bpy.context.view_layer.objects.active = rig
rig.animation_data.action = None
for track in rig.animation_data.nla_tracks:
    track.mute = False
bpy.ops.export_scene.gltf(filepath=str(ASSETS / 'deer_showcase.glb'), export_format='GLB', use_selection=True,
                          export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
                          export_frame_range=False, export_skins=True, export_yup=True, export_def_bones=False,
                          export_anim_slide_to_zero=True)
bpy.ops.export_scene.fbx(filepath=str(BASE / 'deer_showcase.fbx'), use_selection=True, object_types={'ARMATURE', 'MESH'},
                         axis_forward='Z', axis_up='Y', apply_scale_options='FBX_SCALE_ALL',
                         add_leaf_bones=False, bake_anim=True, bake_anim_use_all_actions=False,
                         bake_anim_use_nla_strips=True, bake_anim_simplify_factor=0, path_mode='COPY', embed_textures=True)
for track in rig.animation_data.nla_tracks:
    track.mute = True
rig.animation_data.action = bpy.data.actions['idle']
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(BASE / 'deer_rigged.blend'), check_existing=False)
print('FINALIZED', len(samples), 'reference samples;', len(definition['springs']), 'springs;', len(definition['hitboxes']), 'hitboxes')
