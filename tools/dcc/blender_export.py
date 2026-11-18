"""Run export_job(job_path) inside Blender's Python interpreter.

This adapter exports a selected asset; it never saves the source .blend file.
The companion assimploader_bundle module owns the output/configuration format.
"""

import sys
import tempfile
from pathlib import Path


def _job(path):
    directory = str(Path(__file__).resolve().parent)
    if directory not in sys.path:
        sys.path.insert(0, directory)
    from assimploader_bundle import ExportJob
    return ExportJob.load(path)


def _objects(bpy, roots):
    selected = []
    for name in roots:
        obj = bpy.context.scene.objects.get(name)
        if obj is None:
            raise ValueError("Export root is not in the active scene: " + name)
        selected.append(obj)
    if not roots:
        selected = list(bpy.context.selected_objects)
    if not selected:
        raise ValueError("Select the asset or provide explicit roots; whole-scene export is disabled")
    result = set()

    def descendants(obj):
        result.add(obj)
        for child in obj.children:
            descendants(child)

    for obj in selected:
        descendants(obj)
    changed = True
    while changed:
        previous = len(result)
        rigs = {obj for obj in result if obj.type == "ARMATURE"}
        for obj in list(result):
            if obj.type == "MESH":
                for modifier in obj.modifiers:
                    if modifier.type == "ARMATURE" and modifier.object is not None:
                        result.add(modifier.object)
                        rigs.add(modifier.object)
        for obj in bpy.context.scene.objects:
            if obj.type == "MESH" and any(mod.type == "ARMATURE" and mod.object in rigs for mod in obj.modifiers):
                result.add(obj)
        for obj in list(result):
            parent = obj.parent
            while parent is not None:
                result.add(parent)
                parent = parent.parent
        changed = len(result) != previous
    unsupported = [obj.name for obj in result if obj.type not in {"MESH", "EMPTY", "ARMATURE"}]
    if unsupported:
        raise ValueError("Convert/remove non-runtime objects from the export hierarchy first: " + ", ".join(sorted(unsupported)))
    for obj in result:
        if obj.name not in bpy.context.view_layer.objects or obj.hide_select or obj.hide_get():
            raise ValueError("Export object must be selectable and visible in the active view layer: " + obj.name)
    if not any(obj.type == "MESH" for obj in result):
        raise ValueError("The selected hierarchy contains no meshes")
    return sorted(result, key=lambda obj: obj.name)


def _images(materials):
    found = set()
    visited = set()

    def walk(tree):
        if tree is None or tree.as_pointer() in visited:
            return
        visited.add(tree.as_pointer())
        for node in tree.nodes:
            if node.type == "TEX_IMAGE" and node.image is not None:
                found.add(node.image)
            if node.type == "GROUP":
                walk(node.node_tree)

    for material in materials:
        walk(material.node_tree if material.use_nodes else None)
    return sorted(found, key=lambda image: image.name)


