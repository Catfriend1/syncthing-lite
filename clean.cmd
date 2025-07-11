@echo off
::
RD /S /Q ".gradle" 2>NUL:
RD /S /Q ".kotlin" 2>NUL:
RD /S /Q "app\build" 2>NUL:
RD /S /Q "build" 2>NUL:
RD /S /Q "syncthing-bep\build" 2>NUL:
RD /S /Q "syncthing-client\build" 2>NUL:
RD /S /Q "syncthing-core\build" 2>NUL:
RD /S /Q "syncthing-discovery\build" 2>NUL:
RD /S /Q "syncthing-relay-client\build" 2>NUL:
RD /S /Q "syncthing-repository-android\build" 2>NUL:
RD /S /Q "syncthing-temp-repository-encryption\build" 2>NUL:
::
goto :eof
