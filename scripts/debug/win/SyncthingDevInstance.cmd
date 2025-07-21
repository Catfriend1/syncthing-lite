@echo off
setlocal enabledelayedexpansion
::
SET TASK_NAME="Syncthing\SyncthingDevInstance"
::
schtasks /run /tn "%TASK_NAME%"
::
timeout 3
::
goto :eof
