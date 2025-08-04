@echo off
setlocal enabledelayedexpansion
::
curl -k -X GET "https://discovery.syncthing.net/?device=%SYNCTHING_TEST_DEVICE_ID%"
::
goto :eof
