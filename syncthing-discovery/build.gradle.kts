plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
    namespace = "net.syncthing.java.discovery"

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":syncthing-core"))
    implementation(libs.commons.cli)
    implementation(libs.kotlin.stdlib)
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.coroutines.core)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }

    plugins {
        create("kotlin") {
            artifact = "com.google.protobuf:protoc-gen-kotlin:3.21.12"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
