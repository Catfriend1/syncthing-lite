plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.commons.codec)
    api(libs.commons.io)
    api(libs.gson)
    api(libs.bouncy.castle)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
}