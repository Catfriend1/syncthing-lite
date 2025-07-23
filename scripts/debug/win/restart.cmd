@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_NAME=com.github.catfriend1.syncthinglite.debug"
::
call stop.cmd
::
:: adb shell rm -rf /data/data/%PACKAGE_NAME%/cache/
adb shell rm -rf /storage/emulated/0/Android/data/%PACKAGE_NAME%/cache/
::
adb shell am start -n "%PACKAGE_NAME%/net.syncthing.lite.activities.MainActivity"
::
goto :eof
