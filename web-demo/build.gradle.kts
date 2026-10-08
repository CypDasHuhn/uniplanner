plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    js {
        browser {
            commonWebpackConfig {
                outputFileName = "demo.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
            implementation(project(":handlers"))
        }
    }
}

tasks.matching { it.name == "jsBrowserTest" }.configureEach { enabled = false }
