#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

MANIFEST="tools/native/manifest.txt"
DEST="src/main/resources/native"

if [ ! -f "$MANIFEST" ]; then
    echo "[NATIVE][ERROR] Manifest not found: $MANIFEST" >&2
    exit 1
fi

echo "[NATIVE] Reading $MANIFEST"
while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
        ''|'#'*) continue ;;
    esac
    IFS='|' read -r NAME SOURCE WIN_BUILD UNIX_BUILD DIST LIST <<< "$line"
    echo "[NATIVE] Module: $NAME (source=$SOURCE)"

    if [ -n "$UNIX_BUILD" ]; then
        if [ ! -f "$SOURCE/$UNIX_BUILD" ]; then
            echo "[NATIVE][ERROR] Build script not found: $SOURCE/$UNIX_BUILD" >&2
            exit 1
        fi
        ( cd "$SOURCE" && bash "$UNIX_BUILD" ) || {
            echo "[NATIVE][ERROR] Native build failed for '$NAME'. Configure the native toolchain manually (for FabriKNative: install Zig), then rerun this script." >&2
            exit 1
        }
    fi

    IFS=',' read -r -a artifacts <<< "$LIST"
    for entry in "${artifacts[@]}"; do
        SRC="${entry%%>*}"
        DST="${entry#*>}"
        SRC_PATH="$SOURCE/$DIST/$SRC"
        if [ ! -f "$SRC_PATH" ]; then
            echo "[NATIVE][ERROR] Missing artifact $SRC_PATH for '$NAME'. Build failed or toolchain not configured." >&2
            exit 1
        fi
        OUT_PATH="$DEST/$NAME/$DST"
        mkdir -p "$(dirname "$OUT_PATH")"
        cp -f "$SRC_PATH" "$OUT_PATH"
        echo "[NATIVE]   $SRC -> native/$NAME/$DST"
    done
done < "$MANIFEST"

echo "[NATIVE] All native modules built and copied successfully"
