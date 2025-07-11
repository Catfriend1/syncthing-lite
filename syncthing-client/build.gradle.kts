plugins {
    id("java-library")
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    val kotlin_version: String by rootProject.extra
    val kotlinx_coroutines_version: String by rootProject.extra

    api(project(":syncthing-bep"))
    api(project(":syncthing-discovery"))
    implementation(project(":syncthing-core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
}