def _animation_setup(bpy, objects, clips, snapshots, temporary_tracks):
    """Materialize named Action/NLA clips without changing the author's keys."""
    targets = [obj for obj in objects if obj.type == "ARMATURE"]
    if not targets:
        targets = [obj for obj in objects if obj.animation_data is not None]
    for obj in objects:
        data = obj.animation_data
        if data is not None:
            snapshots.append((obj, data.action, getattr(data, "action_slot", None),
                              [(track, track.mute, track.is_solo) for track in data.nla_tracks],
                              data.use_nla, False))
            data.action = None
            data.use_nla = True
            for track in data.nla_tracks:
                track.mute = True
                track.is_solo = False
    for clip in clips:
        bindings = clip.get("actions")
        if bindings is None:
            target = clip.get("object")
            if target is None:
                if len(targets) != 1:
                    raise ValueError("Each clip needs object/actions when the asset has multiple animation owners")
                target = targets[0].name
            bindings = {target: clip.get("action", clip["name"])}
        for object_name, action_name in bindings.items():
            obj = next((value for value in objects if value.name == object_name), None)
            action = bpy.data.actions.get(action_name)
            if obj is None or action is None:
                raise ValueError("Clip owner/action does not exist in the export: " + object_name + " / " + action_name)
            if obj.animation_data is None:
                obj.animation_data_create()
                snapshots.append((obj, None, None, [], True, True))
            track = obj.animation_data.nla_tracks.new()
            temporary_tracks.append((obj, track))
            track.name = "AssimpExport_" + clip["name"]
            strip = track.strips.new(clip["name"], int(clip["start"]), action)
            strip.name = clip["name"]
            slots = list(getattr(action, "slots", ()))
            if slots:
                slot_id = clip.get("slot")
                matches = [slot for slot in slots if slot.identifier == slot_id] if slot_id else slots
                if len(matches) != 1:
                    raise ValueError("A slotted Action requires one explicit slot identifier: " + action_name)
                strip.action_slot = matches[0]
            strip.action_frame_start = float(clip["start"])
            strip.action_frame_end = float(clip["end"])
            strip.frame_start = float(clip["start"])
            strip.frame_end = float(clip["end"])
            strip.blend_type = "REPLACE"
            strip.extrapolation = "NOTHING"


