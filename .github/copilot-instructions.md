## Copilot Coding Agent Configuration

We use Java 21 (eclipse-temurin:21-jdk-jammy) to build the app via gradle.

Only use the "debug" flavor of the app when you make gradle builds.
You cannot gradle build "release" as the signing keys are not part of the repository.
Please refrain from building the whole app with "assemleDebug" as this will take much time if it is not really required for your work. If possible, try to build smaller parts. If you need to see the lint report, use "gradle lintDebug"

Do not try to upgrade the kotlin version in "gradle/libs.versions.toml", it will throw a lot of warnings and errors.

No matter which language I use to write my prompt for you, please always do your coding work and code comments in english.
