"""Shared bundle writer for the DCC adapters; uses only the Python standard library.

Run an adapter in its DCC, not this module. Generated metadata describes export
requests; only a subsequent Assimp import knows the final node/take names.
"""

import hashlib
import json
import math
import os
import re
import shutil
import tempfile
from collections import Counter
from pathlib import Path


INTENTS = {
    "idle", "walk", "run", "walk_backward", "strafe_left", "strafe_right",
    "crouch", "crouch_walk", "jump", "fall", "swim", "fly", "elytra",
    "ride", "sleep", "dead", "use_item", "swing", "crawl", "crawl_idle",
    "ladder_up", "ladder_down", "ladder_idle", "water_idle", "lava",
    "riptide", "frozen", "hurt", "land",
}

HUMANOID_ALIASES = {
    "hips": ("hips", "pelvis", "bip001pelvis"),
    "spine": ("spine", "bip001spine"),
    "chest": ("chest", "spine1", "bip001spine1"),
    "upper_chest": ("upperchest", "spine2", "bip001spine2"),
    "neck": ("neck", "bip001neck"), "head": ("head", "bip001head"),
    "left_upper_arm": ("upperarml", "leftarm", "bip001lupperarm"),
    "right_upper_arm": ("upperarmr", "rightarm", "bip001rupperarm"),
    "left_forearm": ("forearml", "leftforearm", "bip001lforearm"),
    "right_forearm": ("forearmr", "rightforearm", "bip001rforearm"),
    "left_hand": ("handl", "lefthand", "bip001lhand"),
    "right_hand": ("handr", "righthand", "bip001rhand"),
    "left_thigh": ("thighl", "leftupleg", "bip001lthigh"),
    "right_thigh": ("thighr", "rightupleg", "bip001rthigh"),
    "left_shin": ("shinl", "leftleg", "bip001lcalf"),
    "right_shin": ("shinr", "rightleg", "bip001rcalf"),
    "left_foot": ("footl", "leftfoot", "bip001lfoot"),
    "right_foot": ("footr", "rightfoot", "bip001rfoot"),
}


def _number(value, label, minimum, maximum):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(label + " must be a number")
    if not math.isfinite(value) or not minimum <= value <= maximum:
        raise ValueError(label + " is out of range")
    return value


def _atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix="." + path.name, suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            json.dump(value, stream, ensure_ascii=True, indent=2, sort_keys=True, allow_nan=False)
            stream.write("\n")
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def _slug(value):
    result = re.sub(r"[^a-z0-9_.-]+", "_", value.lower()).strip("._-")
    return result or "texture"


def _semantic(value):
    return re.sub(r"[^a-z0-9]", "", value.rsplit("|", 1)[-1].rsplit(":", 1)[-1].lower())


