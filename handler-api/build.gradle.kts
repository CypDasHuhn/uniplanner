plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
    }
}
