@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET /P PACKAGE_NAME=< "%PROJECT_ROOT%\scripts\debug\package_id.txt"
::
adb push "%SCRIPT_PATH%cert.pem" "/data/data/%PACKAGE_NAME%/files/cert.pem"
adb push "%SCRIPT_PATH%key.pem" "/data/data/%PACKAGE_NAME%/files/key.pem"
::
goto :eof
