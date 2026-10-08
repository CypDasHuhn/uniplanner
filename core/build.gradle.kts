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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
