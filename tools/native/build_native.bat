@echo off
setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"
pushd "%SCRIPT_DIR%..\.." || exit /b 1

set "MANIFEST=tools\native\manifest.txt"
set "DEST=src\main\resources\native"

if not exist "%MANIFEST%" (
    echo [NATIVE][ERROR] Manifest not found: %MANIFEST%
    popd
    exit /b 1
)

echo [NATIVE] Reading %MANIFEST%
for /f "usebackq eol=# tokens=1,2,3,4,5,6 delims=|" %%A in ("%MANIFEST%") do (
    call :module "%%A" "%%B" "%%C" "%%D" "%%E" "%%F"
    if errorlevel 1 (
        popd
        exit /b 1
    )
)

popd
echo [NATIVE] All native modules built and copied successfully
exit /b 0

:module
set "NAME=%~1"
set "SOURCE=%~2"
set "WIN_BUILD=%~3"
set "DIST=%~5"
set "LIST=%~6"
echo [NATIVE] Module: %NAME% (source=%SOURCE%)

if not "%WIN_BUILD%"=="" (
    if not exist "%SOURCE%\%WIN_BUILD%" (
        echo [NATIVE][ERROR] Build script not found: %SOURCE%\%WIN_BUILD%
        exit /b 1
    )
    pushd "%SOURCE%" || exit /b 1
    call "%WIN_BUILD%"
    set "BUILD_ERR=!errorlevel!"
    popd
    if not "!BUILD_ERR!"=="0" (
        echo [NATIVE][ERROR] Native build failed for '%NAME%'. Configure the native toolchain manually ^(for FabriKNative: install Zig and run download_zig.bat^), then rerun this script.
        exit /b 1
    )
)

set "LIST=!LIST:,= !"
for %%I in (!LIST!) do (
    for /f "tokens=1,2 delims=>" %%x in ("%%I") do (
        set "SRC=%%x"
        set "DST=%%y"
        set "DST=!DST:/=\!"
        set "SRC_PATH=%SOURCE%\%DIST%\!SRC!"
        if not exist "!SRC_PATH!" (
            echo [NATIVE][ERROR] Missing artifact !SRC_PATH! for '%NAME%'. Build failed or toolchain not configured.
            exit /b 1
        )
        for %%D in ("!DST!") do if not exist "%DEST%\%NAME%\%%~dpD" mkdir "%DEST%\%NAME%\%%~dpD"
        copy /Y "!SRC_PATH!" "%DEST%\%NAME%\!DST!" >nul
        echo [NATIVE]   !SRC! -^> native\%NAME%\!DST!
    )
)
exit /b 0
