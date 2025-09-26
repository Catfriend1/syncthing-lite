@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET /P PACKAGE_NAME=< "%PROJECT_ROOT%\scripts\debug\package_id.txt"
::
call stop.cmd
::
:: adb shell rm -rf /data/data/%PACKAGE_NAME%/cache/
adb shell rm -rf /storage/emulated/0/Android/data/%PACKAGE_NAME%/cache/
::
adb shell am start -n "%PACKAGE_NAME%/net.syncthing.lite.activities.MainActivity"
::
goto :eof
