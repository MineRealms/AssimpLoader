"""Run export_job(job_path) in Maya's Python Script Editor or MCP bridge."""

import os
import sys
from pathlib import Path


def _job(path):
    directory = str(Path(__file__).resolve().parent)
    if directory not in sys.path:
        sys.path.insert(0, directory)
    from assimploader_bundle import ExportJob
    return ExportJob.load(path)


def _quote(value):
    value = str(value)
    if any(character in value for character in "\n\r\0"):
        raise ValueError("MEL arguments cannot contain control characters")
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _nodes(cmds, roots):
    seeds = roots or cmds.ls(selection=True, long=True, objectsOnly=True) or []
    if not seeds:
        raise ValueError("Select the asset or provide roots; whole-scene export is disabled")
    result = set()

    def resolve(name):
        matches = cmds.ls(name, long=True) or []
        if len(matches) != 1:
            raise ValueError("Export node must resolve uniquely: " + name)
        node = matches[0]
        if cmds.nodeType(node) not in {"transform", "joint"}:
            parents = cmds.listRelatives(node, parent=True, fullPath=True) or []
            if not parents:
                raise ValueError("Expected a DAG transform or joint: " + node)
            node = parents[0]
        return node

    def add(node, children=False):
        result.add(node)
        if children:
            for descendant in cmds.listRelatives(node, allDescendents=True, fullPath=True) or []:
                if cmds.nodeType(descendant) in {"transform", "joint"}:
                    result.add(descendant)
        parent = cmds.listRelatives(node, parent=True, fullPath=True) or []
        while parent:
            result.add(parent[0])
            parent = cmds.listRelatives(parent[0], parent=True, fullPath=True) or []

    for name in seeds:
        add(resolve(name), children=True)
    changed = True
    while changed:
        previous = len(result)
        for skin in cmds.ls(type="skinCluster") or []:
            geometry = cmds.skinCluster(skin, query=True, geometry=True) or []
            owners = [resolve(shape) for shape in geometry]
            influences = [resolve(node) for node in cmds.skinCluster(skin, query=True, influence=True) or []]
            if result.intersection(owners) or result.intersection(influences):
                for node in owners + influences:
                    add(node)
        changed = len(result) != previous
    shapes = []
    for node in result:
        shapes.extend(cmds.listRelatives(node, shapes=True, noIntermediate=True, fullPath=True) or [])
    if not any(cmds.nodeType(shape) == "mesh" for shape in shapes):
        raise ValueError("Selected hierarchy and skin dependencies contain no meshes")
    unsupported = [shape for shape in shapes if cmds.nodeType(shape) not in {"mesh", "nurbsCurve"}]
    if unsupported:
        raise ValueError("Convert unsupported shapes before export: " + ", ".join(unsupported))
    return sorted(result), [shape for shape in shapes if cmds.nodeType(shape) == "mesh"]


