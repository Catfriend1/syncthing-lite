plugins {
    application
    kotlin("jvm")
    id("com.google.protobuf")
}

application {
    mainClass.set("net.syncthing.java.discovery.Main")
}

dependencies {
    val kotlinVersion: String by rootProject.extra
    val protobufLiteVersion: String by rootProject.extra
    val kotlinxCoroutinesVersion: String by rootProject.extra

    implementation(project(":syncthing-core"))
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("com.google.protobuf:protobuf-javalite:$protobufLiteVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
}

tasks.named<JavaExec>("run") {
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split("\\s+".toRegex()))
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${rootProject.extra["protobuf_lite_version"]}"
    }

    generateProtoTasks {
        all().configureEach {
            builtins {
                java {
                    option("lite")
                }
            }
        }
    }
}