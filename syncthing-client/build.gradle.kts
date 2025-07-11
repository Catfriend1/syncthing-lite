plugins {
    id("java-library")
    kotlin("jvm")
}

dependencies {
    val kotlinVersion: String by rootProject.extra
    val kotlinxCoroutinesVersion: String by rootProject.extra

    api(project(":syncthing-bep"))
    api(project(":syncthing-discovery"))
    implementation(project(":syncthing-core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
}