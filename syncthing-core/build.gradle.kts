plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
    namespace = "net.syncthing.java.core"

    defaultConfig {
        minSdk = 29
        targetSdk = libs.versions.target.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(libs.commons.codec)
    api(libs.commons.io)
    api(libs.gson)
    api(libs.bouncy.castle)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    implementation("org.conscrypt:conscrypt-android:2.5.2")
}
