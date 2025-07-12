@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
::
attrib +h "%PROJECT_ROOT%\.git" >NUL:
attrib +h "%PROJECT_ROOT%\.github" >NUL:
attrib +h "%PROJECT_ROOT%\.gradle" >NUL:
attrib +h "%PROJECT_ROOT%\.idea" >NUL:
attrib +h "%PROJECT_ROOT%\.kotlin" >NUL:
attrib +h "%PROJECT_ROOT%\app\build" >NUL:
attrib +h "%PROJECT_ROOT%\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing-bep\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing-client\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing-core\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing-discovery\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing-relay-client\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing-repository-android\build" >NUL:
attrib +h "%PROJECT_ROOT%\syncthing-temp-repository-encryption\build" >NUL:
::
goto :eof
