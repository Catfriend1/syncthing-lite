plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
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
        abortOnError = true
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
    implementation(libs.appcompat)
    implementation(libs.legacy.preference.v14)
    implementation(libs.legacy.support.v4)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.recyclerview)
    implementation(libs.core.ktx)
    implementation(libs.collection.ktx)
    implementation(libs.preference.ktx)

    implementation(libs.zxing.android.embedded)
    implementation(libs.appintro)

    implementation(project(":syncthing-client"))
    implementation(project(":syncthing-repository-android"))
}

tasks.register("validateAppVersionCode") {
    doFirst {
        val versionName = libs.versions.version.name.get()
        val versionCode = libs.versions.version.code.get().toInt()

        val parts = versionName.split(".")
        if (parts.size != 4) {
            throw GradleException("Invalid versionName format: '$versionName'. Expected format 'major.minor.patch.wrapper'.")
        }

        val calculatedCode = parts[0].toInt() * 1_000_000 +
                             parts[1].toInt() * 10_000 +
                             parts[2].toInt() * 100 +
                             parts[3].toInt()

        if (calculatedCode != versionCode) {
            throw GradleException("Version mismatch: Calculated versionCode ($calculatedCode) does not match declared versionCode ($versionCode). Please review 'gradle/libs.versions.toml'.")
        }
    }
}

project.afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
        dependsOn("validateAppVersionCode")
    }
}
