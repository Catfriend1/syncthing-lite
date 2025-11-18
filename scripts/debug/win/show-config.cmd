@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
SET /P PACKAGE_NAME=< "%PROJECT_ROOT%\scripts\debug\package_id.txt"
::
:: adb root
::
adb shell cat "/data/data/%PACKAGE_NAME%/files/config.json"
echo.&echo.
::
:: call :showCerts
::
:: adb unroot
::
goto :eof


:showCerts
::
adb shell cat "/data/data/%PACKAGE_NAME%/files/cert.pem"
echo.&echo.
adb shell cat "/data/data/%PACKAGE_NAME%/files/key.pem"
echo.
::
goto :eof
