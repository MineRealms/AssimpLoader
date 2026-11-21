@echo off
setlocal
set "ROOT=%~dp0"
set "VCVARS=C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
set "JDK=C:\Program Files\Java\jdk-21"
if not exist "%ROOT%build" mkdir "%ROOT%build"
call "%VCVARS%" || exit /b 1
cd /d "%ROOT%build"

echo Building fabrik_jni.dll ...
cl /nologo /std:c++20 /O2 /arch:AVX /EHsc /D_CRT_SECURE_NO_WARNINGS /LD ^
   /I"%JDK%\include" /I"%JDK%\include\win32" ^
   /I"%ROOT%include" /I"%ROOT%src" "%ROOT%src\jni_bridge.cpp" ^
   /Fo"jni_bridge.obj" /Fe"fabrik_jni.dll" fabrik.lib || exit /b 1

echo JNI BUILD OK
endlocal
