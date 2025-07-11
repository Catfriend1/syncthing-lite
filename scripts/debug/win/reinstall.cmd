@echo off
setlocal enabledelayedexpansion
::
adb uninstall net.syncthing.lite
::
call update.cmd
::
goto :eof
