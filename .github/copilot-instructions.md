We use Java 21 (eclipse-temurin:21-jdk-jammy) to build the app via gradle.

Only use the "debug" flavor of the app when you make builds.
You cannot build "release" as the signing keys are not part of the repository.

Do not try to upgrade the kotlin version in "gradle/libs.versions.toml", it will throw a lot of warnings and errors.

No matter which language I use to write my prompt for you, please always do your coding work and code comments in english.
