plugins {
    id("java-library")
    kotlin("jvm")
    id("com.google.protobuf")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    val kotlin_version: String by rootProject.extra
    val kotlinx_coroutines_version: String by rootProject.extra
    val protobuf_lite_version: String by rootProject.extra

    api(project(":syncthing-core"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":syncthing-relay-client"))
    implementation("net.jpountz.lz4:lz4:1.3.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
    api("com.google.protobuf:protobuf-javalite:$protobuf_lite_version")
}

// protobuf {
//     protoc {
//         artifact = "com.google.protobuf:protoc:${rootProject.extra["protobuf_lite_version"]}"
//     }
// 
//     generateProtoTasks {
//         all().configureEach {
//             builtins {
//                 java {
//                     option("lite")
//                 }
//             }
//         }
//     }
// }