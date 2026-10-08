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
            api(project(":handler-api"))
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        }
        commonTest.dependencies {
            implementation(project(":core"))
            implementation(kotlin("test"))
        }
    }
}
