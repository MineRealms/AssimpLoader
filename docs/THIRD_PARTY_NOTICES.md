# Third-Party Notices

Source and license paths below are relative to the repository root. This document is packaged as `META-INF/THIRD_PARTY_NOTICES.md` in the release jar; license texts remain under `META-INF/licenses/`.

## Character Physics Libraries

The release jar bundles unmodified Maven artifacts `com.github.stephengold:jbullet:1.0.3`,
`com.github.stephengold:stack-alloc:1.0.2` and `javax.vecmath:vecmath:1.5.2`.
JBullet and stack-alloc are by Martin Dvorak; Bullet is by Erwin Coumans.
Their zlib notices are retained in `licenses/JBullet-Zlib.txt` and
`licenses/StackAlloc-Zlib.txt`. Sources: [JBullet](https://github.com/stephengold/jbullet)
and [stack-alloc](https://github.com/stephengold/stack-alloc).

Vecmath is by Sun Microsystems and contributors. It is GPL version 2 with the
Classpath exception; the license and copyright notices are retained in
`licenses/Vecmath-GPL2-Classpath.txt` and `licenses/Vecmath-Copyright.txt`.
Its exact Maven 1.5.2 source archive accompanies the binary inside the release jar
at `META-INF/third-party-sources/vecmath-1.5.2-sources.jar` and is also available
from [Maven Central](https://repo.maven.apache.org/maven2/javax/vecmath/vecmath/1.5.2/vecmath-1.5.2-sources.jar).
License text source: [vecmath repository](https://github.com/hharrison/vecmath).
AssimpLoader's adapters and generated block-player assets are independently authored.

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

The 1.20.1 Forge release bundles LWJGL Assimp 3.3.1 bindings and native libraries;
the 1.21.1 NeoForge release bundles 3.3.3 to match that Minecraft runtime.
See `licenses/LWJGL-BSD.txt` (retained from upstream LWJGL tag `3.3.1`) and
`licenses/Assimp-BSD.txt` (Assimp revision
`a34688230d152345d04710e256224b71ce8ba8a1`, recorded in the 3.3.1 native artifact).
The license texts are retained for both runtime versions; that historical
revision must not be read as a version claim for the 3.3.3 native binaries.
Upstream texts:
[LWJGL license](https://github.com/LWJGL/lwjgl3/blob/3.3.1/LICENSE.md),
[Assimp license](https://github.com/assimp/assimp/blob/a34688230d152345d04710e256224b71ce8ba8a1/LICENSE).
Their licenses and upstream third-party notices apply independently of the
AssimpLoader MIT license. Photon and LDLib are optional separate mods and are
resolved for compilation and development only; they are not bundled in the
release jar. Oculus and Embeddium are separate development/runtime mods for
1.20.1 Forge; Iris and Sodium serve that role on 1.21.1 NeoForge. Minecraft and
the corresponding Forge or NeoForge loader are supplied by the game installation.
The current model browser and workbench use native Minecraft screens. The unused
ModernUI node-editor sources and ModernUI dependencies have been removed;
AssimpLoader does not require ModernUI, glsl-transformer or ANTLR.

KubeJS, Rhino and Architectury are separate optional mods for scripting
integration. They are compile-only dependencies and are added to development
runs only when explicitly requested; their binaries and sources are not bundled.
