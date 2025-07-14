@echo off
setlocal enabledelayedexpansion
::
:: --no-build-cache
call gradlew --warning-mode all %* assembledebug
::
call scripts\debug\win\hide-folders-from-notepad++.cmd
::
endlocal
where update.cmd >NUL: 2>&1 || SET "PATH=%PATH%;%~dp0scripts\debug\win"
::
goto :eof
