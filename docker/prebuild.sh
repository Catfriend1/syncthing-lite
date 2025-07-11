#!/bin/bash

if [ -z "$SYNCTHING_LITE_PREBUILT" ]; then
    echo "Prebuild disabled"
    rm -rf syncthing-lite
    exit 0
fi

echo "Prepopulating gradle build cache"
cd syncthing-lite
./gradlew --no-daemon lint
cd ..
rm -rf syncthing-lite