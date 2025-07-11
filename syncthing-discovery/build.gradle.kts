plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("net.syncthing.java.discovery.Main")
}

dependencies {
    implementation(project(":syncthing-core"))
    implementation(libs.commons.cli)
    implementation(libs.kotlin.stdlib)
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.named<JavaExec>("run") {
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split("\\s+".toRegex()))
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}