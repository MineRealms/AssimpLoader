"""Add editable IK controls, bake an IK demonstration and export the runtime rig."""
import bpy
import json
import math
from pathlib import Path
from mathutils import Vector

BASE = Path('H:/MinecraftMods/AssimpLoader/model-tests/deer')
ASSETS = Path('H:/MinecraftMods/AssimpLoader/src/main/resources/assets/assimploader/models/validation')
scene = bpy.context.scene
rig = bpy.data.objects['DeerRig']
body = bpy.data.objects['DeerMesh']
rig.animation_data.action = bpy.data.actions['idle']
scene.frame_set(1)
bpy.context.view_layer.update()
authoring = bpy.data.collections.new('DEER_IK_AUTHORING')
scene.collection.children.link(authoring)
ik_rig = rig.copy()
ik_rig.data = rig.data.copy()
ik_rig.name = 'DeerIKRig'
ik_rig.animation_data_clear()
ik_rig.animation_data_create()
ik_rig.animation_data.action = bpy.data.actions['idle']
authoring.objects.link(ik_rig)
ik_rig.show_in_front = True
ik_rig.display_type = 'WIRE'
preview = body.copy()
preview.name = 'DeerIKPreview'
preview.parent = ik_rig
preview.modifiers['Skin'].object = ik_rig
authoring.objects.link(preview)
bpy.context.view_layer.update()
targets, baselines, poles = {}, {}, {}
for leg in ('FL', 'FR', 'BL', 'BR'):
    end = ik_rig.pose.bones['leg' + leg + '2']
    target = bpy.data.objects.new('IK_hoof_' + leg, None)
    authoring.objects.link(target)
    target.empty_display_type = 'ARROWS'
    target.empty_display_size = .12
    target.location = end.tail
    targets[leg] = target
    baselines[leg] = target.location.copy()
    pole = bpy.data.objects.new('IK_pole_' + leg, None)
    authoring.objects.link(pole)
    pole.empty_display_type = 'SPHERE'
    pole.empty_display_size = .06
    pole.location = ik_rig.pose.bones['leg' + leg + '1'].head + Vector((0, -.6 if leg[0] == 'F' else .6, 0))
    poles[leg] = pole
    constraint = end.constraints.new('IK')
    constraint.name = 'Hoof target (bake before export)'
    constraint.target = target
    constraint.pole_target = pole
    constraint.chain_count = 3
    constraint.use_stretch = False
    reference = {i: rig.pose.bones['leg' + leg + str(i)].head.copy() for i in range(3)}
    def score(angle):
        constraint.pole_angle = angle
        bpy.context.view_layer.update()
        return sum((ik_rig.pose.bones['leg' + leg + str(i)].head - reference[i]).length_squared for i in range(3))
    angle = min((i * math.tau / 64 - math.pi for i in range(64)), key=score)
    constraint.pole_angle = angle
    bpy.context.view_layer.update()

frames = []
max_foot_error = 0.0
for frame in range(1, 62):
    for leg, target in targets.items():
        phase = (frame - 1) / 60 * math.tau + (0 if leg in ('FL', 'BR') else math.pi)
        lift = max(0, math.sin(phase))
        target.location = baselines[leg] + Vector((0, -.06 * lift, .18 * lift))
        target.keyframe_insert('location', frame=frame)
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    frames.append({p.name: p.matrix.copy() for p in ik_rig.pose.bones})
    max_foot_error = max(max_foot_error, max((ik_rig.pose.bones['leg' + leg + '2'].tail - target.location).length for leg, target in targets.items()))
assert max_foot_error < .015, max_foot_error

action = bpy.data.actions.new('ik_probe')
rig.animation_data.action = action
previous = {}
for index, desired in enumerate(frames):
    for bone in rig.data.bones:
        pose = rig.pose.bones[bone.name]
        kwargs = {'parent_matrix': desired[bone.parent.name], 'parent_matrix_local': bone.parent.matrix_local} if bone.parent else {}
        basis = bone.convert_local_to_pose(desired[bone.name], bone.matrix_local, invert=True, **kwargs)
        location, rotation, scale = basis.decompose()
        if bone.name in previous and rotation.dot(previous[bone.name]) < 0:
            rotation.negate()
        previous[bone.name] = rotation.copy()
        pose.location, pose.rotation_quaternion, pose.scale = location, rotation, scale
        for prop in ('location', 'rotation_quaternion', 'scale'):
            pose.keyframe_insert(prop, frame=index + 1, group=bone.name)
max_bake_error = 0.0
for index in range(len(frames)):
    scene.frame_set(index + 1)
    for name, expected in frames[index].items():
        actual = rig.pose.bones[name].matrix
        max_bake_error = max(max_bake_error, max(abs(actual[r][c] - expected[r][c]) for r in range(4) for c in range(4)))
assert max_bake_error < .0001, max_bake_error
clips = ('idle', 'walk', 'gallop', 'graze', 'bugle', 'antler_sweep', 'rear_up', 'jump', 'hit', 'death', 'ik_probe')
rig.animation_data.action = None
for name in clips:
    track = rig.animation_data.nla_tracks.new()
    track.name = name
    track.strips.new(name, 1, bpy.data.actions[name])
    track.mute = True
rig.animation_data.action = bpy.data.actions['idle']
scene.frame_start, scene.frame_end = 1, 121
scene.frame_set(1)
for obj in authoring.objects:
    obj.hide_set(True)
    obj.hide_render = True
rig.show_in_front = True
bpy.ops.object.select_all(action='DESELECT')
body.select_set(True)
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.wm.save_as_mainfile(filepath=str(BASE / 'deer_rigged.blend'), check_existing=False)
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
manifest = json.loads((BASE / 'deer_manifest.json').read_text())
manifest.update({'ikTargets': list(target.name for target in targets.values()),
                 'ikPoles': list(pole.name for pole in poles.values()), 'ikChainLength': 3,
                 'ikTargetMaximumErrorMeters': max_foot_error, 'ikBakeMaximumMatrixError': max_bake_error,
                 'exportClips': list(clips)})
(BASE / 'deer_manifest.json').write_text(json.dumps(manifest, indent=2))
print(json.dumps(manifest))
