# Client Asset Acceptance

The generic Showcase smoke run is disabled by default. It does not enable the older
`autotest` runners. Use a dedicated existing single-player test world; this run
leaves a stone display platform, the selected Showcase block, spectator mode, clear
weather, and fixed midday in that world. It searches loaded air above the player
and refuses to replace existing blocks. It supports assets up to 128 blocks per
axis for this automatic platform scan; this is not a renderer size limit.

```powershell
.\gradlew.bat runClient -PclientAssetSmoke=AssetTest -PclientAssetModel=PROBE_TEXTURED
```

`clientAssetSmoke` is a save directory name under the configured game directory's
`saves` folder, not an absolute path. `clientAssetModel` is a `ShowcaseModel` enum
name and defaults to `PROBE_TEXTURED`. Enable the desired local resource pack in
that game directory before running; a pack can override the existing model and
its relative texture files without adding licensed assets to this repository.
Do not combine this option with `clientSmokeWorld`, `clientStartupSmoke`, or
`clientPbrSmoke`. The client remains open after success or failure.

The run captures five images in the game directory's `screenshots` folder:
`assimp-asset-smoke-<run>-<model>-overview.png`, `edge-near`, `edge-far`,
`reload-overview`, and `reload-edge-far`. The overview uses the imported geometry's
actual transformed bounds and the viewport aspect ratio. Edge cameras approach
opposite ends of the longest horizontal axis, ordered by distance from the anchor.
For centered assets both ends may be equally distant. Reload uses Minecraft's
normal resource reload API, the same operation triggered by F3+T.

Look for `[AssetSmoke] SUCCESS` in `logs/latest.log`, then inspect all five images.
Diagnostics log the real source and geometry bounds, every used material's albedo,
normal/roughness/metallic references and factors, prepared normal/specular sources,
GL texture IDs, shader state, and whether the anchor is in each camera frame.
Each screenshot requires ready material textures, no pending GL error, and the
actual block entity render box containing the independently calculated geometry.
After reload, scene and material texture identities must be new. Oculus bindings
are checked when a shader pack and AssimpLoader PBR are enabled; the run also
supports ordinary assets and does not enable a shader pack or force PBR settings.
These diagnostics do not replace checking the screenshots for the intended art.

All world changes run on the integrated server thread. Texture diagnostics and
screenshots run through normal client tick/render events; no remote render-thread
execution is used. GUI visibility, FOV, camera mode, view bobbing, and focus-pause
settings are restored at the end, and the camera returns to the whole asset.
The Showcase anchor chunk must stay loaded for rendering, including when
`rendering.alwaysRenderLargeModels` is enabled; see [configuration](CONFIG_USER_GUIDE.md).

## Armored Car Acceptance

Enable the local `ArmoredCar_Local_PBR` resource pack, then run:

```powershell
.\tools\verification\start-vehicle-client.ps1 -World Scaffold_PBR_Test
```

This uses `clientVehicleSmoke`, independently of the other smoke runners. It
creates a platform and a mounted car in the selected test world. The game entity
scales the reference FBX by 2, giving width 4, length 9 and height 5 blocks. The
test checks real acceleration, coasting, braking, reverse motion, steering, wheel
roll, PBR textures, third-person camera placement, GL errors, cached Oculus draws,
and resource reload. Capture views are held only while taking screenshots.

Look for `[VehicleSmoke] SUCCESS` in `logs/armored-car-client.log` and inspect
`run/screenshots/assimp-vehicle-*.png`. The client stays open after completion;
W/S/A/D drive, Space brakes, and Shift dismounts. See [vehicles](VEHICLES.md) for
the asset, wheel-pivot and physics contracts.
