"""Run export_job(job_path) inside 3ds Max's bundled Python (pymxs)."""

import os
import sys
from pathlib import Path


def _job(path):
    directory = str(Path(__file__).resolve().parent)
    if directory not in sys.path:
        sys.path.insert(0, directory)
    from assimploader_bundle import ExportJob
    return ExportJob.load(path)


def _handle(rt, value):
    return int(rt.getHandleByAnim(value))


def _selection(rt, roots):
    scene = list(rt.objects)

    def resolve(name):
        matches = [node for node in scene if str(node.name) == name]
        if len(matches) != 1:
            raise ValueError("Export node must resolve uniquely: " + name)
        return matches[0]

    seeds = [resolve(name) for name in roots] if roots else list(rt.selection)
    if not seeds:
        raise ValueError("Select the asset or provide roots; whole-scene export is disabled")
    result = {}
    skin_bones = {}

    def add(node, children=False):
        result[_handle(rt, node)] = node
        if children:
            for child in node.children:
                add(child, children=True)
        parent = node.parent
        while parent is not None and parent != rt.rootNode:
            result[_handle(rt, parent)] = parent
            parent = parent.parent

    for node in seeds:
        add(node, children=True)
    skins = []
    for node in scene:
        for modifier in node.modifiers:
            if rt.classOf(modifier) == rt.Skin:
                influences = []
                for index in range(1, int(rt.skinOps.GetNumberBones(modifier)) + 1):
                    bone_id = rt.skinOps.GetBoneIDByListID(modifier, index)
                    influences.append(resolve(str(rt.skinOps.GetBoneName(modifier, bone_id, 0))))
                skins.append((node, influences))
    changed = True
    while changed:
        previous = len(result)
        for node, bones in skins:
            if _handle(rt, node) in result or any(_handle(rt, bone) in result for bone in bones):
                add(node)
                for bone in bones:
                    add(bone)
                    skin_bones[_handle(rt, bone)] = bone
        changed = len(result) != previous
    objects = sorted(result.values(), key=lambda node: (str(node.name), _handle(rt, node)))
    if not any(rt.superClassOf(node) == rt.GeometryClass for node in objects):
        raise ValueError("Selected hierarchy and skin dependencies contain no geometry")
    for node in objects:
        for modifier in node.modifiers:
            if str(rt.classOf(modifier)).lower() == "physique":
                raise ValueError("Convert Physique to Skin on an export copy first: " + str(node.name))
    return objects, sorted(skin_bones.values(), key=lambda node: str(node.name))


def _set(rt, key, *values):
    result = rt.FBXExporterSetParam(key, *values)
    if str(result).lower() == "unsupplied":
        raise RuntimeError("Installed FBX exporter does not support parameter: " + key)
    return result


def export_job(job_path):
    from pymxs import runtime as rt

    job = _job(job_path)
    objects, bones = _selection(rt, job.roots)
    bake = bool(job.raw.get("bake_animation", False))
    if job.clips and not bake:
        raise ValueError("Declared clips require bake_animation=true")
    rt.pluginManager.loadClass(rt.FbxExporter)
    job.prepare()
    previous_selection = list(rt.selection)
    previous_time = rt.sliderTime
    texture_paths = []
    pushed = False
    warnings = [
        "Autodesk FBX is not the engine's Blender metallic/roughness convention. Normalize in Blender or supply prepacked LabPBR companions.",
        "Only Bitmaptexture files are copied. Renderer-specific, OSL, procedural and tiled maps require UV0 image baking first.",
        "FBX takes contain evaluated animation, not Max controllers, constraints or the complete source modifier stack.",
    ]
    try:
        materials = {}
        textures = {}
        for node in objects:
            material = node.material
            if material is None:
                continue
            materials[_handle(rt, material)] = {"name": str(material.name), "type": str(rt.classOf(material))}
            for bitmap in rt.getClassInstances(rt.Bitmaptexture, target=material):
                textures[_handle(rt, bitmap)] = bitmap
        for bitmap in textures.values():
            original = str(bitmap.filename)
            source = Path(os.path.expandvars(str(rt.pathConfig.convertPathToAbsolute(original))))
            if not source.is_file():
                raise FileNotFoundError("Missing Bitmaptexture: " + str(bitmap.name) + " / " + str(source))
            destination = job.copy_texture(source)
            texture_paths.append((bitmap, original))
            bitmap.filename = str(destination)
        _set(rt, "PushSettings")
        pushed = True
        for key, value in {
            "ASCII": False, "Animation": bake, "BakeAnimation": bake,
            "BakeResampleAnimation": bake, "Skin": True, "Shape": True,
            "Cameras": False, "Lights": False, "SmoothingGroups": True,
            "Triangulate": bool(job.raw.get("triangulate", True)),
            "EmbedTextures": bool(job.raw.get("embed_textures", True)), "UpAxis": "Y",
        }.items():
            _set(rt, key, value)
        _set(rt, "SplitAnimationIntoTakes", "-clear")
        start = min((float(clip["start"]) for clip in job.clips), default=float(rt.animationRange.start.frame))
        end = max((float(clip["end"]) for clip in job.clips), default=float(rt.animationRange.end.frame))
        if bake:
            _set(rt, "BakeFrameStart", int(start))
            _set(rt, "BakeFrameEnd", int(end))
            _set(rt, "BakeFrameStep", int(job.raw.get("frame_step", 1)))
        clips = []
        for clip in job.clips:
            _set(rt, "SplitAnimationIntoTakes", clip["name"], int(clip["start"]), int(clip["end"]))
            clips.append({"name": clip["name"], "start": clip["start"], "end": clip["end"], "fps": int(rt.frameRate)})
        if bake and not clips:
            clips.append({"name": "Take001", "start": start, "end": end, "fps": int(rt.frameRate)})
        elif bake:
            warnings.append("The FBX plugin retains Take001 in addition to split takes; select intended imported clips explicitly.")
        rt.select(rt.Array(*objects))
        result = rt.exportFile(str(job.model_path), rt.Name("noPrompt"), selectedOnly=True, using=rt.FBXEXP)
        if result is False:
            raise RuntimeError("3ds Max's FBX exporter reported failure")
        return job.finish({
            "application": "3ds Max", "application_version": str(rt.maxVersion()),
            "source_file": str(rt.maxFilePath) + str(rt.maxFileName),
            "units": {"system_type": str(rt.units.SystemType), "system_scale": float(rt.units.SystemScale),
                      "source_up_axis": "Z", "export_up_axis": "Y", "policy": "FBX unit metadata; no Reset XForm or scene-unit changes"},
            "nodes": [str(node.name) for node in objects], "bones": [str(bone.name) for bone in bones],
            "clips": clips, "materials": list(materials.values()), "warnings": warnings,
        })
    finally:
        for bitmap, original in texture_paths:
            bitmap.filename = original
        rt.sliderTime = previous_time
        if previous_selection:
            rt.select(rt.Array(*previous_selection))
        else:
            rt.clearSelection()
        if pushed:
            _set(rt, "PopSettings")