def export_job(job_path):
    import maya.cmds as cmds
    import maya.mel as mel
    import maya.api.OpenMaya as om

    job = _job(job_path)
    nodes, meshes = _nodes(cmds, job.roots)
    bake = bool(job.raw.get("bake_animation", False))
    if job.clips and not bake:
        raise ValueError("Declared clips require bake_animation=true")
    if not cmds.pluginInfo("fbxmaya", query=True, loaded=True):
        cmds.loadPlugin("fbxmaya", quiet=True)
    job.prepare()
    old_selection = cmds.ls(selection=True, long=True) or []
    old_time = cmds.currentTime(query=True)
    old_modified = cmds.file(query=True, modified=True)
    changed_files = []
    pushed = False
    warnings = [
        "Autodesk FBX is not the engine's Blender metallic/roughness convention. Normalize in Blender or supply prepacked LabPBR companions.",
        "Only regular file textures are copied. Arnold/Redshift/V-Ray/UDIM/procedural graphs must be baked to UV0 images first.",
        "FBX bakes evaluated motion; Maya's controller/constraint graph is not a runtime rig definition.",
    ]
    try:
        engines = sorted({engine for mesh in meshes for engine in cmds.listConnections(mesh, type="shadingEngine") or []})
        shaders = sorted({shader for engine in engines
                          for shader in cmds.listConnections(engine + ".surfaceShader", source=True, destination=False) or []})
        file_nodes = sorted({node for shader in shaders for node in cmds.listHistory(shader) or [] if cmds.nodeType(node) == "file"})
        material_inventory = [{"name": shader, "type": cmds.nodeType(shader)} for shader in shaders]
        for node in file_nodes:
            if cmds.getAttr(node + ".useFrameExtension") or cmds.getAttr(node + ".uvTilingMode"):
                raise ValueError("Bake sequence/UDIM texture into regular UV0 images first: " + node)
            original = cmds.getAttr(node + ".fileTextureName")
            source = Path(os.path.expandvars(cmds.workspace(expandName=original)))
            if not source.is_file():
                raise FileNotFoundError("Missing texture: " + node + " / " + str(source))
            destination = job.copy_texture(source)
            changed_files.append((node, original))
            cmds.setAttr(node + ".fileTextureName", str(destination).replace("\\", "/"), type="string")
        mel.eval("FBXPushSettings;")
        pushed = True
        mel.eval("FBXResetExport;")
        for command, enabled in {
            "FBXExportInAscii": False, "FBXExportSkins": True, "FBXExportShapes": True,
            "FBXExportConstraints": False, "FBXExportCameras": False, "FBXExportLights": False,
            "FBXExportIncludeChildren": False, "FBXExportInputConnections": False,
            "FBXExportBakeComplexAnimation": bake,
            "FBXExportEmbeddedTextures": bool(job.raw.get("embed_textures", True)),
            "FBXExportTriangulate": bool(job.raw.get("triangulate", True)),
        }.items():
            mel.eval(command + " -v " + str(enabled).lower() + ";")
        mel.eval('FBXProperty "Export|IncludeGrp|Animation" -v ' + str(bake).lower() + ";")
        mel.eval("FBXExportUpAxis y;")
        mel.eval("FBXExportScaleFactor 1;")
        mel.eval("FBXExportSplitAnimationIntoTakes -c;")
        fps = 1.0 / om.MTime(1, om.MTime.uiUnit()).asUnits(om.MTime.kSeconds)
        start = min((float(clip["start"]) for clip in job.clips), default=cmds.playbackOptions(query=True, minTime=True))
        end = max((float(clip["end"]) for clip in job.clips), default=cmds.playbackOptions(query=True, maxTime=True))
        if bake:
            mel.eval("FBXExportBakeComplexStart -v " + str(start) + ";")
            mel.eval("FBXExportBakeComplexEnd -v " + str(end) + ";")
            mel.eval("FBXExportBakeComplexStep -v " + str(int(job.raw.get("frame_step", 1))) + ";")
        clips = []
        for clip in job.clips:
            mel.eval("FBXExportSplitAnimationIntoTakes -v " + _quote(clip["name"]) + " " +
                     str(float(clip["start"])) + " " + str(float(clip["end"])) + ";")
            clips.append({"name": clip["name"], "start": clip["start"], "end": clip["end"], "fps": fps})
        if bake and not clips:
            clips.append({"name": "Take001", "start": start, "end": end, "fps": fps})
        if bake and job.clips:
            warnings.append("The FBX plugin may retain an additional Take001; inspect imported clip names and ranges.")
        cmds.select(nodes, replace=True, noExpand=True)
        mel.eval("FBXExport -f " + _quote(str(job.model_path).replace("\\", "/")) + " -s;")
        return job.finish({
            "application": "Maya", "application_version": cmds.about(version=True),
            "source_file": cmds.file(query=True, sceneName=True),
            "units": {"linear": cmds.currentUnit(query=True, linear=True),
                      "source_up_axis": cmds.upAxis(query=True, axis=True), "export_up_axis": "Y",
                      "policy": "FBX source units; no destructive scene unit/transform changes"},
            "nodes": [node.rsplit("|", 1)[-1] for node in nodes],
            "bones": [node.rsplit("|", 1)[-1] for node in nodes if cmds.nodeType(node) == "joint"],
            "clips": clips, "materials": material_inventory, "warnings": warnings,
        })
    finally:
        for node, original in changed_files:
            cmds.setAttr(node + ".fileTextureName", original, type="string")
        cmds.currentTime(old_time, edit=True)
        if old_selection:
            cmds.select(old_selection, replace=True, noExpand=True)
        else:
            cmds.select(clear=True)
        if pushed:
            mel.eval("FBXPopSettings;")
        cmds.file(modified=old_modified)
