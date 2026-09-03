@echo off
rem ---------------------------------------------------------------------------
rem  Daybook - build an installable debug APK on Windows.
rem
rem  Double-click this file, or run it from a terminal in the project root.
rem  It needs a JDK 17+ and an Android SDK; it will tell you exactly what is
rem  missing rather than failing with a wall of Gradle output.
rem
rem  The real work is in scripts\build-apk.ps1. This wrapper exists so the
rem  script can be double-clicked and so the window stays open on failure.
rem ---------------------------------------------------------------------------

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-apk.ps1" %*
set EXITCODE=%ERRORLEVEL%

if not "%EXITCODE%"=="0" (
    echo.
    echo Build failed with exit code %EXITCODE%.
    echo Read the messages above, then see INSTALL.md for the other two ways
    echo to get an APK.
)

rem Only pause when launched by double-click, so CI and terminals are unaffected.
echo %CMDCMDLINE% | find /i "/c" >nul
if not errorlevel 1 pause

exit /b %EXITCODE%
