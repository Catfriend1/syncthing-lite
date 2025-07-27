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
    api(libs.bouncy.castle)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    
    implementation("org.bouncycastle:bctls-jdk15to18:1.81")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("org.bouncycastle:bcutil-jdk15to18:1.81")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")
}