@echo off
setlocal
set ROOT=%~dp0
set VCVARS="C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%ROOT%build" mkdir "%ROOT%build"
call %VCVARS% || exit /b 1
cd /d "%ROOT%build"

echo [1/3] Building fabrik.dll ...
cl /nologo /std:c++20 /O2 /arch:AVX /EHsc /D_CRT_SECURE_NO_WARNINGS /DFK_BUILDING_DLL /LD ^
   /I"%ROOT%include" /I"%ROOT%src" "%ROOT%src\c_api.cpp" ^
   /Fo"c_api.obj" /Fe"fabrik.dll" || exit /b 1

echo [2/3] Building test_fabrik.exe ...
cl /nologo /std:c++20 /O2 /arch:AVX /EHsc /D_CRT_SECURE_NO_WARNINGS ^
   /I"%ROOT%include" /I"%ROOT%src" "%ROOT%tests\test_fabrik.cpp" ^
   /Fo"test_fabrik.obj" /Fe"test_fabrik.exe" fabrik.lib || exit /b 1

echo [3/3] Running tests ...
test_fabrik.exe || exit /b 1

echo BUILD OK
endlocal

