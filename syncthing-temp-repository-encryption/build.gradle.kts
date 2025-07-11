plugins {
    id("java-library")
    kotlin("jvm")
}

dependencies {
    val kotlinVersion: String by rootProject.extra

    implementation(project(":syncthing-core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
}