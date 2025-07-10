@echo off
setlocal enabledelayedexpansion
::
SET "ARTIFACT_PATH=X:\Shared"
::
adb uninstall net.syncthing.lite
::
dir "%ARTIFACT_PATH%\app-debug.apk"
adb install -r "%ARTIFACT_PATH%\app-debug.apk"
::
goto :eof
