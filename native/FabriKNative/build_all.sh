#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST="$ROOT/dist"
ZIG="${ZIG:-zig}"
mkdir -p "$DIST"

if ! command -v "$ZIG" >/dev/null 2>&1; then
    echo "[ERROR] zig not found; install Zig or set ZIG=/path/to/zig" >&2
    exit 1
fi

build() {
    local target="$1"
    local output="$2"
    shift 2
    echo "[BUILD] $target -> $output"
    "$ZIG" c++ --target="$target" "$@" -std=c++20 -O2 -w -shared -fPIC \
        -I"$ROOT/include" -I"$ROOT/src" "$ROOT/src/c_api.cpp" -o "$DIST/$output"
}

build x86_64-windows-gnu fabrik-windows-x86_64.dll -mavx -DFK_BUILDING_DLL
build aarch64-windows-gnu fabrik-windows-aarch64.dll -DFK_BUILDING_DLL
build x86-windows-gnu fabrik-windows-x86.dll -msse2 -DFK_BUILDING_DLL
build x86_64-linux-gnu fabrik-linux-x86_64.so -mavx
build aarch64-linux-gnu fabrik-linux-aarch64.so
build arm-linux-gnueabihf fabrik-linux-armv7.so -mfpu=neon
build x86_64-macos.11.0.0-none fabrik-macos-x86_64.dylib -mavx
build aarch64-macos.11.0.0-none fabrik-macos-aarch64.dylib

echo "ALL PLATFORM BUILDS OK"
