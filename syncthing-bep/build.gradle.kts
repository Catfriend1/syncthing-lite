plugins {
    id("java-library")
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    val kotlinVersion: String by rootProject.extra
    val kotlinxCoroutinesVersion: String by rootProject.extra
    val protobufLiteVersion: String by rootProject.extra

    api(project(":syncthing-core"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":syncthing-relay-client"))
    implementation("net.jpountz.lz4:lz4:1.3.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    api("com.google.protobuf:protobuf-javalite:$protobufLiteVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${rootProject.extra["protobuf_lite_version"]}"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                java {
                    option("lite")
                }
            }
        }
    }
}