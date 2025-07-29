@echo off
setlocal enabledelayedexpansion
::
SET "PACKAGE_NAME=com.github.catfriend1.syncthinglite.debug"
::
adb push "%~dp0cert.pem" "/data/data/%PACKAGE_NAME%/files/cert.pem"
adb push "%~dp0key.pem" "/data/data/%PACKAGE_NAME%/files/key.pem"
::
goto :eof
