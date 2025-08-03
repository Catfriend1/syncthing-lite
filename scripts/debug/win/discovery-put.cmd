@echo off
setlocal enabledelayedexpansion
::
SET CURL="%ProgramFiles%\SupportPack\curl.exe"
::
%CURL% --version
%CURL% -k -X POST "https://discovery.syncthing.net/v3" ^
  --cert "%LocalAppData%\Syncthing\cert.pem" ^
  --key "%LocalAppData%\Syncthing\key.pem" ^
  -H "Content-Type: application/json" ^
  -d "{\"id\":\"%SYNCTHING_TEST_DEVICE_ID%\",\"addresses\":[\"tcp://192.168.1.100:22000\"],\"expiration\":120}"
::
goto :eof