def export_job(job_path):
    import bpy

    job = _job(job_path)
    if bpy.context.mode != "OBJECT":
        raise ValueError("Finish Edit/Pose mode before exporting")
    objects = _objects(bpy, job.roots)
    if any(obj.animation_data is not None and obj.animation_data.use_tweak_mode for obj in objects):
        raise ValueError("Finish NLA Tweak mode before exporting")
    bake = bool(job.raw.get("bake_animation", False))
    if job.clips and not bake:
        raise ValueError("Declared clips require bake_animation=true")
    job.prepare()
    materials = sorted({slot.material for obj in objects if obj.type == "MESH"
                        for slot in obj.material_slots if slot.material is not None}, key=lambda value: value.name)
    previous_selection = list(bpy.context.selected_objects)
    previous_active = bpy.context.view_layer.objects.active
    previous_frame = (bpy.context.scene.frame_current, bpy.context.scene.frame_subframe)
    image_paths = []
    animation_snapshots = []
    temporary_tracks = []
    warnings = ["FBX take names may include an armature prefix; confirm imported clip names in the workbench.",
                "Procedural materials, UDIM and arbitrary node graphs require UV0 image baking before export."]
    clips = []
    fps = bpy.context.scene.render.fps / bpy.context.scene.render.fps_base
    try:
        with tempfile.TemporaryDirectory(prefix="assimploader-images-") as staging:
            for index, image in enumerate(_images(materials)):
                if image.source not in {"FILE", "GENERATED"}:
                    raise ValueError("Bake sequence/tiled/movie image to a regular image first: " + image.name)
                source = Path(bpy.path.abspath(image.filepath, library=image.library)) if image.filepath else None
                if image.packed_file is not None:
                    suffix = source.suffix if source is not None and source.suffix else ".png"
                    source = Path(staging) / ("image_" + str(index) + suffix)
                    source.write_bytes(bytes(image.packed_file.data))
                elif source is None or not source.is_file():
                    if image.source != "GENERATED":
                        raise FileNotFoundError("Missing texture: " + image.name + " / " + str(source))
                    duplicate = image.copy()
                    try:
                        source = Path(staging) / ("image_" + str(index) + ".png")
                        duplicate.filepath_raw = str(source)
                        duplicate.file_format = "PNG"
                        duplicate.save()
                    finally:
                        bpy.data.images.remove(duplicate)
                destination = job.copy_texture(source)
                image_paths.append((image, image.filepath))
                image.filepath = str(destination)
            if bake and job.clips:
                _animation_setup(bpy, objects, job.clips, animation_snapshots, temporary_tracks)
                clips = [{"name": clip["name"], "start": clip["start"], "end": clip["end"], "fps": fps}
                         for clip in job.clips]
            elif bake:
                for obj in objects:
                    data = obj.animation_data
                    if data is None:
                        continue
                    for track in data.nla_tracks:
                        if not track.mute:
                            clips.extend({"name": strip.name, "start": strip.frame_start, "end": strip.frame_end, "fps": fps}
                                         for strip in track.strips if not strip.mute)
                    if data.action is not None:
                        clips.append({"name": data.action.name, "start": data.action.frame_range[0],
                                      "end": data.action.frame_range[1], "fps": fps})
            for obj in bpy.context.selected_objects:
                obj.select_set(False)
            for obj in objects:
                obj.select_set(True)
            bpy.context.view_layer.objects.active = next((obj for obj in objects if obj.type == "ARMATURE"), objects[0])
            result = bpy.ops.export_scene.fbx(
                filepath=str(job.model_path), check_existing=False, use_selection=True,
                object_types={"MESH", "EMPTY", "ARMATURE"}, global_scale=1,
                axis_forward="-Z", axis_up="Y", apply_unit_scale=True,
                apply_scale_options="FBX_SCALE_NONE", bake_space_transform=False,
                use_mesh_modifiers=not any(obj.type == "ARMATURE" for obj in objects),
                mesh_smooth_type="FACE", use_triangles=bool(job.raw.get("triangulate", True)),
                add_leaf_bones=False, use_armature_deform_only=False,
                bake_anim=bake, bake_anim_use_all_bones=True, bake_anim_use_nla_strips=True,
                bake_anim_use_all_actions=False, bake_anim_force_startend_keying=True,
                bake_anim_step=float(job.raw.get("frame_step", 1)), bake_anim_simplify_factor=0,
                path_mode="COPY" if job.raw.get("embed_textures", True) else "RELATIVE",
                embed_textures=bool(job.raw.get("embed_textures", True)),
            )
            if "FINISHED" not in result:
                raise RuntimeError("Blender's FBX exporter did not finish: " + str(result))
        inventory = {
            "application": "Blender", "application_version": bpy.app.version_string,
            "source_file": bpy.data.filepath,
            "units": {"system": bpy.context.scene.unit_settings.system,
                      "scale_length": bpy.context.scene.unit_settings.scale_length,
                      "source_up_axis": "Z", "export_up_axis": "Y", "export_forward_axis": "-Z"},
            "nodes": [obj.name for obj in objects],
            "bones": [bone.name for obj in objects if obj.type == "ARMATURE" for bone in obj.data.bones],
            "clips": clips,
            "materials": [{"name": material.name, "use_nodes": material.use_nodes} for material in materials],
            "warnings": warnings,
        }
        return job.finish(inventory)
    finally:
        for obj, track in reversed(temporary_tracks):
            obj.animation_data.nla_tracks.remove(track)
        for obj, action, slot, tracks, use_nla, created in animation_snapshots:
            if created:
                obj.animation_data_clear()
            else:
                obj.animation_data.action = action
                if slot is not None:
                    obj.animation_data.action_slot = slot
                obj.animation_data.use_nla = use_nla
                for track, muted, solo in tracks:
                    track.mute = muted
                    track.is_solo = solo
        for image, filepath in image_paths:
            image.filepath = filepath
        bpy.context.scene.frame_set(previous_frame[0], subframe=previous_frame[1])
        for obj in bpy.context.selected_objects:
            obj.select_set(False)
        for obj in previous_selection:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = previous_active


if __name__ == "__main__":
    if "--" not in sys.argv or len(sys.argv[sys.argv.index("--") + 1:]) != 1:
        raise SystemExit("Blender usage: blender scene.blend --background --python blender_export.py -- export-job.json")
    print(export_job(sys.argv[sys.argv.index("--") + 1]))
