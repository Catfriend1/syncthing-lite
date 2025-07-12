plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.build.tools.get()
    namespace = "net.syncthing.lite"

    defaultConfig {
        applicationId = "com.github.catfriend1.syncthinglite"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = libs.versions.version.code.get().toInt()
        versionName = libs.versions.version.name.get()
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("SYNCTHING_RELEASE_STORE_FILE")?.let(::file)
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = System.getenv("SYNCTHING_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    lint {
        abortOnError = false
        targetSdk = libs.versions.target.sdk.get().toInt()
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.runCatching { getByName("release") }
                .getOrNull()
                .takeIf { it?.storeFile != null }
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
        jniLibs {
            excludes.add("META-INF/*")
        }
        resources {
            excludes.add("META-INF/*")
        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.legacy.preference.v14)
    implementation(libs.legacy.support.v4)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.recyclerview)

    implementation(libs.zxing.integration)
    implementation(libs.zxing.core)
    implementation(libs.appintro)

    implementation(project(":syncthing-client"))
    implementation(project(":syncthing-repository-android"))
    implementation(project(":syncthing-temp-repository-encryption"))
}