FROM eclipse-temurin:21-jdk-jammy

# Can be found scrolling down on this page:
# https://developer.android.com/studio/index.html#command-tools
ARG ANDROID_SDK_FILENAME=commandlinetools-linux-11076708_latest.zip
WORKDIR /opt

RUN apt-get update && apt-get install --reinstall --no-install-recommends -y unzip git gnupg

# Install Android SDK manager
RUN mkdir -p /opt/android-sdk && cd /opt/android-sdk && \
    wget -q https://dl.google.com/android/repository/${ANDROID_SDK_FILENAME} && \
    unzip -q ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME}
ENV ANDROID_HOME=/opt/android-sdk

ARG SDKMANAGER="${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME}"

# Accept the SDK license, as we can't install packages otherwise
RUN yes | $SDKMANAGER --licenses > /dev/null

# NDK version
ENV BUILD_TOOLS_VERSION=34

# Install other android packages, including NDK
RUN $SDKMANAGER tools platform-tools "build-tools;${BUILD_TOOLS_VERSION}.0.0" "platforms;android-${BUILD_TOOLS_VERSION}" "extras;android;m2repository"

# Accept licenses of newly installed packages
RUN yes | $SDKMANAGER --licenses

# Enable prebuild mode
ENV SYNCTHING_LITE_PREBUILT=1

# Run prebuild script (will prebuild stuff into the image if env var is set)
ADD . /opt/syncthing-lite

RUN find /opt/syncthing-lite -name "*.sh" -exec sed -i 's/\r$//' {} \;
RUN find /opt/syncthing-lite -name "gradlew" -exec sed -i 's/\r$//' {} \;

RUN /opt/syncthing-lite/docker/prebuild.sh

WORKDIR /mnt
