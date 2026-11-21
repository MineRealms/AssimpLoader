@echo off
setlocal enabledelayedexpansion
set "ROOT=%~dp0"
set "DIST=%ROOT%dist"
if not exist "%DIST%" mkdir "%DIST%"

if not defined ZIG (
    for /f "delims=" %%Z in ('dir /s /b "%ROOT%.tools\zig.exe" 2^>nul') do set "ZIG=%%Z"
)
if not defined ZIG (
    for /f "delims=" %%Z in ('where zig.exe 2^>nul') do set "ZIG=%%Z"
)
if not defined ZIG (
    echo [ERROR] Zig not found - run download_zig.bat, add Zig to PATH, or set ZIG to zig.exe
    exit /b 1
)

echo Using %ZIG%

call :build x86_64-windows-gnu   fabrik-windows-x86_64.dll   "-mavx -DFK_BUILDING_DLL" || exit /b 1
call :build aarch64-windows-gnu  fabrik-windows-aarch64.dll  "-DFK_BUILDING_DLL" || exit /b 1
call :build x86-windows-gnu      fabrik-windows-x86.dll      "-msse2 -DFK_BUILDING_DLL" || exit /b 1
call :build x86_64-linux-gnu     fabrik-linux-x86_64.so      "-mavx" || exit /b 1
call :build aarch64-linux-gnu    fabrik-linux-aarch64.so     "" || exit /b 1
call :build arm-linux-gnueabihf  fabrik-linux-armv7.so       "-mfpu=neon" || exit /b 1
call :build x86_64-macos.11.0.0-none fabrik-macos-x86_64.dylib "-mavx" || exit /b 1
call :build aarch64-macos.11.0.0-none fabrik-macos-aarch64.dylib "" || exit /b 1

echo.
echo ALL PLATFORM BUILDS OK
dir "%DIST%"
endlocal
exit /b 0

:build
set "TARGET=%~1"
set "OUTPUT=%~2"
set "EXTRA=%~3"
echo [BUILD] %TARGET% -^> %OUTPUT%
"%ZIG%" c++ --target=%TARGET% %EXTRA% -std=c++20 -O2 -w -shared -fPIC ^
    -I"%ROOT%include" -I"%ROOT%src" "%ROOT%src\c_api.cpp" -o "%DIST%\%OUTPUT%"
if errorlevel 1 (
    echo [FAILED] %TARGET%
    exit /b 1
)
exit /b 0
