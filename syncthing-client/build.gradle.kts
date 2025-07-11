plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":syncthing-bep"))
    api(project(":syncthing-discovery"))
    implementation(project(":syncthing-core"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
}