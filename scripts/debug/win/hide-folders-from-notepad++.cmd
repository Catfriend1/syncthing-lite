@echo off
setlocal enabledelayedexpansion
::
SET "SCRIPT_PATH=%~dp0"
SET "PROJECT_ROOT=%SCRIPT_PATH%..\..\.."
::
attrib +h "%PROJECT_ROOT%\.git"
attrib +h "%PROJECT_ROOT%\.github"
attrib +h "%PROJECT_ROOT%\.gradle"
attrib +h "%PROJECT_ROOT%\.idea"
attrib +h "%PROJECT_ROOT%\.kotlin" >NUL:
attrib +h "%PROJECT_ROOT%\app\build"
attrib +h "%PROJECT_ROOT%\build"
attrib +h "%PROJECT_ROOT%\syncthing-bep\build"
attrib +h "%PROJECT_ROOT%\syncthing-client\build"
attrib +h "%PROJECT_ROOT%\syncthing-core\build"
attrib +h "%PROJECT_ROOT%\syncthing-discovery\build"
attrib +h "%PROJECT_ROOT%\syncthing-relay-client\build"
attrib +h "%PROJECT_ROOT%\syncthing-repository-android\build"
attrib +h "%PROJECT_ROOT%\syncthing-temp-repository-encryption\build"
::
goto :eof
