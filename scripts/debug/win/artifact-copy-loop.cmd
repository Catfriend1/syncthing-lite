@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
::
:loopMe
copy /y "%PROJECT_ROOT%\app\build\outputs\apk\debug\app-debug.apk" "\\vmware-host\Shared Folders\Shared\app-debug.apk"
::
call hide-folders-from-notepad++.cmd
::
timeout 3
goto :loopMe
::
goto :eof
