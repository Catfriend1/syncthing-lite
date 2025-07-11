FROM eclipse-temurin:21-jdk-jammy

# Can be found scrolling down on this page:
# https://developer.android.com/studio/index.html#command-tools
ARG ANDROID_SDK_FILENAME=commandlinetools-linux-11076708_latest.zip
WORKDIR /opt

# The '--reinstall' combined with 'build-essentials' is necessary for cgo
# compilation of go std libs to work. A terrible, arcane hack to solve arkane
# cgo issues - almost fitting.
RUN apt-get update && apt-get install --reinstall --no-install-recommends -y build-essential gcc unzip git gnupg python3

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
ENV NDK_VERSION=28.0.13004108
ENV TARGET_SDK_VERSION=36

# Install other android packages, including NDK
RUN $SDKMANAGER tools platform-tools "build-tools;${TARGET_SDK_VERSION}.0.0" "platforms;android-${TARGET_SDK_VERSION}" "extras;android;m2repository" "ndk;${NDK_VERSION}"

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