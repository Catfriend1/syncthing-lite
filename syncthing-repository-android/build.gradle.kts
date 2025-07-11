plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
}

android {
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    namespace = "net.syncthing.repository.android"

    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}

dependencies {
    val roomVersion = "2.6.1"
    val kotlin_version: String by rootProject.extra
    val protobuf_lite_version: String by rootProject.extra

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":syncthing-client"))
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("com.android.support:appcompat-v7:28.0.0")
    implementation("com.google.protobuf:protobuf-javalite:$protobuf_lite_version")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")

    kapt("androidx.room:room-compiler:$roomVersion")
}