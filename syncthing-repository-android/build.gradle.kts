plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.build.tools.get()
    namespace = "net.syncthing.repository.android"

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    lint {
        targetSdk = libs.versions.target.sdk.get().toInt()
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":syncthing-client"))
    implementation(libs.room.runtime)
    implementation(libs.appcompat.v7)
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlin.stdlib)

    kapt(libs.room.compiler)
}