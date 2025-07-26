@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_NAME=com.github.catfriend1.syncthinglite.debug"
::
adb shell cat "/data/data/%PACKAGE_NAME%/files/config.json"
::
goto :eof