class ExportJob:
    def __init__(self, job_file, raw):
        if not isinstance(raw, dict) or raw.get("version") != 1:
            raise ValueError("Export job must be an object with version=1")
        self.job_file = Path(job_file).resolve()
        self.raw = raw
        self.namespace = raw.get("namespace", "")
        self.asset_id = raw.get("asset_id", "")
        if (not isinstance(self.namespace, str) or not re.fullmatch(r"[a-z0-9_.-]+", self.namespace)
                or self.namespace in {".", ".."}):
            raise ValueError("namespace must use lowercase Minecraft resource characters")
        if not isinstance(self.asset_id, str) or not re.fullmatch(r"[a-z0-9_-]+", self.asset_id):
            raise ValueError("asset_id must use lowercase letters, digits, underscores or hyphens")
        output = raw.get("output_dir")
        if not isinstance(output, str) or not output.strip():
            raise ValueError("output_dir is required; relative paths are relative to the job file")
        target = Path(os.path.expandvars(os.path.expanduser(output)))
        self.output_dir = (self.job_file.parent / target).resolve() if not target.is_absolute() else target.resolve()
        self.asset_dir = self.output_dir / "assets" / self.namespace / "models" / self.asset_id
        self.model_path = self.asset_dir / "model.fbx"
        self.model_id = self.namespace + ":models/" + self.asset_id + "/model.fbx"
        self.roots = raw.get("roots", [])
        if not isinstance(self.roots, list) or any(not isinstance(value, str) or not value for value in self.roots):
            raise ValueError("roots must be a list of object names")
        self.clips = raw.get("clips", [])
        if not isinstance(self.clips, list):
            raise ValueError("clips must be an array")
        seen = set()
        for clip in self.clips:
            if not isinstance(clip, dict) or not isinstance(clip.get("name"), str) or not clip["name"].strip():
                raise ValueError("Each clip needs a nonempty name")
            if clip["name"] in seen:
                raise ValueError("Duplicate clip name: " + clip["name"])
            seen.add(clip["name"])
            start = _number(clip.get("start"), "clip.start", -1000000, 1000000)
            end = _number(clip.get("end"), "clip.end", -1000000, 1000000)
            if end <= start:
                raise ValueError("clip.end must be after clip.start")
        step = _number(raw.get("frame_step", 1), "frame_step", 1, 120)
        if step != int(step):
            raise ValueError("frame_step must be an integer for all three DCC adapters")
        for name in ("bake_animation", "triangulate", "embed_textures", "overwrite", "overwrite_configs"):
            if name in raw and not isinstance(raw[name], bool):
                raise ValueError(name + " must be boolean")
        if self.clips and not raw.get("bake_animation", False):
            raise ValueError("Clips require bake_animation=true")
        self.character = raw.get("character", {})
        if not isinstance(self.character, dict):
            raise ValueError("character must be an object")
        self._textures = {}
        self._warnings = []
        self._prepared = False

    @classmethod
    def load(cls, path):
        with Path(path).open("r", encoding="utf-8-sig") as stream:
            return cls(path, json.load(stream))

    def prepare(self):
        if self.model_path.exists() and not self.raw.get("overwrite", False):
            raise FileExistsError("Existing export; choose another output or set overwrite=true: " + str(self.model_path))
        self.asset_dir.mkdir(parents=True, exist_ok=True)
        self._prepared = True

    def copy_texture(self, source):
        if not self._prepared:
            raise RuntimeError("Call prepare before copying textures")
        source = Path(source).resolve()
        if not source.is_file():
            raise FileNotFoundError(str(source))
        if str(source) in self._textures:
            return Path(self._textures[str(source)])
        base = source.stem
        if base.lower().endswith(("_n", "_s")):
            base = base[:-2]
        group_key = str(source.parent) + "/" + base.lower()
        token = hashlib.sha256(group_key.encode("utf-8")).hexdigest()[:10]
        folder = self.asset_dir / "textures" / (_slug(base) + "-" + token)
        folder.mkdir(parents=True, exist_ok=True)
        # Copy prepacked LabPBR siblings together and keep their base/_n/_s names.
        family = {base.lower(), base.lower() + "_n", base.lower() + "_s"}
        companions = [item for item in source.parent.iterdir()
                      if item.is_file() and item.stem.lower() in family and item.suffix.lower() == source.suffix.lower()]
        for item in companions:
            destination = folder / (_slug(item.stem) + item.suffix.lower())
            if destination.exists() and item.resolve() != destination.resolve() and not self.raw.get("overwrite", False):
                raise FileExistsError("Texture already exists; set overwrite=true: " + str(destination))
            if item.resolve() != destination.resolve():
                shutil.copy2(str(item), str(destination))
            self._textures[str(item.resolve())] = str(destination)
        return Path(self._textures[str(source)])

    def _config(self, path, value):
        if path.exists() and not self.raw.get("overwrite_configs", False):
            self._warnings.append("Preserved existing configuration: " + str(path.relative_to(self.output_dir)))
            return
        _atomic_json(path, value)

    def _profile(self, inventory):
        if not self.character.get("enabled", False):
            return None
        bones = list(inventory.get("bones", []))
        nodes = list(inventory.get("nodes", []))
        names = bones + nodes
        known = set(names)
        ambiguous = {name for name, count in Counter(bones).items() if count > 1}
        ambiguous.update(name for name, count in Counter(nodes).items() if count > 1)
        mapped = self.character.get("humanoid", {})
        if not isinstance(mapped, dict):
            raise ValueError("character.humanoid must map semantic roles to actual node names")
        humanoid = dict(mapped)
        for role, node in humanoid.items():
            if not re.fullmatch(r"[a-z][a-z0-9_]{0,63}", role):
                raise ValueError("Invalid humanoid role: " + role)
            if not isinstance(node, str) or node not in known or node in ambiguous:
                raise ValueError("Humanoid node must exist uniquely; inspect DCC duplicate names: " + str(node))
        if self.character.get("auto_humanoid", True):
            for role, aliases in HUMANOID_ALIASES.items():
                if role in humanoid:
                    continue
                candidates = {name for name in known if name not in ambiguous and _semantic(name) in aliases}
                if len(candidates) == 1:
                    humanoid[role] = candidates.pop()
                elif len(candidates) > 1:
                    self._warnings.append("Ambiguous humanoid role requires manual mapping: " + role)
        required = ("hips", "head", "left_upper_arm", "right_upper_arm", "left_forearm", "right_forearm",
                    "left_hand", "right_hand", "left_thigh", "right_thigh", "left_shin", "right_shin")
        missing = [role for role in required if role not in humanoid]
        if missing:
            self._warnings.append("Character draft has unmapped roles; calibrate before Apply: " + ", ".join(missing))
        clips = {clip["name"] for clip in inventory.get("clips", [])}
        animations = dict(self.character.get("animations", {}))
        for intent, clip in animations.items():
            if intent not in INTENTS or clip not in clips:
                raise ValueError("Animation mapping must use a supported intent and an exported clip: " + str(intent))
        for intent in sorted(INTENTS):
            if intent not in animations:
                candidates = {clip for clip in clips if _semantic(clip) == _semantic(intent)}
                if len(candidates) == 1:
                    animations[intent] = candidates.pop()
        result = {
            "version": 1, "model": self.model_id,
            "scale": _number(self.character.get("scale", 1), "character.scale", .001, 100),
            "modelYawDegrees": _number(self.character.get("model_yaw_degrees", 180), "model_yaw_degrees", -36000, 36000),
            "humanoid": humanoid, "animations": animations, "blendSeconds": .15,
            "movementThreshold": .04,
        }
        self._warnings.append("Name suggestions do not calibrate bind axes, reference/native rotations, hand sockets or contact IK.")
        return result

    def finish(self, inventory):
        if not self._prepared or not self.model_path.is_file() or self.model_path.stat().st_size == 0:
            raise RuntimeError("DCC did not create a nonempty FBX")
        profile = self._profile(inventory)
        self._config(self.output_dir / "pack.mcmeta", {
            "pack": {"pack_format": 15, "description": "AssimpLoader DCC assets: " + self.namespace},
        })
        self._config(self.asset_dir / "geometry.json", {
            "loader": "assimploader:assimp", "model": self.model_id, "mode": "dynamic",
        })
        states = {"clip_" + str(index): {"clip": clip["name"], "loop": True, "speed": 1, "transition": .15}
                  for index, clip in enumerate(inventory.get("clips", []))}
        self._config(self.asset_dir / "model.instance.json", {"version": 1, "model": self.model_id, "states": states})
        if profile is not None:
            self._config(self.asset_dir / "profile.json", profile)
        checksum = hashlib.sha256()
        with self.model_path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                checksum.update(block)
        manifest = dict(inventory)
        manifest.update({
            "version": 1, "model": self.model_id, "model_sha256": checksum.hexdigest(),
            "model_file": str(self.model_path.relative_to(self.output_dir)).replace("\\", "/"),
            "profile": self.namespace + ":models/" + self.asset_id + "/profile.json" if profile else None,
            "textures": sorted({str(Path(value).relative_to(self.output_dir)).replace("\\", "/") for value in self._textures.values()}),
            "warnings": list(inventory.get("warnings", [])) + self._warnings,
            "verification": "Export completed; no Assimp import or Minecraft visual acceptance performed by this script.",
        })
        _atomic_json(self.asset_dir / "export-manifest.json", manifest)
        return {"model": str(self.model_path), "resource_pack": str(self.output_dir),
                "manifest": str(self.asset_dir / "export-manifest.json"), "profile": manifest["profile"],
                "warnings": manifest["warnings"]}
