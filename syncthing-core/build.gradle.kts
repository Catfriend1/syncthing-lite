plugins {
    id("java-library")
    kotlin("jvm")
}

dependencies {
    val kotlinVersion: String by rootProject.extra
    val kotlinxCoroutinesVersion: String by rootProject.extra

    api("commons-codec:commons-codec:1.18.0")
    api("commons-io:commons-io:2.19.0")
    api("com.google.code.gson:gson:2.13.1")
    api("org.bouncycastle:bcmail-jdk15to18:1.81")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
}