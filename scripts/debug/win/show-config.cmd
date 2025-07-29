@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_NAME=com.github.catfriend1.syncthinglite.debug"
::
adb shell cat "/data/data/%PACKAGE_NAME%/files/config.json"
echo.&echo.
::
adb shell cat "/data/data/%PACKAGE_NAME%/files/cert.pem"
echo.&echo.
adb shell cat "/data/data/%PACKAGE_NAME%/files/key.pem"
echo.
::
goto :eof
