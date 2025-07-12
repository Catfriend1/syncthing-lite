@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_NAME=com.github.catfriend1.syncthinglite.debug"
::
adb shell am force-stop "%PACKAGE_NAME%"
adb shell am start -n "%PACKAGE_NAME%/net.syncthing.lite.activities.MainActivity"
::
goto :eof
