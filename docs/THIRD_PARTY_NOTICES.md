# Third-Party Notices

Source and license paths below are relative to the repository root. This document is packaged as `META-INF/THIRD_PARTY_NOTICES.md` in the release jar; license texts remain under `META-INF/licenses/`.

## Caliko

The vendored `src/main/java/au/edu/federation/caliko/` and
`src/main/java/au/edu/federation/utils/` sources originate from
[FedUni/caliko](https://github.com/FedUni/caliko). Original authors include
Alastair Lansley and Federation University Australia. These files are MIT
licensed; see `licenses/Caliko-MIT.txt` (also packaged under `META-INF/licenses`).
The exact imported upstream revision was not recorded by this repository;
individual source version headers are retained. Do not infer a release number
from the current upstream branch. This copy has local integration changes.

## Gaussian Splat Shader

The Gaussian shader derives from
[limacv/GaussianSplattingViewer](https://github.com/limacv/GaussianSplattingViewer).
Its original MIT attribution to Li Ma (2023) is retained in the shader source.
The full text is in `licenses/GaussianSplattingViewer-MIT.txt`.

## Runtime Libraries

LWJGL Assimp 3.3.1 bindings and native libraries are bundled in the all jar.
See `licenses/LWJGL-BSD.txt` (upstream LWJGL tag `3.3.1`) and
`licenses/Assimp-BSD.txt` (Assimp revision
`a34688230d152345d04710e256224b71ce8ba8a1`, recorded in the native artifact).
Upstream texts:
[LWJGL license](https://github.com/LWJGL/lwjgl3/blob/3.3.1/LICENSE.md),
[Assimp license](https://github.com/assimp/assimp/blob/a34688230d152345d04710e256224b71ce8ba8a1/LICENSE).
Their licenses and upstream third-party notices apply independently of the
AssimpLoader MIT license. ModernUI is a separate required mod. Photon and LDLib
are optional separate mods and are downloaded for development only; they are
not bundled in the release jar. Minecraft and Forge are supplied by the game
installation. The dormant node-editor facade no longer links glsl-transformer
or ANTLR at compile time or runtime.

KubeJS, Rhino and Architectury are separate optional mods for scripting
integration. They are compile-only dependencies and are added to development
runs only when explicitly requested; their binaries and sources are not bundled.
