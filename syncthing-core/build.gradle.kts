plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    api(libs.commons.codec)
    api(libs.commons.io)
    api(libs.gson)
    api(libs.bouncy.castle.bcmail)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncy.castle.bctls)
}