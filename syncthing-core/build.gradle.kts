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

    api("commons-codec:commons-codec:1.18.0")
    api("commons-io:commons-io:2.19.0")
    api("com.google.code.gson:gson:2.13.1")
    api("org.bouncycastle:bcmail-jdk15to18:1.81")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
}