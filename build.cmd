@echo off
setlocal enabledelayedexpansion
::
IF /I "%1" == "clean" SET PARAM_NO_BUILD_CACHE=--no-build-cache
::
call gradlew %PARAM_NO_BUILD_CACHE% --warning-mode all %* assembledebug
::
call scripts\debug\win\hide-folders-from-notepad++.cmd
::
endlocal
where update.cmd >NUL: 2>&1 || SET "PATH=%PATH%;%~dp0scripts\debug\win"
::
goto :eof
