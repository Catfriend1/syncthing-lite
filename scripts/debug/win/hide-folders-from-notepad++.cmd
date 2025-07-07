@echo off
cd ..\..\..
::
attrib +h .git
attrib +h .github
attrib +h .gradle
attrib +h .idea
attrib +h .kotlin
attrib +h app\build
attrib +h build
attrib +h syncthing-bep\build
attrib +h syncthing-client\build
attrib +h syncthing-core\build
attrib +h syncthing-discovery\build
attrib +h syncthing-relay-client\build
attrib +h syncthing-repository-android\build
attrib +h syncthing-temp-repository-encryption\build
timeout 3
::
goto :eof
