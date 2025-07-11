plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    namespace = "net.syncthing.lite"

    defaultConfig {
        applicationId = "net.syncthing.lite"
        minSdk = 21
        targetSdk = 33
        versionCode = 22
        versionName = "0.3.12"
        multiDexEnabled = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes.add("META-INF/*")
        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
}

dependencies {
    val kotlin_version: String by rootProject.extra
    val kotlinx_coroutines_version: String by rootProject.extra
    
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinx_coroutines_version")
    implementation("com.google.android.material:material:1.0.0")
    implementation("androidx.legacy:legacy-preference-v14:1.0.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.0.0")

    implementation("com.google.zxing:android-integration:3.3.0")
    implementation("com.google.zxing:core:3.3.0")
    implementation("com.github.apl-devs:appintro:6.3.1")

    implementation(project(":syncthing-client"))
    implementation(project(":syncthing-repository-android"))
    implementation(project(":syncthing-temp-repository-encryption"))
}