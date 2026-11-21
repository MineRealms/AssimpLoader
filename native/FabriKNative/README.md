# FabriKNative

This is the C++ FABRIK implementation used by AssimpLoader's optional native IK
backend. The Java side calls the stable C ABI in `include/fabrik.h`; it does not
depend on JNI or on C++ standard library objects crossing the boundary.

Normal AssimpLoader builds package the checked-in platform libraries under
`src/main/resources/native/fabrik` and do not compile this directory. To rebuild
all eight release targets, install Zig and run `build_all.bat` from this folder,
then build AssimpLoader with `-PbuildNative`:

```powershell
.\build_all.bat
..\..\gradlew.bat :1.21.1-neoforge:jar -PbuildNative --no-daemon --console=plain
```

`-PfabrikNativeDir=<path>` or `FABRIK_NATIVE_DIR` can override this vendored
source directory. `build.bat` builds the host DLL and its native test with MSVC;
`build_jni.bat` is retained for JNI experiments and is not part of the JNA
runtime path. Do not commit `dist/`, compiler output, Zig downloads or local
toolchain directories.
