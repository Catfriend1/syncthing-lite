plugins {
    application
    kotlin("jvm")
    id("com.google.protobuf")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("net.syncthing.java.discovery.Main")
}

dependencies {
    val kotlin_version: String by rootProject.extra
    val protobuf_lite_version: String by rootProject.extra
    val kotlinx_coroutines_version: String by rootProject.extra

    implementation(project(":syncthing-core"))
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("com.google.protobuf:protobuf-javalite:$protobuf_lite_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
}

tasks.named<JavaExec>("run") {
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split("\\s+".toRegex()))
    }
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