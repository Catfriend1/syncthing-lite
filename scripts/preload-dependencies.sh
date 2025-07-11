#!/bin/bash
# Pre-load gradle dependencies to avoid firewall issues during builds
# This script should be run in an environment with internet access

set -e

echo "Pre-loading gradle dependencies for syncthing-lite..."

# Set gradle properties for better network handling
export GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=60000 -Dorg.gradle.internal.http.socketTimeout=60000"

# Download dependencies without building
./gradlew --no-daemon dependencies --refresh-dependencies

echo "Dependencies downloaded. Build cache is now populated."
echo "You can now run builds in offline mode if needed:"
echo "./gradlew --offline build"

# List downloaded dependencies for verification
echo "Dependency tree:"
./gradlew --no-daemon dependencies --configuration implementation | head -20