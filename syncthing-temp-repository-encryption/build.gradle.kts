plugins {
    id("java-library")
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    val kotlin_version: String by rootProject.extra

    implementation(project(":syncthing-core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
}