@echo off
setlocal
set "ROOT=%~dp0"
set "TOOLS=%ROOT%.tools"
if not exist "%TOOLS%" mkdir "%TOOLS%"
if exist "%TOOLS%\zig.exe" goto :done

echo Downloading Zig 0.13.0 toolchain ...
curl.exe -L --fail --retry 2 -o "%TOOLS%\zig.zip" "https://ziglang.org/download/0.13.0/zig-windows-x86_64-0.13.0.zip" || exit /b 1
"C:\Program Files\7-Zip\7z.exe" x "%TOOLS%\zig.zip" "-o%TOOLS%" -y -bso0 -bsp0 || exit /b 1
del "%TOOLS%\zig.zip"

:done
for /f "delims=" %%Z in ('dir /s /b "%TOOLS%\zig.exe" 2^>nul') do set "ZIG=%%Z"
"%ZIG%" version
echo ZIG READY: %ZIG%
endlocal
