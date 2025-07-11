@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_NAME=net.syncthing.lite"
::
adb shell am force-stop "%PACKAGE_NAME%"
adb shell am start -n "%PACKAGE_NAME%/.activities.MainActivity"
::
goto :eof
