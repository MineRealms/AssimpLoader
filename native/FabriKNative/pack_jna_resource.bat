@echo off
setlocal
set "ROOT=%~dp0"
set "DIST=%ROOT%dist"
set "OUT=%ROOT%jna-resources"
if not exist "%DIST%" (
    echo [ERROR] run build_all.bat first
    exit /b 1
)

call :pack win32-x86-64     fabrik-windows-x86_64.dll   fabrik.dll
call :pack win32-x86        fabrik-windows-x86.dll      fabrik.dll
call :pack win32-arm64      fabrik-windows-aarch64.dll  fabrik.dll
call :pack linux-x86-64     fabrik-linux-x86_64.so      libfabrik.so
call :pack linux-aarch64    fabrik-linux-aarch64.so     libfabrik.so
call :pack linux-arm        fabrik-linux-armv7.so       libfabrik.so
call :pack darwin-x86-64    fabrik-macos-x86_64.dylib   libfabrik.dylib
call :pack darwin-aarch64   fabrik-macos-aarch64.dylib  libfabrik.dylib

echo.
echo JNA RESOURCE TREE READY: %OUT%
echo Copy the whole tree into your project's src/main/resources/
endlocal
exit /b 0

:pack
set "DIR=%OUT%\%~1"
set "SRC=%DIST%\%~2"
set "DST=%DIR%\%~3"
if not exist "%DIR%" mkdir "%DIR%"
if not exist "%SRC%" (
    echo [WARN] missing %SRC%
    exit /b 0
)
copy /Y "%SRC%" "%DST%" >nul
echo [OK] %~1 -^> %~3
exit /b 0
