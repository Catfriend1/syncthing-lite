// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    val kotlin_version by extra("1.9.22")
    val kotlinx_coroutines_version by extra("1.7.3")
    val build_tools_version by extra("8.5.0")
    val protobuf_lite_version by extra("4.31.1")
    
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:$build_tools_version")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.5")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "21"
        }
    }
}

val projectsToApplyOptInTo = listOf(
    project(":syncthing-bep"),
    project(":syncthing-client"),
    project(":syncthing-core"),
    project(":syncthing-discovery"),
)

projectsToApplyOptInTo.forEach { p ->
    p.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += listOf(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }
}